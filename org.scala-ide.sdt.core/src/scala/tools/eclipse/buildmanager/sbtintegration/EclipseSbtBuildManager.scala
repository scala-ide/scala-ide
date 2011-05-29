package scala.tools.eclipse
package buildmanager
package sbtintegration

import scala.tools.nsc.{Global, Settings}
import scala.tools.nsc.io.AbstractFile

import org.eclipse.core.resources.{ IFile, IMarker }
import org.eclipse.core.runtime.{ IProgressMonitor, SubMonitor }

// Temporary stub for future sbt integration
class EclipseSbtBuildManager(val project: ScalaProject, settings0: Settings)
  extends EclipseBuildManager {
	
  var depFile: IFile = null
  var compiler = null
  
  def loadFrom(file: AbstractFile, toFile: String => AbstractFile) : Boolean = true
  def saveTo(file: AbstractFile, fromFile: AbstractFile => String) {}
  def removeFiles(files: scala.collection.Set[AbstractFile]) {}
  def addSourceFiles(files: scala.collection.Set[AbstractFile]) {}
  def update(added: scala.collection.Set[AbstractFile], removed: scala.collection.Set[AbstractFile]) {}
  def build(addedOrUpdated: Set[IFile], removed: Set[IFile], monitor: SubMonitor): Unit = {}
  def clean(implicit monitor: IProgressMonitor) {}
  def invalidateAfterLoad: Boolean = false
}