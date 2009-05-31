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

import scala.tools.nsc.util.BatchSourceFile

import scala.tools.eclipse.javaelements.ScalaCompilationUnit
import scala.tools.eclipse.util.ReflectionUtils

class ScalaTextHover extends JavaSourceHover {

  override def getHoverInfo(textViewer : ITextViewer, hoverRegion :  IRegion) = {
    val scu = getCodeAssist.asInstanceOf[ScalaCompilationUnit]
    val doc = textViewer.getDocument
    
    val start = hoverRegion.getOffset
    val length = hoverRegion.getLength
    val end = start+length
    
    val th = scu.getTreeHolder
    import th._
    
    val bsf = new BatchSourceFile(scu.aFile)
    val pos = compiler.rangePos(bsf, start, start, end)
    
    val tree = compiler.locateTree(pos)
    val sw = new StringWriter
    val pw = new PrintWriter(sw)
    compiler.treePrinters.create(pw).print(tree)
    pw.flush
    
    val typed = new SyncVar[Either[compiler.Tree, Throwable]]
    compiler.askTypeAt(pos, typed)
    val typ = typed.get.left.toOption match {
      case Some(tree) =>
        val sw = new StringWriter
        val pw = new PrintWriter(sw)
        compiler.treePrinters.create(pw).print(tree)
        pw.flush
        sw.toString
      case None => "<None>"
    }
    
    doc.get(start, length)+" : "+scu.getElementName+" ("+start+", "+end+")\n\nlocateTree:\n"+sw.toString+"\n\naskTypeAt:\n"+typ
    
    /*
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
    */
  }
}
