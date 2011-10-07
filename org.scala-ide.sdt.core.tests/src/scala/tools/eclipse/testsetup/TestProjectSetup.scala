package scala.tools.eclipse.testsetup

import org.eclipse.core.runtime.Path
import org.eclipse.jdt.core.IPackageFragmentRoot
import org.eclipse.jdt.core.JavaCore
import org.junit.Assert.assertNotNull
import scala.tools.eclipse.ScalaProject
import scala.tools.eclipse.javaelements.ScalaCompilationUnit
import org.eclipse.jdt.core.ICompilationUnit

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
class TestProjectSetup(projectName: String) {
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
  
  /** Return the Scala compilation unit corresponding to the given path, relative to the src folder.
   *  for example: "scala/collection/Map.scala". 
   */
  def scalaCompilationUnit(path: String): ScalaCompilationUnit =
    compilationUnit(path).asInstanceOf[ScalaCompilationUnit]

  /** Return a sequence of Scala compilation units corresponding to the given paths. */
  def scalaCompilationUnits(paths: String*): Seq[ScalaCompilationUnit] =
    paths.map(scalaCompilationUnit)
  
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
}
