package util

import util.EclipseUtils._

object FileUtils {
  def foo(): Unit = {
    workspaceRunnableIn("", null) { m => () }
  }
}