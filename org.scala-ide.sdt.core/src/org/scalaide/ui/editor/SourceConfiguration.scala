package org.scalaide.ui.editor

import org.scalaide.core.internal.hyperlink.DeclarationHyperlinkDetector
import org.scalaide.core.internal.hyperlink.ImplicitHyperlinkDetector
import org.eclipse.jface.text.hyperlink.IHyperlinkDetectorExtension
import org.eclipse.jface.text.hyperlink.IHyperlinkDetector
import org.scalaide.core.internal.hyperlink.ScalaHyperlink
import org.eclipse.jface.text.IRegion
import org.eclipse.jface.text.hyperlink.IHyperlink
import org.eclipse.jface.text.hyperlink.AbstractHyperlinkDetector

/** Factory object for creating Scala-specific editor goodies, like auto-edits or
 *  hyperlink detectors.
 */
object SourceConfiguration {

  /** A hyperlink detector that navigates to the declaration of the symbol under cursor.
   *
   *  @note For proper functionality, this detector needs an `editor` as part of the context. Make
   *        sure to configure this before returning it to the platform:
   *        {{{
   *          val detector = SourceConfiguration.scalaDeclarationDetector
   *          detector.setContext(editor)
   *        }}}
   */
  def scalaDeclarationDetector: AbstractHyperlinkDetector = DeclarationHyperlinkDetector()

  /** A hyperlink detector that navigates to the implicit, if there is an implicit application under cursor.
   *
   *  @note This detector only works if the editor has implicit-annotations enabled.
   *  @note For proper functionality, this detector needs an `editor` as part of the context. Make
   *        sure to configure this before returning it to the platform:
   *        {{{
   *          val detector = SourceConfiguration.scalaDeclarationDetector
   *          detector.setContext(editor)
   *        }}}
   */
  def implicitDeclarationDetector: AbstractHyperlinkDetector = ImplicitHyperlinkDetector()

  /** Create a hyperlink that can open a Scala editor.
   *
   * @param openableOrUnit The unit to open (either an Openable, or an `InteractiveCompilationUnit`)
   * @param pos            The position at which the editor should be open
   * @param len            The length of the selection in the open editor
   * @param label          A hyperlink label (additional information)
   * @param text           The name of the hyperlink, to be shown in a menu if there's more than one hyperlink
   * @param wordRegion     The region to underline in the start editor
   */
  def scalaHyperlink(openableOrUnit: AnyRef, pos: Int, len: Int, label: String, text: String, wordRegion: IRegion): IHyperlink =
    new ScalaHyperlink(openableOrUnit, pos, len, label, text, wordRegion)
}