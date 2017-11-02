package org.scalaide.core.internal.builder

import java.io.File
import java.util.zip.ZipFile

import org.eclipse.core.runtime.SubMonitor
import org.scalaide.core.IScalaInstallation
import org.scalaide.core.internal.ScalaPlugin

import sbt.internal.inc.AnalyzingCompiler
import sbt.internal.inc.Locate
import sbt.internal.inc.classpath.ClasspathUtilities
import xsbti.Logger
import xsbti.Reporter
import xsbti.compile.ClasspathOptions
import xsbti.compile.CompilerBridgeProvider
import xsbti.compile.DefinesClass
import xsbti.compile.IncToolOptions
import xsbti.compile.JavaCompiler
import xsbti.compile.ScalaInstance

package object zinc {
  private[zinc] object Locator {
    val NoClass = new DefinesClass {
      override def apply(className: String) = false
    }

    def apply(f: File): DefinesClass =
      if (f.isDirectory)
        new DirectoryLocator(f)
      else if (f.exists && ClasspathUtilities.isArchive(f))
        new JarLocator(f)
      else
        NoClass

    class DirectoryLocator(dir: File) extends DefinesClass {
      override def apply(className: String): Boolean = Locate.classFile(dir, className).isFile
    }

    class JarLocator(jar: File) extends DefinesClass {
      lazy val entries: Set[String] = {
        val zipFile = new ZipFile(jar, ZipFile.OPEN_READ)
        try {
          import scala.collection.JavaConverters._
          zipFile.entries.asScala.filterNot(_.isDirectory).map { entry =>
            toClassNameFromJarFileName(entry.getName)
          }.toSet
        } finally
          zipFile.close()
      }

      private def toClassNameFromJarFileName(jarFileName: String): String = {
        val noClassAtEnd = if (jarFileName.endsWith(".class"))
          jarFileName.substring(0, jarFileName.lastIndexOf(".class"))
        else
          jarFileName
        noClassAtEnd.replaceAll("/", ".")
      }

      override def apply(className: String): Boolean =
        entries.contains(className)
    }
  }

  private[zinc] object unimplementedJavaCompiler extends JavaCompiler {
    override def run(srcs: Array[File], opts: Array[String], incOpts: IncToolOptions, reporter: Reporter, logger: Logger) =
      throw new NotImplementedError("expects to be not called")
  }

  private[zinc] object compilers {
    def apply(installation: IScalaInstallation, javaMonitor: SubMonitor, javaCompilerConstructor: () => JavaCompiler = () => unimplementedJavaCompiler): Either[String, Compilers] = {
      import org.scalaide.core.internal.project.ScalaInstallation._
      val scalaInstance = scalaInstanceForInstallation(installation)
      val store = ScalaPlugin().compilerBridgeStore

      store.compilerBridgeFor(installation)(javaMonitor.newChild(10)).right.map {
        compilerBridge =>
          // prevent zinc from adding things to the (boot)classpath
          val cpOptions = ClasspathOptions.create(false, false, false, /* autoBoot = */ false, /* filterLibrary = */ false)
          Compilers(
            new AnalyzingCompiler(
              scalaInstance,
              new CompilerBridgeProvider {
                def fetchCompiledBridge(si: ScalaInstance, logger: Logger) = si.version match {
                  case scalaInstance.version => compilerBridge.toFile
                  case requested => throw new IllegalStateException(s"${scalaInstance.version} does not match requested one $requested")
                }
                def fetchScalaInstance(scalaVersion: String, logger: Logger) = scalaVersion match {
                  case scalaInstance.version => scalaInstance
                  case requested => throw new IllegalStateException(s"${scalaInstance.version} does not match requested one $requested")
                }
              },
              cpOptions,
              _ ⇒ (),
              None),
            javaCompilerConstructor())
      }
    }
  }
}