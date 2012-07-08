package scala.tools.eclipse.buildmanager

import scala.tools.eclipse.ScalaPlugin
import scala.tools.eclipse.resources.MarkerBuilder

/** Factory for creating markers used to report build problems (i.e., compilation errors). */
object BuildProblemMarker {
  def apply(): MarkerBuilder = MarkerBuilder(ScalaPlugin.plugin.problemMarkerId)
}