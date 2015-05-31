package org.scalaide.core.internal.extensions

import org.scalaide.extensions.ExtensionSetting
import org.scalaide.extensions.SaveActionSetting
import org.scalaide.extensions.saveactions._

object SaveActions {
  import ExtensionSetting.fullyQualifiedName

  /**
   * The ID which is used as key in the preference store to identify the actual
   * timeout value for save actions.
   */
  val SaveActionTimeoutId: String = "org.scalaide.extensions.SaveAction.Timeout"

  val documentSaveActionsData: Seq[(String, SaveActionSetting)] = Seq(
    fullyQualifiedName[RemoveTrailingWhitespace] → RemoveTrailingWhitespaceSetting,
    fullyQualifiedName[AddNewLineAtEndOfFile] → AddNewLineAtEndOfFileSetting,
    fullyQualifiedName[AutoFormatting] → AutoFormattingSetting,
    fullyQualifiedName[RemoveDuplicatedEmptyLines] → RemoveDuplicatedEmptyLinesSetting,
    fullyQualifiedName[TabToSpaceConverter] → TabToSpaceConverterSetting
  )

  val compilerSaveActionsData: Seq[(String, SaveActionSetting)] = Seq(
    fullyQualifiedName[AddMissingOverride] → AddMissingOverrideSetting,
    fullyQualifiedName[AddReturnTypeToPublicSymbols] → AddReturnTypeToPublicSymbolsSetting
  )

  /**
   * The settings for all existing save actions.
   */
  val saveActionSettings: Seq[SaveActionSetting] =
    documentSaveActionsData.map(_._2) ++ compilerSaveActionsData.map(_._2)
}
