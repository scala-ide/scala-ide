package org.scalaide.editor

import org.eclipse.core.runtime.Platform

import org.eclipse.ui.editors.text.EditorsUI

/** Members in this class require some UI class to be loaded.
  * @note You won't be able to test your code if you depend on members in this class
  * because our CI server can only execute tests in headless mode.
  */
object EditorUI {
  /** The default line separator */
  def defaultLineSeparator: String = {
    val ls = Option(EditorsUI.getPreferenceStore()).map(_.getString(Platform.PREF_LINE_SEPARATOR))
    ls match {
      case Some("") | None => System.getProperty("line.separator")
      case Some(s)         => s
    }
  }
}