/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.ui.internal.editor.decorators

import org.eclipse.jface.text.source.AnnotationPainter
import org.eclipse.swt.custom.StyleRange
import org.eclipse.swt.graphics.Color

class HighlightingTextStyleStrategy(var fontStyle: Int) extends AnnotationPainter.ITextStyleStrategy {

  // `applyTextStyle` is called by the AnnotatinPainter when the text is painted,
  // so we don't have to notify the `styleRange` of any changes to `fontStyle`.

  def applyTextStyle(styleRange: StyleRange, annotationColor: Color) {
    styleRange.fontStyle = fontStyle
    styleRange.underline = false
    styleRange.underlineColor = annotationColor
  }
}