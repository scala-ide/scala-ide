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

  final def rule[A, B](a: A)(pf: PartialFunction[A, Option[B]]): Option[B] =
    if (pf.isDefinedAt(a)) pf(a) else None

  final def subrule[A, B](a: A)(pf: PartialFunction[A, B]): Option[B] =
    if (pf.isDefinedAt(a)) Option(pf(a)) else None

  final def lookupChar[A](relPos: Int)(pf: PartialFunction[Char, A]): Option[A] = {
    val offset = textChange.start+relPos
    if (offset < 0 || offset >= document.length)
      None
    else {
      val c = document(offset)
      if (pf.isDefinedAt(c)) Option(pf(c)) else None
    }
  }
}

case class AutoEditSetting(
  id: String,
  name: String,
  description: String,
  partitions: Seq[String] = Seq()
) extends ExtensionSetting
