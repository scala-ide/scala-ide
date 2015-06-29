package org.scalaide.ui.editor.hover

import org.scalaide.logging.HasLogger
import org.scalaide.core.IScalaPlugin
import org.scalaide.util.eclipse.OSGiUtils
import org.scalaide.core.SdtConstants
import org.scalaide.ui.editor.InteractiveCompilationUnitEditor
import org.eclipse.jface.text.ITextHover
import org.eclipse.jface.text.ITextHoverExtension
import org.eclipse.jface.text.ITextHoverExtension2
import org.scalaide.ui.internal.editor.hover.HoverInformationProvider
import org.scalaide.ui.editor.InteractiveCompilationUnitEditor
import org.eclipse.jface.text.information.IInformationProvider

object IScalaHover extends HasLogger {
  type HoverType = ITextHover with ITextHoverExtension with ITextHoverExtension2

  /** The Id that is used as a key for the preference store to retrieve the
   *  configured font style to be used by the hover.
   */
  final val HoverFontId = "org.scalaide.ui.font.hover"

  /** The Id that is used as a key for the preference store to retrieve the
   *  stored CSS file.
   */
  final val ScalaHoverStyleSheetId = "org.scalaide.ui.config.scalaHoverCss"

  /** This Id is used as a key for the preference store to retrieve the content
   *  of the default CSS file. This file is already stored in the IDE bundle
   *  and can be found with [[ScalaHoverStyleSheetPath]] but it is nonetheless
   *  necessary to store this file because it may change in a newer version of
   *  the IDE. To detect such a change we need to be able to compare the content
   *  of the CSS file.
   */
  final val DefaultScalaHoverStyleSheetId = "org.scalaide.ui.config.defaultScalaHoverCss"

  /** The path to the default CSS file */
  final val ScalaHoverStyleSheetPath = "/resources/scala-hover.css"

  /** The content of the CSS file [[ScalaHoverStyleSheetPath]]. */
  def ScalaHoverStyleSheet: String =
    IScalaPlugin().getPreferenceStore().getString(ScalaHoverStyleSheetId)

  /** The content of the CSS file [[ScalaHoverStyleSheetPath]]. */
  def DefaultScalaHoverStyleSheet: String = {
    OSGiUtils.fileContentFromBundle(SdtConstants.PluginId, ScalaHoverStyleSheetPath) match {
      case util.Success(css) =>
        css
      case util.Failure(f) =>
        logger.warn(s"CSS file '$ScalaHoverStyleSheetPath' could not be accessed.", f)
        ""
    }
  }

  /** Instantiate a Scala Hover for the given editor. */
  def apply(editor: InteractiveCompilationUnitEditor): HoverType = new ScalaHover(editor)

  /** Instantiate a Scala hover without an associated editor. */
  def apply(): HoverType = new ScalaHover

  /** Return an information creator for the default Scala hover. */
  def hoverInformationProvider(editor: InteractiveCompilationUnitEditor): IInformationProvider =
    new HoverInformationProvider(Some(apply(editor)))
}
