package org.scalaide.core.project

import scala.tools.nsc.settings.SpecificScalaVersion

import org.eclipse.core.runtime.IPath
import org.eclipse.core.runtime.Path
import org.eclipse.core.runtime.Platform
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Ignore
import org.junit.Test
import org.osgi.framework.Bundle
import org.scalaide.core.IScalaInstallation
import org.scalaide.core.IScalaPlugin
import org.scalaide.core.internal.project.BundledScalaInstallation
import org.scalaide.core.internal.project.MultiBundleScalaInstallation
import org.scalaide.core.internal.project.ScalaInstallation
import org.scalaide.util.eclipse.OSGiUtils

class ScalaInstallationTest {

  /**
   * check the installations of Scala based on a bundle of jars
   */
  @Ignore("Needs investigation")
  @Test
  def bundledInstallationsTest(): Unit = {
    val bundledInstallations = ScalaInstallation.bundledInstallations

    IScalaPlugin().scalaVersion match {
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
          bundlePath.append(BundledScalaInstallation.ScalaReflectPath),
          bundlePath.append(BundledScalaInstallation.ScalaSwingPath)).sortBy(_.toOSString())

        assertEquals("Wrong all jars", expectedAllJars, scalaInstallation.allJars.map(_.classJar).sortBy(_.toOSString()))

        val expectedAllSourceJars = Seq(
          bundlePath.append(BundledScalaInstallation.ScalaLibrarySourcesPath),
          bundlePath.append(BundledScalaInstallation.ScalaCompilerSourcesPath),
          bundlePath.append(BundledScalaInstallation.ScalaReflectSourcesPath),
          bundlePath.append(BundledScalaInstallation.ScalaSwingSourcesPath)).sortBy(_.toOSString())

        assertEquals("Wrong all source jars", expectedAllSourceJars, scalaInstallation.allJars.flatMap(_.sourceJar).sortBy(_.toOSString()))

      case SpecificScalaVersion(2, 12, _, _) =>
        assertEquals("Unexpected Scala bundle", 0, bundledInstallations.length)
      case v =>
        fail(s"Unsupported Scala version: $v")
    }
  }

  @Ignore("Needs investigation")
  @Test
  def multiBundleInstallationsTest(): Unit = {
    val multiBundleInstallations = ScalaInstallation.multiBundleInstallations

    IScalaPlugin().scalaVersion match {
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

  def checkMultiBundleInstallation(major: Int, minor: Int, scalaInstallation: IScalaInstallation) = {
    val scalaBundles = IScalaPlugin().getBundle().getBundleContext().getBundles().toList

    def bundleOf(bundleId: String)(bundle: Bundle) = {
      val version = bundle.getVersion()
      bundle.getSymbolicName() == bundleId &&
        version.getMajor() == major &&
        version.getMinor() == minor
    }
    def isLibraryBundle = bundleOf(MultiBundleScalaInstallation.ScalaLibraryBundleId) _
    val libraryBundle = scalaBundles.find(isLibraryBundle).get

    val libraryPath = OSGiUtils.getBundlePath(libraryBundle).get

    def mkBundleNameString(isSource: Boolean, bundleId: String) =
      if (isSource) bundleId + ".source" else bundleId

    // create a path builder depending on when the library bundle jar is coming from: a plugins folder or a m2 repo
    val bundlePathBuilder: Boolean => String => IPath = libraryPath.toString match {
      case pluginsLocationPattern(pluginsFolder, MultiBundleScalaInstallation.ScalaLibraryBundleId, _) => {
        val pluginsPath = new Path(pluginsFolder)
        (isSource: Boolean) => (bundleId: String) => {
          val versionString = scalaBundles.find(bundleOf(bundleId)).get.getVersion.toString
          pluginsPath.append(s"${mkBundleNameString(isSource, bundleId)}_${versionString}.jar")
        }
      }
      case m2RepoLocationPattern(repoFolder, MultiBundleScalaInstallation.ScalaLibraryBundleId, _) => {
        val repoPath = new Path(repoFolder)
        (isSource: Boolean) => (bundleId: String) => {
          val versionString = scalaBundles.find(bundleOf(bundleId)).get.getVersion.toString
          repoPath.append(s"${mkBundleNameString(isSource, bundleId)}/${versionString}/${mkBundleNameString(isSource, bundleId)}-${versionString}.jar")
        }
      }
      case v => {
        fail(s"Didn't understood '$v'")
        // never reaches here
        null
      }
    }
    val binaryPathBuilder = bundlePathBuilder(false)
    val sourcePathBuilder = bundlePathBuilder(true)

    assertEquals("Wrong library jar", binaryPathBuilder(MultiBundleScalaInstallation.ScalaLibraryBundleId), scalaInstallation.library.classJar)
    assertEquals("Wrong compiler jar", binaryPathBuilder(MultiBundleScalaInstallation.ScalaCompilerBundleId), scalaInstallation.compiler.classJar)

    val expectedAllJars = Seq(
      binaryPathBuilder(MultiBundleScalaInstallation.ScalaLibraryBundleId),
      binaryPathBuilder(MultiBundleScalaInstallation.ScalaCompilerBundleId),
      binaryPathBuilder(MultiBundleScalaInstallation.ScalaReflectBundleId)).sortBy(_.toOSString())

    assertEquals("Wrong all jars", expectedAllJars, scalaInstallation.allJars.map(_.classJar).sortBy(_.toOSString()))

    val expectedAllSourceJars = Seq(
      sourcePathBuilder(MultiBundleScalaInstallation.ScalaLibraryBundleId),
      sourcePathBuilder(MultiBundleScalaInstallation.ScalaCompilerBundleId),
      sourcePathBuilder(MultiBundleScalaInstallation.ScalaReflectBundleId)).sortBy(_.toOSString())

    assertEquals("Wrong all sources jars", expectedAllSourceJars, scalaInstallation.allJars.flatMap(_.sourceJar).sortBy(_.toOSString()))
  }

}
