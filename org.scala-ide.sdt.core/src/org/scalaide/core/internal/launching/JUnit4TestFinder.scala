package org.scalaide.core.internal.launching

import collection.mutable
import collection.immutable
import org.eclipse.jdt.core.IJavaElement
import org.eclipse.core.runtime.IProgressMonitor
import java.util.{ Set => JSet }
import org.eclipse.jdt.core.IType
import org.scalaide.core.ScalaPlugin
import org.eclipse.jdt.core.IPackageFragment
import org.eclipse.core.resources.IProject
import org.eclipse.jdt.internal.core.JavaProject
import org.eclipse.search.core.text.TextSearchScope
import org.eclipse.core.resources.IResource
import org.eclipse.search.core.text.TextSearchEngine
import org.eclipse.search.core.text.TextSearchRequestor
import org.eclipse.core.resources.IFile
import org.eclipse.search.core.text.TextSearchMatchAccess
import org.eclipse.jdt.core.IJavaProject
import org.eclipse.jdt.core.JavaCore
import scala.collection.JavaConverters._
import org.eclipse.core.runtime.NullProgressMonitor
import org.eclipse.jdt.internal.junit.JUnitMessages
import org.eclipse.core.runtime.SubProgressMonitor
import org.eclipse.core.runtime.SubMonitor
import org.eclipse.jdt.internal.junit.launcher.ITestFinder
import org.scalaide.core.internal.project.ScalaProject
import org.scalaide.core.internal.jdt.model.ScalaSourceFile
import org.eclipse.jdt.core.IMember
import org.eclipse.jdt.core.IPackageFragmentRoot
import org.eclipse.jdt.core.IParent
import scala.tools.eclipse.contribution.weaving.jdt.launching.ISearchMethods
import org.scalaide.logging.HasLogger

/** A JUnit4 test finder that works for Java and Scala.
 *
 *  We hook this test finder using an internal extension point defined by `org.eclipse.jdt.junit.core`,
 *  `internal_testkinds`. This class is used both when right-clicking on a Java element and choosing
 *  "Run As - Scala JUnit test" and when hitting the "Search" button in the JUnit Run configuration dialog.
 *
 *  Alternatives were considered, but not pursued:
 *   - AspectJ to hook into the JUnit runner. Discarded as hackish, not future-proof and the risk of breaking
 *     the plain JDT plugin
 *   - implement a Scala JUnit configuration type, but that would duplicate more of the JUnit configuration type
 *     and involved rewriting dialogs and buttons
 *
 *  We needed to work around the lack of proper Scala search, and do a best-effort search. Known limitations:
 *
 *  - only @Test and @RunWith-style tests are recognized
 *  - classes that *don't* define any @Test members, but inherit them, are not found in Scala sources
 *     - this can be worked around by adding `@Test` anywhere in the file, for instance as a comment
 */
class JUnit4TestFinder extends ITestFinder with ISearchMethods with HasLogger {
  import JUnit4TestFinder._

  override def findTestsInContainer(element: IJavaElement, result: JSet[_], pm: IProgressMonitor): Unit =
    findTestsInContainer(element, result.asInstanceOf[JSet[IType]].asScala, pm)

  override def isTest(tpe: IType): Boolean = {
    ScalaLaunchShortcut.getJunitTestClasses(tpe).nonEmpty
  }

  /** Find all JUnit 4 tests under `element` and put them in `result`.
   *
   *  `element` can be any Java element: a project, a package, a source folder, a source file, a type.
   */
  def findTestsInContainer(element: IJavaElement, result: mutable.Set[IType], pm: IProgressMonitor): Unit = element match {
    case _: ScalaSourceFile =>
      result ++= ScalaLaunchShortcut.getJunitTestClasses(element)
    case tpe: IType =>
      result ++= ScalaLaunchShortcut.getJunitTestClasses(element).filter(_ == tpe)
    case member: IMember =>
      val parent = member.getAncestor(IJavaElement.TYPE)
      result ++= ScalaLaunchShortcut.getJunitTestClasses(element).filter(_ == parent)
    case _: IProject | _: IJavaProject | _: IPackageFragment | _: IPackageFragmentRoot =>
      findTestsInContainer1(element, result, pm)
    case _ =>
      logger.info("Unknown element type when looking for tests: %s:%s".format(element.getClass(), element.toString))
      ()
  }

  override def getTestMethods(javaProject: IJavaProject, tpe: IType): java.util.Set[String] = {
    import collection.JavaConverters._

    val emptySet = immutable.Set[String]()

    val res = ScalaPlugin.plugin.asScalaProject(javaProject.getProject()) map { scalaProject =>
      scalaProject.presentationCompiler { comp =>
        import comp._
        object helper extends JUnit4TestClassesCollector { val global: comp.type = comp }

        def hasTestAnnotation(sym: Symbol) = {
          sym.initialize
          helper.TestAnnotationOpt.exists(sym.hasAnnotation)
        }
        val fqn = newTypeName(tpe.getFullyQualifiedName())

        askOption { () =>
          // classes in the empty package are not found in the root mirror
          val sym = if (fqn.lastPos('.') > -1) rootMirror.getClassByName(fqn) else rootMirror.EmptyPackageClass.info.member(fqn)
          sym.annotations
          sym.info.members.filter(hasTestAnnotation).map(_.unexpandedName.toString).toSet
        } getOrElse (emptySet)
      } getOrElse (emptySet)
    } getOrElse (emptySet)

    res.asJava
  }

  /** This method finds tests in any container, but may be imprecise if `element` is smaller than a source file.
   *
   *  This method filters out all source file in a first step, and then delegates to `JUnit4TestFinder.findTestClasses(scu)`
   *  for each potential match. The filtering is based on a textual search, and misses test files that don't mention
   *  at all any of the test annotations (for instance, by inheriting all their tests).
   */
  private def findTestsInContainer1(element: IJavaElement, result: mutable.Set[IType], _pm: IProgressMonitor): Unit = {
    val pm = if (_pm == null) new NullProgressMonitor else _pm
    val scalaProject = ScalaPlugin.plugin.asScalaProject(element.getJavaProject().getProject()).get // we know it's a Scala project or we wouldn't be here

    val progress = SubMonitor.convert(pm, JUnitMessages.JUnit4TestFinder_searching_description, 4)
    try {
      val (scalaCandidates, javaCandidates) = filteredTestResources(scalaProject, element, progress.newChild(2)).partition(_.getFileExtension == "scala")

      result ++= scalaMatches(scalaCandidates, progress.newChild(1))
      result ++= ((new JavaJUnit4TestFinder).javaMatches(scalaProject.javaProject, javaCandidates, progress.newChild(1)))
    } finally
      pm.done()
  }

  private[core] def filteredTestResources(prj: ScalaProject, element: IJavaElement, progress: IProgressMonitor): Seq[IResource] = {
    val candidates = element match {
      case project: IJavaProject => prj.allSourceFiles.toSeq
      case _                     => Seq(element.getResource)
    }
    progress.worked(1)

    likelyTestResources(candidates, progress)
  }

  private def likelyTestResources(roots: Seq[IResource], _pm: IProgressMonitor): Seq[IResource] = {
    val pm = SubMonitor.convert(_pm, "Textual search for likely sources that contain tests", roots.size)
    val scope = TextSearchScope.newSearchScope(roots.toArray, FILE_NAME_PATTERN.pattern, /* visitDerivedResoures = */ false)

    if (pm.isCanceled()) Seq()
    else {
      val engine = TextSearchEngine.createDefault()
      val req = new PotentialTestFilesCollector(pm)
      engine.search(scope, req, TEST_PATTERN.pattern, pm)
      pm.done()
      req.files
    }
  }

  private def scalaMatches(candidates: Seq[IResource], _pm: IProgressMonitor): Seq[IType] = {
    import scala.util.control.Exception._

    val pm = SubMonitor.convert(_pm, "Locating Scala Test matches", candidates.size)
    for {
      resource <- candidates
      _ = pm.worked(1)  // interpose a side-effect
      element <- Option(JavaCore.create(resource)).toSeq
      if !pm.isCanceled()
      tpe <- allCatch.withApply(_ => Seq()) { ScalaLaunchShortcut.getJunitTestClasses(element) }
    } yield tpe
  }

  /** Collect all files where matches occur. Optimizations:
   *
   *  - accept only one match per file, tell the engine to skip the rest
   *  - all Java files are collected, regardless whether they match or not the search pattern
   *    (this is because they are potential candidates, and JDT search may find inherited tests)
   */
  private class PotentialTestFilesCollector(pm: IProgressMonitor) extends TextSearchRequestor {
    val files = mutable.ListBuffer[IFile]()

    override def acceptFile(file: IFile): Boolean = {
      pm.worked(1)
      if (file.getFileExtension() == "java") {
        files += file // all Java files, with or without matches, are likely candidates (because JDT search works :D)
        false
      } else
        true // for Scala files, we want to continue and get real match reports
    }

    override def acceptPatternMatch(matchAccess: TextSearchMatchAccess): Boolean = {
      files += matchAccess.getFile()
      false // don't report more matches in this file
    }
  }
}

/** Given a Scala compilation unit, finds all top level class definition that can be run as JUnit4 test classes.
 *
 *  If a source contains errors, the `JUnit4TestFinder` will likely still be able to find executable JUnit test
 *  classes in the passed source. If we wanted to be smart, we could check if the passed source (or even the
 *  enclosing project) has any compile-time error, and return an empty set of runnable test classes.
 *  However, this turns out to be a bad idea, because the user may not understand that the reason why it can't run
 *  the test class is because he has to fix all compilation errors. Therefore, it is better to always return the
 *  set of executable JUnit4 test classes and let the user figure out the cause why the test class cannot be run.
 */
object JUnit4TestFinder {
  private val MARKER_STRINGS = Set("@Test", "@RunWith")

  /** Textual filter, used to find candidate resources for JUnit tests. */
  private val TEST_PATTERN = MARKER_STRINGS.mkString("|").r
  private val FILE_NAME_PATTERN = """(.*\.java$)|(.*\.scala$)""".r

  def findTestClasses(scu: ScalaSourceFile): List[IType] = scu.withSourceFile { (source, comp) =>
    import comp.ClassDef
    import comp.Response
    import comp.Tree
    import org.scalaide.util.internal.Utils._
    val response = new Response[Tree]
    comp.askParsedEntered(source, keepLoaded = false, response)

    object JUnit4TestClasses extends JUnit4TestClassesCollector {
      val global: comp.type = comp
    }

    val trees = response.get.left.getOrElse(comp.EmptyTree)
    for {
      cdef <- JUnit4TestClasses.collect(trees)
      jdtElement <- comp.getJavaElement(cdef.symbol, scu.getJavaProject)
      jdtType <- jdtElement.asInstanceOfOpt[IType]
    } yield jdtType
  } getOrElse Nil
}
