package scala.tools.eclipse.testsetup

import org.eclipse.core.runtime.Path
import org.eclipse.jdt.core.IPackageFragmentRoot
import org.eclipse.jdt.core.JavaCore
import org.junit.Assert.assertNotNull
import scala.tools.eclipse.ScalaProject
import org.eclipse.jdt.core.ICompilationUnit
import scala.tools.eclipse.InteractiveCompilationUnit
import scala.tools.eclipse.javaelements.ScalaSourceFile
import scala.tools.eclipse.javaelements.ScalaCompilationUnit
import org.eclipse.jdt.core.IProblemRequestor
import org.eclipse.jdt.core.WorkingCopyOwner
import org.eclipse.core.runtime.NullProgressMonitor
import org.mockito.Mockito.{mock, when}
import org.eclipse.core.resources.IFile

/** Base class for setting up tests that depend on a project found in the test-workspace.
 * 
 *  Subclass this class with an `object'. The initialization will copy the given project
 *  from test-workspace to the target workspace, and retrieve the 'src/' package root in
 *  `srcPackageRoot'.
 *  
 *  Reference the object form your test, so that the constructor is called and the project
 *  setup.
 *  
 *  Example: `object HyperlinkDetectorTests extends TestProjectSetup("hyperlinks")'
 * 
 */
class TestProjectSetup(projectName: String, srcRoot: String = "/%s/src/", val bundleName: String = "org.scala-ide.sdt.core.tests") extends ProjectBuilder {
  /** The ScalaProject corresponding to projectName, after copying to the test workspace. */
  lazy val project: ScalaProject = SDTTestUtils.setupProject(projectName, bundleName)
  
  /** The package root corresponding to /src inside the project. */
  lazy val srcPackageRoot: IPackageFragmentRoot = {
    val javaProject = JavaCore.create(project.underlying)

    javaProject.open(null)
    javaProject.findPackageFragmentRoot(new Path(srcRoot.format(projectName)))
  }

  assertNotNull(srcPackageRoot)

  srcPackageRoot.open(null)
  
  def file(path: String): IFile = {
    project.underlying.getFile(path)
  }
  
  /** Return the compilation unit corresponding to the given path, relative to the src folder.
   *  for example: "scala/collection/Map.scala"
   */
  def compilationUnit(path: String): ICompilationUnit = {
    val segments = path.split("/")
    srcPackageRoot.getPackageFragment(segments.init.mkString(".")).getCompilationUnit(segments.last)
  }
  
  /** Return a sequence of compilation units corresponding to the given paths. */
  def compilationUnits(paths: String*): Seq[ICompilationUnit] =
    paths.map(compilationUnit)
  
  /** Return a sequence of Scala compilation units corresponding to the given paths. */
  def scalaCompilationUnits(paths: String*): Seq[ScalaSourceFile] =
    paths.map(scalaCompilationUnit)

  /** Return the Scala compilation unit corresponding to the given path, relative to the src folder.
   *  for example: "scala/collection/Map.scala". 
   */
  def scalaCompilationUnit(path: String): ScalaSourceFile =
    compilationUnit(path).asInstanceOf[ScalaSourceFile]

  def createSourceFile(packageName: String, unitName: String)(contents: String): ScalaSourceFile = {
    val pack = SDTTestUtils.createSourcePackage(packageName)(project)
    new scala.tools.eclipse.EclipseUserSimulator().createCompilationUnit(pack, unitName, contents).asInstanceOf[ScalaSourceFile]
  }
  
  def reload(unit: InteractiveCompilationUnit) {
    // first, 'open' the file by telling the compiler to load it
    project.withSourceFile(unit) { (src, compiler) =>
      val dummy = new compiler.Response[Unit]
      compiler.askReload(List(src), dummy)
      dummy.get
    }()
  }

  def parseAndEnter(unit: InteractiveCompilationUnit) {
    project.withSourceFile(unit) { (src, compiler) =>
      val dummy = new compiler.Response[compiler.Tree]
      compiler.askParsedEntered(src, false, dummy)
      dummy.get
    }()
  }
  
  def findMarker(marker: String) = SDTTestUtils.findMarker(marker)
  
  /** Emulate the opening of a scala source file (i.e., it tries to 
   * reproduce the steps performed by JDT when opening a file in an editor). 
   * 
   * @param srcPath the path to the scala source file 
   * */
  def open(srcPath: String): ScalaSourceFile = {
    val unit = scalaCompilationUnit(srcPath)
    openWorkingCopyFor(unit)
    reload(unit)
    unit
  }
  
  /** Open a working copy of the passed `unit` */
  private def openWorkingCopyFor(unit: ScalaSourceFile) {
    val requestor = mock(classOf[IProblemRequestor])
    // the requestor must be active, or unit.getWorkingCopy won't trigger the Scala
    // structure builder
    when(requestor.isActive()).thenReturn(true)

    val owner = new WorkingCopyOwner() {
      override def getProblemRequestor(unit: org.eclipse.jdt.core.ICompilationUnit): IProblemRequestor = requestor
    }

    // this will trigger the Scala structure builder
    unit.getWorkingCopy(owner, new NullProgressMonitor)
  }
  
  /** Wait until the passed `unit` is entirely typechecked. */
  def waitUntilTypechecked(unit: ScalaCompilationUnit) {
    // give a chance to the background compiler to report the error
    project.withSourceFile(unit) { (source, compiler) =>
      import scala.tools.nsc.interactive.Response
      val res = new Response[compiler.Tree]
      compiler.askLoadedTyped(source, res)
      res.get // wait until unit is typechecked
    }()
  }
  
  /** Open the passed `source` and wait until it has been fully typechecked.*/
  def openAndWaitUntilTypechecked(source: ScalaSourceFile) {
    val sourcePath = source.getPath()
    val projectSrcPath = project.underlying.getFullPath() append "src"
    val path = sourcePath.makeRelativeTo(projectSrcPath)
    open(path.toOSString())
    waitUntilTypechecked(source)
  }
}
