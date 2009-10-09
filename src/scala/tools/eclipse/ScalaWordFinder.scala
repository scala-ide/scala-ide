/*
 * Copyright 2005-2009 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse

import scala.tools.nsc.Global

import org.eclipse.jface.text.{ BadLocationException, IDocument, IRegion, Region }

trait ScalaWordFinder { self : Global =>

  def findWord(document : IDocument, offset : Int) : IRegion = {

    def find(p : Char => Boolean) : IRegion = {
      var start = -2
      var end = -1
      
      try {
        var pos = offset
        
        while (pos >= 0 && p(document.getChar(pos)))
          pos -= 1
        
        start = pos
  
        pos = offset
        val len = document.getLength
        while (pos < len && p(document.getChar(pos)))
          pos += 1
        
        end = pos
      } catch {
        case ex : BadLocationException => // Deliberately ignored 
      }
  
      if (start >= -1 && end > -1) {
        if (start == offset && end == offset)
          new Region(offset, 0)
        else if (start == offset)
          new Region(start, end - start)
        else
          new Region(start+1, end-start-1)
      }
      else 
        null
    }
    
    val idRegion = find(syntaxAnalyzer.isIdentifierPart)
    if (idRegion == null || idRegion.getLength == 0)
      find(syntaxAnalyzer.isOperatorPart)
    else
      idRegion
  }
}
