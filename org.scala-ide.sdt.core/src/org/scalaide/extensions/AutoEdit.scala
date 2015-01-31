package org.scalaide.extensions

import org.scalaide.core.text.Change
import org.scalaide.core.text.TextChange

/**
 * Base interface for auto edits. An auto edit is an IDE extension that is
 * executed while an user types.
 */
trait AutoEdit extends ScalaIdeExtension with DocumentSupport {

  /**
   * Contains the text change the user did to the document. In case the user
   * inserted or removed a character, the text change has a range size of one.
   * In case the user removed a selection or pasted text into the editor the
   * text change can have a range of any size.
   *
   * This value must not be overwritten at the location where [[setting()]] or
   * [[perform()]] are implemented. Instead it is implemented by
   * the IDE at the location where the auto edit is instantiated.
   */
  val textChange: TextChange

  /**
   * The setting information is used in the "Scala > Editor > Auto Edits"
   * preference page of Eclipse. On this preference page users can enable or
   * disable auto edits and add further configuration to them. If the auto edit
   * is disabled, [[perform()]] will never be executed.
   */
  override def setting: AutoEditSetting

  /**
   * Performs the auto edit and returns an instance of [[Change]] when the auto
   * edit should be applied to the document. In case the auto edit couldn't be
   * applied, [[None]] needs to be returned.
   *
   * In the case that [[None]] is returned the next auto edit is performed, if
   * one exists. If a [[Some]] is returned, no further auto edits will be
   * performed.
   *
   * This method will never be called when this auto edit is disabled.
   */
  def perform(): Option[Change]

  /**
   * Checks if `pf` is defined for `a` and returns the result of `pf` if this is
   * the case. Returns [[None]] otherwise.
   */
  final def check[A, B](a: A)(pf: PartialFunction[A, Option[B]]): Option[B] =
    if (pf.isDefinedAt(a)) pf(a) else None

  /**
   * Checks if `pf` is defined for `a` and returns the result of `pf` wrapped in
   * an [[Option]] if this is the case. Returns [[None]] otherwise.
   */
  final def subcheck[A, B](a: A)(pf: PartialFunction[A, B]): Option[B] =
    if (pf.isDefinedAt(a)) Option(pf(a)) else None

  /**
   * Retrieves the char at position [[textChange.start+relPos]] if this position
   * is valid and checks if `pf` is defined for this character. If it is
   * defined, the result of `pf` is returned, otherwise [[None]].
   */
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

/**
 * Provides all information an user needs to know in order to understand what
 * the corresponding auto edit is doing.
 *
 * Each auto edit needs to provide such a setting - it is used in the "Scala >
 * Editor > Auto Edits" preference page of Eclipse and allows users to enable
 * or disable the auto edit.
 *
 * If no valid setting object can be found by the IDE, the auto edit will not
 * be made available to users.
 *
 * @param id
 *        A unique ID that identifies the save action. A good value is the fully
 *        qualified name of the save action class. This ID is only for internal
 *        use in the IDE, users may never see it.
 * @param name
 *        A short descriptive name for the save action. It is displayed to
 *        users.
 * @param description
 *        A (detailed) description about what the save action is doing. It is
 *        displayed to users and the only way to describe the behavior of the
 *        save action in detail.
 * @param partitions
 *        Contains all the partitions where the auto edit can be applied to. A
 *        partition is a region in the document, that spans a certain category
 *        of text. This could be the comment partition of the string partition,
 *        which represent - as their name suggests - comments and strings. Each
 *        document is divided in such partitions to allow fine-granular
 *        behavior about where auto edits should be applied to.
 *
 *        Available partition values can be found in
 *        [[org.scalaide.core.lexical.ScalaPartitions]] and in
 *        [[org.eclipse.jdt.ui.text.IJavaPartitions]]. Furthermore, the value
 *        [[org.eclipse.jface.text.IDocument.DEFAULT_CONTENT_TYPE]] can be used.
 *        In case no partitions are provided, the auto edit will be applied to
 *        all available partitions.
 */
case class AutoEditSetting(
  override val id: String,
  name: String,
  description: String,
  partitions: Set[String] = Set()
) extends ExtensionSetting
