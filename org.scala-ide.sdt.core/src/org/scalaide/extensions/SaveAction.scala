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
  override def setting: SaveActionSetting

  /**
   * Performs the save action and returns all changes that should be done on
   * the document. This method will never be called when this save action is
   * disabled.
   */
  def perform(): Seq[Change]
}

/**
 * Provides all information an user needs to know in order to understand what
 * the corresponding save action is doing.
 *
 * Each save action needs to provide such a setting - it is used in the "Scala >
 * Editor > Save Actions" preference page of Eclipse and allows users to enable
 * or disable the save action.
 *
 * If no valid setting object can be found by the IDE, the save action will not
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
 * @param codeExample
 *        A short code example that depicts on which part of the sources the
 *        save action is working on. The IDE can execute save actions
 *        (especially the corresponding save action) on this code in order to
 *        show users how they change the code.
 */
case class SaveActionSetting(
  override val id: String,
  name: String,
  description: String,
  codeExample: String
) extends ExtensionSetting
