package org.scalaide.ui.internal.editor.hover

import org.eclipse.jface.text.IInformationControlCreator
import org.eclipse.jface.text.IRegion
import org.eclipse.jface.text.ITextViewer
import org.eclipse.jface.text.information.IInformationProvider
import org.eclipse.jface.text.information.IInformationProviderExtension
import org.eclipse.jface.text.information.IInformationProviderExtension2
import org.scalaide.ui.editor.hover.IScalaHover.HoverType
import org.scalaide.ui.editor.hover.IScalaHover

/**
 * Adapter interface for an [[ITextHover]] to work together with the
 * [[IInformationProvider]] interface.
 */
final class HoverInformationProvider(hover: Option[HoverType])
    extends IInformationProvider with IInformationProviderExtension with IInformationProviderExtension2 {

  @deprecated("Use getInformation2 instead", "4.0.0")
  def getInformation(viewer: ITextViewer, subject: IRegion): String =
    hover.map(_.getHoverInfo(viewer, subject)).orNull

  def getInformationPresenterControlCreator(): IInformationControlCreator =
    new FocusedControlCreator(IScalaHover.HoverFontId)

  def getSubject(viewer: ITextViewer, offset: Int): IRegion =
    hover.map(_.getHoverRegion(viewer, offset)).orNull

  def getInformation2(viewer: ITextViewer, subject: IRegion): AnyRef =
    hover.map(_.getHoverInfo2(viewer, subject)).orNull
}