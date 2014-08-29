package org.scalaide.core

import org.eclipse.core.resources.IProject
import org.scalaide.logging.HasLogger
import org.eclipse.ui.plugin.AbstractUIPlugin
import scala.tools.nsc.settings.ScalaVersion
import org.scalaide.util.internal.CompilerUtils

object IScalaPlugin {

  /** The runtime instance of ScalaPlugin
   */
  def apply(): IScalaPlugin = org.scalaide.core.internal.ScalaPlugin()

}

/** The public interface of the plugin runtime class of the SDT plugin.
 *
 *  All methods defined inside this trait are thread-safe.
 *  For the inherited methods, check their own documentation.
 */
trait IScalaPlugin extends AbstractUIPlugin with HasLogger {

  import SdtConstants._

  /** Indicates if the `sdtcore.notimeouts` flag is set.
   */
  lazy val noTimeoutMode: Boolean = System.getProperty(NoTimeoutsProperty) ne null

  /** Indicates if the `sdtcore.headless` flag is set.
   */
  lazy val headlessMode: Boolean = System.getProperty(HeadlessProperty) ne null

  /** The Scala version the SDT plugin is running on.
   */
  lazy val scalaVersion: ScalaVersion = ScalaVersion.current

  /** The `major.minor` string for the Scala version the SDT plugin is running on.
   */
  lazy val shortScalaVersion: String = CompilerUtils.shortString(scalaVersion)

  /** Always returns the ScalaProject for the given project, creating a
   *  new instance if needed.
   *
   *  The given project has to have the Scala nature,
   *  otherwise it might lead to errors later on.
   *
   *  If it is not known if the project has the Scala nature or not,
   *  use [[org.scalaide.core.IScalaPlugin!.asScalaProject]] instead.
   */
  def getScalaProject(project: IProject): IScalaProject

  /**
   * Return Some(ScalaProject) if the project has the Scala nature, None otherwise.
   */
  def asScalaProject(project: IProject): Option[IScalaProject]
}
