package scala.tools.eclipse.semicolon

import java.util.ListResourceBundle

object ShowInferredSemicolonsBundle {

  val PREFIX = "Editor.ShowInferredSemicolons."

}

class ShowInferredSemicolonsBundle extends ListResourceBundle {

  import ShowInferredSemicolonsBundle._

  def getContents = Array(
    Array(PREFIX + "label", "Show inferred semicolons"),
    Array(PREFIX + "description", "Shows inferred semicolons in current editor"),
    Array(PREFIX + "image", ""),
    Array(PREFIX + "tooltip", "Show inferred semicolons"))

}