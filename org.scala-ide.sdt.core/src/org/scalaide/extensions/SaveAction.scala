package org.scalaide.extensions

import org.scalaide.core.text.Change

/**
 * Base interface for save actions. A safe action is an IDE extension that is
 * executed whenever a document is saved.
 */
trait SaveAction extends ScalaIdeExtension {

  /**
   * The setting information is used in the "Scala > Editor > Save Actions"
   * preference page of Eclipse. On this preference page users can enable or
   * disable save actions. If the save action is disabled, [[perform()]] will
   * never be executed.
   */
  def setting: SaveActionSetting

  /**
   * Performs the save action and returns all changes that should be done on
   * the document. This method will never be called when this save action is
   * disabled.
   */
  def perform(): Seq[Change]
}

/**
 * Provides all information an users needs to know in order to understand what
 * the corresponding save action is doing.
 *
 * Each save action needs to provide such a setting - it is used in the Scala >
 * Editor > Save Actions preference page of Eclipse and allows users to enable
 * or disable the save action.
 *
 * If no valid setting object can be found by the IDE, the save action will not
 * be made available to users.
 *
 * @param id
 *        A uniqe ID that identifies the save action. A good value is the fully
 *        qualified name of the save action class. This ID is only for internal
 *        use in the IDE, users may never see it.
 * @parem name
 *        A short descriptive name for the save action. It is displayed to
 *        users.
 * @param description
 *        A (detailed) description about what the save action is doing. It is
 *        displayed to users and the only way to describe the behavior of the
 *        save action in detail.
 * @param textBefore
 *        A short code example that depicts on which part of the sources the
 *        save action is working on. In contrast to `textAfter` this shows the
 *        state of the code before the save action is executed.
 * @param textAfter
 *        In contrast to `textBefore` this shows the state of the code after the
 *        save action is executed
 */
case class SaveActionSetting(
  id: String,
  name: String,
  description: String,
  textBefore: String,
  textAfter: String
) extends ExtensionSetting