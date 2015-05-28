package org.scalaide.core.internal.launching

import scala.collection.JavaConverters.mapAsScalaMapConverter
import scala.collection.mutable

import org.eclipse.core.runtime.Path
import org.eclipse.debug.core.ILaunchConfiguration
import org.eclipse.jdt.launching.AbstractJavaLaunchConfigurationDelegate
import org.eclipse.jdt.launching.IRuntimeClasspathEntry
import org.eclipse.jdt.launching.JavaRuntime
import org.scalaide.core.SdtConstants

trait ClasspathGetterForLaunchDelegate extends AbstractJavaLaunchConfigurationDelegate {
  override def getClasspath(configuration: ILaunchConfiguration): Array[String] = {
    val baseClasspath = super.getClasspath(configuration).toSeq
    (baseClasspath ++ scalaClasspath(configuration, baseClasspath)).toArray
  }

  private def scalaClasspath(configuration: ILaunchConfiguration, baseClasspath: Seq[String]): Seq[String] = {
    val vmAttributesMap = getVMSpecificAttributesMap(configuration)
    val modifiedAttrMap: mutable.Map[String, Array[String]] = if (vmAttributesMap == null) mutable.Map()
      else vmAttributesMap.asInstanceOf[java.util.Map[String, Array[String]]].asScala
    toInclude(modifiedAttrMap, baseClasspath.toList, configuration)
  }

  private def toInclude(vmMap: mutable.Map[String, Array[String]], classpath: List[String],
    configuration: ILaunchConfiguration): List[String] =
    missingScalaLibraries((vmMap.values.flatten.toList) ::: classpath, configuration)

  private def missingScalaLibraries(included: List[String], configuration: ILaunchConfiguration): List[String] = {
    val entries = JavaRuntime.computeUnresolvedRuntimeClasspath(configuration).toList
    val libid = Path.fromPortableString(SdtConstants.ScalaLibContId)
    val found = entries.find(e => e.getClasspathEntry != null && e.getClasspathEntry.getPath == libid)
    found match {
      case Some(e) =>
        val scalaLibs = resolveClasspath(e, configuration)
        scalaLibs.diff(included)
      case None =>
        Nil
    }
  }

  private def resolveClasspath(a: IRuntimeClasspathEntry, configuration: ILaunchConfiguration): List[String] = {
    val bootEntry = JavaRuntime.resolveRuntimeClasspath(Array(a), configuration)
    bootEntry.toList.map(_.getLocation())
  }
}