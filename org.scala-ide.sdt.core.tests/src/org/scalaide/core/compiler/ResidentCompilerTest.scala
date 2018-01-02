package org.scalaide.core.compiler

import java.io.File

import org.junit.Assert
import org.junit.Test
import org.scalaide.core.SdtConstants
import org.scalaide.core.internal.builder.zinc.ResidentCompiler
import org.scalaide.core.internal.builder.zinc.ResidentCompiler.CompilationError
import org.scalaide.core.internal.builder.zinc.ResidentCompiler.CompilationFailed
import org.scalaide.core.internal.builder.zinc.ResidentCompiler.CompilationSuccess
import org.scalaide.core.testsetup.TestProjectSetup

object ResidentCompilerTest extends TestProjectSetup("resident-compiler") {
  val Dot = 1
}

class ResidentCompilerTest {
  import ResidentCompilerTest._

  @Test
  def shouldCompileScalaSource(): Unit = {
    val scalaSrc = project.allSourceFiles.collectFirst {
      case file if file.getFileExtension == SdtConstants.ScalaFileExtn.drop(Dot) =>
        new File(file.getLocationURI)
    }.get
    val output = project.outputFolderLocations.headOption.map(_.toFile()).get
    val tested = ResidentCompiler(project, output, None).get

    val actual = tested.compile(scalaSrc)

    actual match {
      case CompilationSuccess =>
      // ok
      case CompilationFailed(errors) =>
        Assert.fail(s"Expected no errors, found ${errors.toList.size}")
    }
  }

  @Test
  def shouldNotCompileJavaSourceBecauseThereIsNoWorkingJavaCompiler(): Unit = {
    val javaSrc = project.allSourceFiles.collectFirst {
      case file if file.getFileExtension == SdtConstants.JavaFileExtn.drop(Dot) =>
        new File(file.getLocationURI)
    }.get
    val output = project.outputFolderLocations.headOption.map(_.toFile()).get
    val tested = ResidentCompiler(project, output, None).get

    try {
      tested.compile(javaSrc) match {
        case CompilationFailed(CompilationError(actual, _) :: Nil) =>
          Assert.assertTrue(actual == "expects to be not called")
        case fail =>
          Assert.fail(s"Expected compilation error, got $fail")
      }
    } catch {
      case fail: Throwable =>
        Assert.fail(s"Expected compilation error, got ${fail.getMessage}")
    }

  }
}
