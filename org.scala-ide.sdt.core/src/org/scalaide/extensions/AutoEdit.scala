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

  def perform(): Option[Change]

  final def rule[A](a: A)(pf: PartialFunction[A, Option[Change]]): Option[Change] =
    if (pf.isDefinedAt(a)) pf(a) else None

  final def subrule[A](a: A)(pf: PartialFunction[A, Change]): Option[Change] =
    if (pf.isDefinedAt(a)) Option(pf(a)) else None
}

case class AutoEditSetting(
  id: String,
  name: String,
  description: String
) extends ExtensionSetting
