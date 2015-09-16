package org.scalaide.core.internal.extensions

import org.scalaide.extensions.AutoEditSetting
import org.scalaide.extensions.ExtensionSetting
import org.scalaide.extensions.autoedits._

object AutoEdits {
  import ExtensionSetting.fullyQualifiedName

  val autoEditData: Seq[(String, AutoEditSetting)] = Seq(
    fullyQualifiedName[ConvertToUnicode] → ConvertToUnicodeSetting,
    fullyQualifiedName[SmartSemicolonInsertion] → SmartSemicolonInsertionSetting,
    fullyQualifiedName[JumpOverClosingCurlyBrace] → JumpOverClosingCurlyBraceSetting,
    fullyQualifiedName[RemoveCurlyBracePair] → RemoveCurlyBracePairSetting,
    fullyQualifiedName[RemoveParenthesisPair] → RemoveParenthesisPairSetting,
    fullyQualifiedName[CreateMultiplePackageDeclarations] → CreateMultiplePackageDeclarationsSetting,
    fullyQualifiedName[ApplyTemplate] → ApplyTemplateSetting,
    fullyQualifiedName[RemoveBracketPair] → RemoveBracketPairSetting,
    fullyQualifiedName[RemoveAngleBracketPair] → RemoveAngleBracketPairSetting,
    fullyQualifiedName[JumpOverClosingParenthesis] → JumpOverClosingParenthesisSetting,
    fullyQualifiedName[JumpOverClosingBracket] → JumpOverClosingBracketSetting,
    fullyQualifiedName[JumpOverClosingAngleBracket] → JumpOverClosingAngleBracketSetting,
    fullyQualifiedName[CloseString] → CloseStringSetting,
    fullyQualifiedName[CloseChar] → CloseCharSetting,
    fullyQualifiedName[SurroundBlock] → SurroundBlockSetting,
    fullyQualifiedName[SurroundSelectionWithString] → SurroundSelectionWithStringSetting,
    fullyQualifiedName[SurroundSelectionWithParentheses] → SurroundSelectionWithParenthesesSetting,
    fullyQualifiedName[SurroundSelectionWithBraces] → SurroundSelectionWithBracesSetting,
    fullyQualifiedName[SurroundSelectionWithBrackets] → SurroundSelectionWithBracketsSetting,
    fullyQualifiedName[SurroundSelectionWithAngleBrackets] → SurroundSelectionWithAngleBracketsSetting,
    fullyQualifiedName[CloseCurlyBrace] → CloseCurlyBraceSetting,
    fullyQualifiedName[CloseParenthesis] → CloseParenthesisSetting,
    fullyQualifiedName[CloseBracket] → CloseBracketSetting,
    fullyQualifiedName[CloseAngleBracket] → CloseAngleBracketSetting
  )

  /**
   * The settings for all existing auto edits.
   */
  val autoEditSettings: Seq[AutoEditSetting] =
    autoEditData.map(_._2)
}
