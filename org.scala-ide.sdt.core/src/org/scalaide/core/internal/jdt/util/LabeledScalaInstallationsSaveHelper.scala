package org.scalaide.core.internal.jdt.util

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.OutputStream
import java.io.Serializable
import org.eclipse.core.runtime.IPath
import org.eclipse.core.runtime.Path
import org.scalaide.core.internal.project.ScalaInstallation
import org.scalaide.core.internal.project.LabeledScalaInstallation
import org.scalaide.core.internal.project.ScalaInstallationLabel
import org.scalaide.core.internal.project.ScalaModule
import scala.tools.nsc.settings.NoScalaVersion
import org.scalaide.core.ScalaPlugin
import sbt.ScalaInstance
import java.net.URLClassLoader
import java.io.ObjectStreamClass
import scala.util.Try
import scala.util.Success
import scala.util.Failure
import org.eclipse.core.runtime.CoreException

class ContextualizedObjectInputStream(in: InputStream) extends ObjectInputStream(in) {

  override def resolveClass(desc: ObjectStreamClass) = {

    val res  = Try(Thread.currentThread().getContextClassLoader().loadClass(desc.getName()))
    res match {
      case Success(cl) => cl
      case Failure(thrown) => throw new IllegalAccessException("Something went horribly wrong deserializing")
    }
  }

}

object LabeledScalaInstallationsSaveHelper {

  def readInstallations(input: InputStream): List[LabeledScalaInstallation] = {
    val is = new ContextualizedObjectInputStream(new BufferedInputStream(input)) {
      enableResolveObject(true)

      override protected def resolveObject(o: Object): Object = o match {
        case i: PathReplace => i.getPath()
        case i: LabeledScalaInstallationReplace => i.getLabeledScalaInstallation()
        case i: ScalaModuleReplace => i.getScalaModule()
        case _ => super.resolveObject(o)
      }
    }

    val res = is.readObject().asInstanceOf[List[LabeledScalaInstallation]]
    res
  }

  def writeInstallations(scalaInstallations: Seq[ScalaInstallation], output: OutputStream): Unit = {
    val os = new ObjectOutputStream(new BufferedOutputStream(output)) {
      enableReplaceObject(true)

      override protected def replaceObject(o: Object): Object = o match {
        case i: ScalaModule => new ScalaModuleReplace(i)
        case i: LabeledScalaInstallation => new LabeledScalaInstallationReplace(i)
        case e: IPath => new PathReplace(e)
        case _ => super.replaceObject(o)
      }
    }

    os.writeObject(scalaInstallations)
    os.flush()
  }

  /** A ScalaModule replacement used for object serialization
   */
  class ScalaModuleReplace(val classJar: IPath, val srcJar: Option[IPath]) extends Serializable {
    private val serialVersionUID = 1001667379327078799L
    def this(sm: ScalaModule) = this(sm.classJar, sm.sourceJar)
    def getScalaModule() = ScalaModule(classJar, srcJar)
  }

  /** A ScalaInstallation replacement used for object serialization
   */
  class LabeledScalaInstallationReplace(val name: ScalaInstallationLabel, val compilerMod: ScalaModule, val libraryMod: ScalaModule, val extraJarsMods: Seq[ScalaModule]) extends Serializable {
    private val serialVersionUID = 3901667379327078799L
    def this(ins: LabeledScalaInstallation) = this(ins.label, ins.compiler, ins.library, ins.extraJars)
    def getLabeledScalaInstallation() = new LabeledScalaInstallation() {
      override def label = name
      override def compiler = compilerMod
      override def library = libraryMod
      override def extraJars = extraJarsMods
      override def version = ScalaInstallation.extractVersion(library.classJar).getOrElse(NoScalaVersion)
      override def scalaInstance: ScalaInstance = {
        val store = ScalaPlugin.plugin.classLoaderStore
        val scalaLoader = store.getOrUpdate(this)(new URLClassLoader(allJars.map(_.classJar.toFile.toURI.toURL).toArray, ClassLoader.getSystemClassLoader))

        new sbt.ScalaInstance(version.unparse, scalaLoader, library.classJar.toFile, compiler.classJar.toFile, extraJars.map(_.classJar.toFile).toList, None)
      }
    }
  }

  /** An IPath replacement used for object serialization
   */
  class PathReplace(val path: String) extends Serializable {
    private val serialVersionUID = -2361259525684491181L
    def this(ip: IPath) = this(ip.toPortableString())
    def getPath() = Path.fromPortableString(path)
  }

}