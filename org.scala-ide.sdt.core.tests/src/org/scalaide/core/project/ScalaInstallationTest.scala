package org.scalaide.core.project

import org.junit.Test
import org.junit.Assert._
import org.scalaide.core.internal.project.ScalaInstallation
import org.scalaide.core.ScalaPlugin
import scala.tools.nsc.settings.ScalaVersion
import scala.tools.nsc.settings.SpecificScalaVersion
import scala.tools.nsc.settings.SpecificScalaVersion
import org.scalaide.util.internal.eclipse.OSGiUtils
import scala.tools.nsc.settings.SpecificScalaVersion
import org.scalaide.core.internal.project.BundledScalaInstallation
import org.eclipse.core.runtime.Platform
import org.scalaide.core.internal.project.MultiBundleScalaInstallation
import org.osgi.framework.Bundle
import org.eclipse.core.runtime.Path
import org.eclipse.core.runtime.IPath

class ScalaInstallationTest {

  /**
   * check the installations of Scala based on a bundle of jars
   */
  @Test
  def bundledInstallationsTest() {
    val bundledInstallations = ScalaInstallation.bundledInstallations

    ScalaPlugin.plugin.scalaVer match {
      case SpecificScalaVersion(2, 10, _, _) =>
        assertEquals("Unexpected Scala bundle", 0, bundledInstallations.length)
      case SpecificScalaVersion(2, 11, _, _) =>
        assertEquals("Wrong number of Scala bundles", 1, bundledInstallations.length)
        val scalaInstallation = bundledInstallations(0)

        scalaInstallation.version match {
          case SpecificScalaVersion(2, 10, _, _) =>
          case _ =>
            fail(s"Unexpected bundled Scala version: ${scalaInstallation.version}")
        }

        val bundlePath = OSGiUtils.pathInBundle(Platform.getBundle("org.scala-ide.scala210.jars"), "target").get.removeLastSegments(1)

        assertEquals("Wrong library jar", bundlePath.append(BundledScalaInstallation.ScalaLibraryPath), scalaInstallation.library.classJar)
        assertEquals("Wrong compiler jar", bundlePath.append(BundledScalaInstallation.ScalaCompilerPath), scalaInstallation.compiler.classJar)

        val expectedAllJars = Seq(
          bundlePath.append(BundledScalaInstallation.ScalaLibraryPath),
          bundlePath.append(BundledScalaInstallation.ScalaCompilerPath),
          bundlePath.append(BundledScalaInstallation.ScalaActorPath),
          bundlePath.append(BundledScalaInstallation.ScalaReflectPath),
          bundlePath.append(BundledScalaInstallation.ScalaSwingPath)).sortBy(_.toOSString())

        assertEquals("Wrong all jars", expectedAllJars, scalaInstallation.allJars.map(_.classJar).sortBy(_.toOSString()))

        val expectedAllSourceJars = Seq(
          bundlePath.append(BundledScalaInstallation.ScalaLibrarySourcesPath),
          bundlePath.append(BundledScalaInstallation.ScalaCompilerSourcesPath),
          bundlePath.append(BundledScalaInstallation.ScalaActorSourcesPath),
          bundlePath.append(BundledScalaInstallation.ScalaReflectSourcesPath),
          bundlePath.append(BundledScalaInstallation.ScalaSwingSourcesPath)).sortBy(_.toOSString())

        assertEquals("Wrong all source jars", expectedAllSourceJars, scalaInstallation.allJars.flatMap(_.sourceJar).sortBy(_.toOSString()))

      case SpecificScalaVersion(2, 12, _, _) =>
        assertEquals("Unexpected Scala bundle", 0, bundledInstallations.length)
      case v =>
        fail(s"Unsupported Scala version: $v")
    }
  }

  @Test
  def multiBundleInstallationsTest() {
    val multiBundleInstallations = ScalaInstallation.multiBundleInstallations

    ScalaPlugin.plugin.scalaVer match {
      case SpecificScalaVersion(2, 10, _, _) =>
        assertEquals("Unexpected Scala bundle", 1, multiBundleInstallations.length)
        checkMultiBundleInstallation(2, 10, multiBundleInstallations.head)
      case SpecificScalaVersion(2, 11, _, _) =>
        assertEquals("Wrong number of Scala bundles", 1, multiBundleInstallations.length)
        checkMultiBundleInstallation(2, 11, multiBundleInstallations.head)
      case SpecificScalaVersion(2, 12, _, _) =>
        assertEquals("Wrong number of Scala bundles", 1, multiBundleInstallations.length)
        checkMultiBundleInstallation(2, 12, multiBundleInstallations.head)
      case v =>
        fail(s"Unsupported Scala version: $v")
    }
  }

  val m2RepoLocationPattern = "(.*/)([^/]+)/([^/]+)/[^/]+\\.jar".r
  val pluginsLocationPattern = "(.*/)([^/]+)_([^/]+)\\.jar".r

  def checkMultiBundleInstallation(major: Int, minor: Int, scalaInstallation: ScalaInstallation) = {

    def isLibraryBundle(bundle: Bundle) = {
      val version = bundle.getVersion()
      bundle.getSymbolicName() == MultiBundleScalaInstallation.ScalaLibraryBundleId &&
        version.getMajor() == major &&
        version.getMinor() == minor
    }

    val libraryBundle = ScalaPlugin.plugin.getBundle().getBundleContext().getBundles().toList.find(isLibraryBundle).get

    val libraryPath = OSGiUtils.getBundlePath(libraryBundle).get

    // create a path builder depending on when the library bundle jar is coming from: a plugins folder or a m2 repo
    val bundlePathBuilder: (String) => IPath = libraryPath.toString match {
      case pluginsLocationPattern(pluginsFolder, MultiBundleScalaInstallation.ScalaLibraryBundleId, versionString) =>
        val pluginsPath = new Path(pluginsFolder)
        (bundleId: String) => pluginsPath.append(s"${bundleId}_${versionString}.jar")
      case m2RepoLocationPattern(repoFolder, MultiBundleScalaInstallation.ScalaLibraryBundleId, versionString) =>
        val repoPath = new Path(repoFolder)
        (bundleId: String) => repoPath.append(s"${bundleId}/${versionString}/${bundleId}-${versionString}.jar")
      case v =>
        fail(s"Didn't understood '$v'")
        // never reachs here
        null
    }

    assertEquals("Wrong library jar", bundlePathBuilder(MultiBundleScalaInstallation.ScalaLibraryBundleId), scalaInstallation.library.classJar)
    assertEquals("Wrong compiler jar", bundlePathBuilder(MultiBundleScalaInstallation.ScalaCompilerBundleId), scalaInstallation.compiler.classJar)

    val expectedAllJars = Seq(
      bundlePathBuilder(MultiBundleScalaInstallation.ScalaLibraryBundleId),
      bundlePathBuilder(MultiBundleScalaInstallation.ScalaCompilerBundleId),
      bundlePathBuilder(MultiBundleScalaInstallation.ScalaActorsBundleId),
      bundlePathBuilder(MultiBundleScalaInstallation.ScalaReflectBundleId)).sortBy(_.toOSString())

    assertEquals("Wrong all jars", expectedAllJars, scalaInstallation.allJars.map(_.classJar).sortBy(_.toOSString()))

    val expectedAllSourceJars = Seq(
      bundlePathBuilder(MultiBundleScalaInstallation.ScalaLibraryBundleId + ".source"),
      bundlePathBuilder(MultiBundleScalaInstallation.ScalaCompilerBundleId + ".source"),
      bundlePathBuilder(MultiBundleScalaInstallation.ScalaActorsBundleId + ".source"),
      bundlePathBuilder(MultiBundleScalaInstallation.ScalaReflectBundleId + ".source")).sortBy(_.toOSString())

    assertEquals("Wrong all sources jars", expectedAllSourceJars, scalaInstallation.allJars.flatMap(_.sourceJar).sortBy(_.toOSString()))

  }

}
