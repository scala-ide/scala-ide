package org.scalaide.core.project

import org.scalaide.core.testsetup.TestProjectSetup
import org.scalaide.core.internal.project.DirectoryScalaInstallation
import org.junit.Test
import org.junit.Assert._
import org.scalaide.core.internal.project.ScalaInstallation
import org.eclipse.core.runtime.IPath
import scala.tools.nsc.settings.SpecificScalaVersion
import scala.tools.nsc.settings.Development
import org.eclipse.core.runtime.Path
import org.scalaide.core.internal.project.ScalaModule
import org.eclipse.core.runtime.NullProgressMonitor
import org.scalaide.core.ScalaPlugin
import org.junit.AfterClass
import org.scalaide.util.internal.eclipse.EclipseUtils

object DirectoryScalaInstallationTest extends TestProjectSetup("classpath") {

  @AfterClass
  final def deleteProject(): Unit = {
    EclipseUtils.workspaceRunnableIn(ScalaPlugin.plugin.workspaceRoot.getWorkspace()) { _ =>
      project.underlying.delete(/* force */ true, new NullProgressMonitor)
    }
  }

}

class DirectoryScalaInstallationTest {

  import DirectoryScalaInstallationTest._

  @Test
  def filenamesWithoutVersion_210 {

    checkScalaInstallation(
        "lib/2.10.x/",
        SpecificScalaVersion(2, 10, 0, Development("filenamesWithoutVersion")),
        "")
  }

  @Test
  def filenamesWithVersion_210 {
    checkScalaInstallation(
        "lib/2.10.x-withVersion/",
        SpecificScalaVersion(2, 10, 0, Development("filenamesWithVersion")),
        "_2.10.0-filenamesWithVersion")
  }

  @Test
  def libraryCompilerCompatibleVersions_210 {
    checkScalaInstallation(
        "lib/2.10.x-libraryCompilerCompatibleVersions",
        SpecificScalaVersion(2, 10, 2, Development("libraryCompilerCompatibleVersions")),
        "")
  }

  @Test
  def noExtraJars_210 {
    checkScalaInstallation(
        "lib/2.10.x-noExtraJars/",
        SpecificScalaVersion(2, 10, 0, Development("filenamesWithoutVersion")),
        "",
        withReflect = false,
        withSwing = false,
        withActor = false)
  }

  @Test
  def filenamesWithoutVersion_211 {
    checkScalaInstallation(
        "lib/2.11.x/",
        SpecificScalaVersion(2, 11, 1, Development("filenamesWithoutVersion")),
        "")
  }

  @Test
  def filenamesWithVersion_211 {

    checkScalaInstallation(
        "lib/2.11.x-withVersion/",
        SpecificScalaVersion(2, 11, 1, Development("filenamesWithVersion")),
        "_2.11.1-filenamesWithVersion")
  }

  @Test
  def libraryCompilerCompatibleVersions_211 {
    checkScalaInstallation(
        "lib/2.11.x-libraryCompilerCompatibleVersions",
        SpecificScalaVersion(2, 11, 0, Development("libraryCompilerCompatibleVersions")),
        "")
  }

  @Test
  def noExtraJars_211 {
    checkScalaInstallation(
        "lib/2.11.x-noExtraJars/",
        SpecificScalaVersion(2, 11, 1, Development("filenamesWithoutVersion")),
        "",
        withReflect = false,
        withSwing = false,
        withActor = false)
  }

  @Test(expected=classOf[IllegalArgumentException])
  def libraryCompilerIncompatibleVersions {
    val basePath = project.underlying.getLocation().append("lib/libraryCompilerIncompatibleVersions")

    new DirectoryScalaInstallation(basePath)
  }

  def extraJarsIncompatibleVersions {
    checkScalaInstallation(
        "lib/extraJarsIncompatibleVersions/",
        SpecificScalaVersion(2, 11, 1, Development("filenamesWithoutVersion")),
        "",
        withReflect = false,
        withSwing = false,
        withActor = false)
  }



  @Test(expected=classOf[IllegalArgumentException])
  def missingLibrary {
    val basePath = project.underlying.getLocation().append("lib/missingLibrary")

    new DirectoryScalaInstallation(basePath)
  }

  @Test(expected=classOf[IllegalArgumentException])
  def missingCompiler {
    val basePath = project.underlying.getLocation().append("lib/missingCompiler")

    new DirectoryScalaInstallation(basePath)
  }

  /** best effort to find some jars, any jars and the associated sources */
  @Test
  def mixedCompatibleVersionsWithSources {
    val basePath = project.underlying.getLocation().append("lib/mixedCompatibleVersionsWithSources")

    val si = new DirectoryScalaInstallation(basePath)

    assertEquals("wrong version", SpecificScalaVersion(2, 10, 4, Development("mixedCompatibleVersionsWithName")), si.version)

    assertEquals("bad scala-library jar", basePath.append("scala-library_2.10.4-mixedCompatibleVersionsWithName.jar"), si.library.classJar)
    assertEquals("bad scala-library source jar", Some(basePath.append("scala-library-src_2.10.4-mixedCompatibleVersionsWithName.jar")), si.library.sourceJar)

    assertEquals("bad scala-compiler jar", basePath.append("scala-compiler_2.10.3-mixedCompatibleVersionsWithName.jar"), si.compiler.classJar)
    assertEquals("bad scala-compiler source jar", Some(basePath.append("scala-compiler-src_2.10.3-mixedCompatibleVersionsWithName.jar")), si.compiler.sourceJar)

    def checkExtraJar(id: String, versionSuffix: String, jars: List[ScalaModule]) = {
      val path= basePath.append(s"scala-${id}${versionSuffix}.jar")
      val (goodJars, remainder) = jars.partition(_.classJar == path)
      assertFalse(s"Missing scala-$id jar", goodJars.isEmpty)
      assertFalse(s"Too many scala-$id jars", goodJars.size > 1)
      assertEquals(s"Unexpected source jar for scala-$id", None, goodJars.head.sourceJar)
      remainder
    }

    val extraJars1 = checkExtraJar("reflect", "_2.10.2-mixedCompatibleVersionsWithName", si.extraJars)
    val extraJars2 = checkExtraJar("swing", "", extraJars1)
    val extraJars3 = checkExtraJar("actor", "", extraJars2)

    val extraJarsFinal = extraJars3

    assertTrue(s"Unexpected extra jars: $extraJarsFinal", extraJarsFinal.isEmpty)
  }

  def checkScalaInstallation(
      baseFolder: String,
      version: SpecificScalaVersion,
      versionSuffix: String,
      withReflect: Boolean = true,
      withSwing: Boolean = true,
      withActor: Boolean = true): Unit = {
    val basePath = project.underlying.getLocation().append(baseFolder)

    val si = new DirectoryScalaInstallation(basePath)

    assertEquals("wrong version", version, si.version)

    assertEquals("bad scala-library jar", basePath.append(s"scala-library${versionSuffix}.jar"), si.library.classJar)

    assertEquals("bad scala-compiler jar", basePath.append(s"scala-compiler${versionSuffix}.jar"), si.compiler.classJar)

    def checkExtraJar(check: Boolean, id: String, jars: List[ScalaModule]) = {
      if (check) {
        val path= basePath.append(s"scala-${id}${versionSuffix}.jar")
        val (goodJars, remainder) = jars.partition(_.classJar == path)
        assertFalse(s"Missing scala-$id jar", goodJars.isEmpty)
        assertFalse(s"Too many scala-$id jars", goodJars.size > 1)
        remainder
      } else {
        jars
      }
    }

    val extraJars1 = checkExtraJar(withReflect, "reflect", si.extraJars)
    val extraJars2 = checkExtraJar(withSwing, "swing", extraJars1)
    val extraJars3 = checkExtraJar(withActor, "actor", extraJars2)

    val extraJarsFinal = extraJars3

    assertTrue(s"Unexpected extra jars: $extraJarsFinal", extraJarsFinal.isEmpty)
  }

}
