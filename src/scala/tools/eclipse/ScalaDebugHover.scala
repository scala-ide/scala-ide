/*
 * Copyright 2005-2009 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse

import org.eclipse.jdt.internal.ui.text.java.hover.JavaSourceHover
import org.eclipse.jface.text.{ ITextViewer, IRegion }

import scala.tools.eclipse.javaelements.ScalaCompilationUnit

class ScalaDebugHover extends JavaSourceHover {

  override def getHoverInfo(textViewer : ITextViewer, hoverRegion :  IRegion) = {
    val scu = getCodeAssist.asInstanceOf[ScalaCompilationUnit]
    
    val start = hoverRegion.getOffset
    val length = hoverRegion.getLength
    val end = start+length
    
    scu.withCompilerResult({ crh =>
      import crh._
      compiler.debugInfo(sourceFile, start, length)
    })
  }
}
