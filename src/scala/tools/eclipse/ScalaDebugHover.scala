/*
 * Copyright 2005-2009 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse

import java.io.{ PrintWriter, StringWriter }

import scala.concurrent.SyncVar

import org.eclipse.jdt.internal.ui.text.java.hover.JavaSourceHover
import org.eclipse.jface.internal.text.html.HTMLPrinter
import org.eclipse.jface.text.{ ITextViewer, IRegion }

import scala.tools.eclipse.javaelements.ScalaCompilationUnit
import scala.tools.eclipse.util.ReflectionUtils

class ScalaDebugHover extends JavaSourceHover {

  override def getHoverInfo(textViewer : ITextViewer, hoverRegion :  IRegion) = {
    val scu = getCodeAssist.asInstanceOf[ScalaCompilationUnit]
    val doc = textViewer.getDocument
    
    val start = hoverRegion.getOffset
    val length = hoverRegion.getLength
    val end = start+length
    
    val th = scu.getTreeHolder
    import th._
    
    val source = scu.getSourceFile
    compiler.debugInfo(source, start, length)
  }
}
