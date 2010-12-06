/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse

import org.eclipse.jdt.internal.ui.text.java.hover.JavaSourceHover
import org.eclipse.jface.text.{ ITextViewer, IRegion }

import scala.tools.eclipse.javaelements.ScalaCompilationUnit

class ScalaDebugHover extends JavaSourceHover {
  
  private val _noHoverInfo = "" // could return null but prefere to return empty (see API of ITextHover)

  // override deprecated getHoverInfo instead of getHoverInfo2 like JavaSourceHover    
  override def getHoverInfo(textViewer : ITextViewer, hoverRegion :  IRegion) = {
    getCodeAssist match {
      case null => _noHoverInfo // getCodeAssist can return null
      case scu : ScalaCompilationUnit => {
        val start = hoverRegion.getOffset
        val length = hoverRegion.getLength
        val end = start+length
        scu.withSourceFile({ (sourceFile, compiler) =>
          compiler.debugInfo(sourceFile, start, length)
        })
      }
      case _ => super.getHoverInfo(textViewer, hoverRegion)
    }
  }
  
  /*
   * @see org.eclipse.jface.text.ITextHoverExtension2#getHoverInfo2(org.eclipse.jface.text.ITextViewer, org.eclipse.jface.text.IRegion)
   * @since 3.4
   */
  // duplicate code from AbstractJavaEditorText to avoid loosing feature if super code change.
  override def getHoverInfo2(textViewer : ITextViewer , hoverRegion : IRegion) : Object =  getHoverInfo(textViewer, hoverRegion)
}
