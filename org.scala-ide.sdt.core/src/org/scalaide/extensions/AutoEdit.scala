package org.scalaide.extensions

import org.scalaide.core.text.Change
import org.scalaide.core.text.TextChange

/**
 * Base interface for auto edits. An auto edit is an IDE extension that is
 * executed while an user types.
 */
trait AutoEdit extends ScalaIdeExtension with DocumentSupport {

  val textChange: TextChange

  override def setting: AutoEditSetting

  def perform(): Change
}

case class AutoEditSetting(
  id: String,
  name: String,
  description: String
) extends ExtensionSetting
