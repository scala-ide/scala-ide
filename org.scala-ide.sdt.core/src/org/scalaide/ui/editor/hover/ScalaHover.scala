package org.scalaide.ui.editor.hover

import org.scalaide.ui.internal.editor.hover.ScalaHoverImpl
import org.scalaide.ui.editor.InteractiveCompilationUnitEditor

/** A Scala hover implementation. This class exists only to allow clients to
 *  use it in extension points, like `plugin.xml`.
 *
 *  @note Prefer factory methods in `IScalaHover` over directly manipulating this class.
 */
final class ScalaHover(editor: InteractiveCompilationUnitEditor) extends ScalaHoverImpl(editor) {
  def this() = {
    this(null)
  }
}
