/*
 * Copyright 2005-2009 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse

import org.eclipse.jdt.internal.ui.text.java.hover.{ JavadocBrowserInformationControlInput, JavadocHover }
import org.eclipse.jface.internal.text.html.HTMLPrinter
import org.eclipse.jface.text.{ ITextViewer, IRegion }

import scala.tools.eclipse.util.ReflectionUtils

class ScalaTextHover extends JavadocHover with ReflectionUtils {
  val getStyleSheetMethod = getDeclaredMethod(classOf[JavadocHover], "getStyleSheet")
  
  override def getHoverInfo2(textViewer : ITextViewer, hoverRegion :  IRegion) = {
    val i = super.getHoverInfo2(textViewer, hoverRegion)
    if (i != null)
      i
    else {
      val s = "Not yet implemented"
      val buffer = new StringBuffer(s)
      HTMLPrinter.insertPageProlog(buffer, 0, getStyleSheet0)
      HTMLPrinter.addPageEpilog(buffer)
      
      new JavadocBrowserInformationControlInput(null, null, buffer.toString, 0)
    }
  }
  
  def getStyleSheet0 = getStyleSheetMethod.invoke(null).asInstanceOf[String]
}
