package org.scalaide

import org.eclipse.jdt.core.ICompilationUnit
import org.eclipse.jdt.core.IProblemRequestor
import org.eclipse.jdt.core.WorkingCopyOwner
import org.eclipse.jdt.core.compiler.IProblem
import org.junit.AfterClass
import org.scalaide.core.compiler.IScalaPresentationCompiler
import org.scalaide.core.internal.jdt.model.ScalaCompilationUnit
import org.scalaide.core.testsetup.SDTTestUtils
import org.scalaide.core.IScalaProject

/**
 * Common functionality to be used in tests that need to interop with the
 * compiler.
 *
 * This class creates a project with a name represented by [[givenName]] where
 * all created compilation units are moved to.
 *
 * This trait is meant to be mixed into an object.
 */
trait CompilerSupportTests {

  /** Can be overwritten in a subclass if desired. */
  val projectName: String = getClass().getSimpleName()

  private val project: IScalaProject = SDTTestUtils.createProjectInWorkspace(projectName)

  final def withCompiler(f: IScalaPresentationCompiler => Unit): Unit =
    project.presentationCompiler { compiler =>
      f(compiler)
    }

  /**
   * Generates a unique package name.
   */
  final def uniquePkgName(): String =
    s"testpackage${System.nanoTime()}"

  /**
   * Creates a Scala compilation unit whose underlying source file physically
   * exists in the test project of the test workspace. In order to prevent name
   * clashes, `source` is scanned for a package declaration, which is mapped to
   * the file system.
   *
   * If no package declaration is found, one is automatically generated.
   * However, no package declaration is placed into `source` in such a case.
   * This means that only file name clashes are avoided, but types can still
   * hide each other.
   *
   * The newly generated file is made available to the Eclipse platform and the
   * Scala compiler to allow the usage of the full non GUI feature set of the IDE.
   *
   * Note, that `source` is not checked for compilation errors. This should be
   * done immediately after the typechecking phase.
   */
  final def mkScalaCompilationUnit(source: String): ScalaCompilationUnit = {
    val PkgFinder = """(?s).*?package ([\w\.]+).*?""".r
    val pkgName = source match {
      case PkgFinder(name) ⇒ name
      case _ ⇒ uniquePkgName()
    }
    val p = SDTTestUtils.createSourcePackage(pkgName)(project)
    SDTTestUtils.createCompilationUnit(p, "testfile.scala", source).asInstanceOf[ScalaCompilationUnit]
  }

  /**
   * Creates a Java compilation unit whose underlying source file physically
   * exists in the test project of the test workspace. In order to prevent name
   * clashes, `source` is scanned for a package declaration, which is mapped to
   * the file system. Furthermore, `source` is scanned for a type
   * name, which is used as the filename.
   *
   * If either no package declaration or no type name is found, an exception is
   * thrown.
   *
   * The newly generated file is made available to the Eclipse platform and the
   * Scala compiler to allow the usage of the full non GUI feature set of the IDE.
   *
   * If any compilation errors are found in `source`, an exception is thrown
   * too.
   */
  final def mkJavaCompilationUnit(source: String): ICompilationUnit = {
    val PkgFinder = """(?s).*?package ([\w\.]+).*?""".r
    val ClassFinder = """(?s).*?public (?:(?:abstract )?class|interface) ([\w]+).*?""".r
    val pkgName = source match {
      case PkgFinder(name) ⇒ name
      case _ ⇒ throw new IllegalArgumentException("No valid package declaration found.")
    }
    val fileName = source match {
      case ClassFinder(name) ⇒ name
      case _ ⇒ throw new IllegalArgumentException("No valid class declaration found.")
    }

    val r = new IProblemRequestor {
      override def acceptProblem(p: IProblem) =
        throw new IllegalArgumentException(s"Got compilation error: $p")
      override def beginReporting() = ()
      override def endReporting() = ()
      override def isActive() = true
    }
    import org.mockito.Mockito._
    import org.mockito.Matchers._

    val o = mock(classOf[WorkingCopyOwner])
    when(o.getProblemRequestor(any())).thenReturn(r)

    val p = SDTTestUtils.createSourcePackage(pkgName)(project)
    val u = SDTTestUtils.createCompilationUnit(p, s"$fileName.java", source)
    u.getWorkingCopy(o, null)
  }

  @AfterClass
  final def deleteProject(): Unit =
    SDTTestUtils.deleteProjects(project)
}
