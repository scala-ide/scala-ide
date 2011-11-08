package scala.tools.eclipse.testsetup

import org.eclipse.core.runtime.Path
import org.eclipse.jdt.core.IPackageFragmentRoot
import org.eclipse.jdt.core.JavaCore
import org.junit.Assert.assertNotNull
import scala.tools.eclipse.ScalaProject
import org.eclipse.jdt.core.ICompilationUnit
import scala.tools.eclipse.javaelements.ScalaSourceFile
import scala.tools.eclipse.javaelements.ScalaCompilationUnit
import org.eclipse.jdt.core.ICompilationUnit
import org.eclipse.jdt.core.IProblemRequestor
import org.eclipse.jdt.core.WorkingCopyOwner
import scala.tools.eclipse.javaelements.ScalaCompilationUnit
import scala.tools.eclipse.javaelements.ScalaSourceFile
import org.eclipse.core.runtime.NullProgressMonitor


import org.mockito.Mockito.{mock, when}

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
class TestProjectSetup(projectName: String)  {
  type ScalaUnit = ScalaCompilationUnit with ICompilationUnit
  
  /** The ScalaProject corresponding to projectName, after copying to the test workspace. */
  lazy val project: ScalaProject = SDTTestUtils.setupProject(projectName)
  
  /** The package root corresponding to /src inside the project. */
  lazy val srcPackageRoot: IPackageFragmentRoot = {
    val javaProject = JavaCore.create(project.underlying)

    javaProject.open(null)
    javaProject.findPackageFragmentRoot(new Path("/%s/src".format(projectName)))
  }

  assertNotNull(srcPackageRoot)

  srcPackageRoot.open(null)
  
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
  def scalaCompilationUnits(paths: String*): Seq[ScalaUnit] =
    paths.map(scalaCompilationUnit)

  /** Return the Scala compilation unit corresponding to the given path, relative to the src folder.
   *  for example: "scala/collection/Map.scala". 
   */
  def scalaCompilationUnit(path: String): ScalaUnit =
    compilationUnit(path).asInstanceOf[ScalaSourceFile]

  
  def reload(unit: ScalaCompilationUnit) {
    // first, 'open' the file by telling the compiler to load it
    project.withSourceFile(unit) { (src, compiler) =>
      val dummy = new compiler.Response[Unit]
      compiler.askReload(List(src), dummy)
      dummy.get
    }()
  }
  
  def findMarker(marker: String) = new {
    import org.eclipse.jdt.internal.compiler.env.ICompilationUnit
    def in(unit: ICompilationUnit): Seq[Int] = {
    	val contents = unit.getContents()
    	SDTTestUtils.positionsOf(contents, marker)
    }
  }
  
  /** Emulate the opening of a scala source file (i.e., it tries to 
   * reproduce the steps performed by JDT when opening a file in an editor). 
   * 
   * @param srcPath the path to the scala source file 
   * */
  def open(srcPath: String) = {
    val unit = scalaCompilationUnit(srcPath)
    openWorkingCopyFor(unit)
    reload(unit)
    unit
  }
  
  /** Open a working copy of the passed `unit` */
  private def openWorkingCopyFor(unit: ScalaUnit) {
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
}
