package org.scalaide

import org.eclipse.jdt.core.ICompilationUnit
import org.junit.AfterClass
import org.scalaide.core.compiler.ScalaPresentationCompiler
import org.scalaide.core.internal.jdt.model.ScalaCompilationUnit
import org.scalaide.core.internal.project.ScalaProject
import org.scalaide.core.testsetup.SDTTestUtils

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

  private val project: ScalaProject = SDTTestUtils.createProjectInWorkspace(projectName)

  final def withCompiler(f: ScalaPresentationCompiler => Unit): Unit =
    project.presentationCompiler { compiler =>
      f(compiler)
    }

  /**
   * Creates a compilation unit whose underlying source file physically exists
   * in the test project of the test workspace. The file is placed in a unique
   * package name to prevent name clashes between generated files.
   *
   * The newly generated file is made available to the Eclipse platform and the
   * Scala compiler to allow the usage of the full non GUI feature set of the IDE.
   */
  final def mkCompilationUnit(source: String): ICompilationUnit = {
    val p = SDTTestUtils.createSourcePackage("testpackage" + System.nanoTime())(project)
    SDTTestUtils.createCompilationUnit(p, "testfile.scala", source)
  }

  final def mkScalaCompilationUnit(source: String): ScalaCompilationUnit =
    mkCompilationUnit(source).asInstanceOf[ScalaCompilationUnit]

  @AfterClass
  final def deleteProject(): Unit =
    SDTTestUtils.deleteProjects(project)
}
