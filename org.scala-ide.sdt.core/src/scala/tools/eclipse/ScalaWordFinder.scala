/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse

import scala.collection.immutable.IndexedSeq

import scala.tools.nsc.Global
import scala.tools.nsc.util.Chars.{ isIdentifierPart, isOperatorPart, CR, LF, FF }

import org.eclipse.jdt.core.IBuffer
import org.eclipse.jface.text.{ BadLocationException, IDocument, IRegion, Region }

import scala.tools.eclipse.contribution.weaving.jdt.IScalaWordFinder

object ScalaWordFinder extends IScalaWordFinder {

  def docToSeq(doc : IDocument) = new IndexedSeq[Char] {
    override def apply(i : Int) = doc.getChar(i)
    override def length = doc.getLength
  }

  def bufferToSeq(buf : IBuffer) = new IndexedSeq[Char] {
    override def apply(i : Int) = if (i >= buf.getLength()) '\0' else buf.getChar(i)
    override def length = buf.getLength
  }

  def findWord(document : IDocument, offset : Int) : IRegion =
    findWord(docToSeq(document), offset)

  def findWord(buffer : IBuffer, offset : Int) : IRegion =
    findWord(bufferToSeq(buffer), offset)

  def findWord(document : Seq[Char], offset : Int) : IRegion = {

    def find(p : Char => Boolean) : IRegion = {
      var start = -2
      var end = -1

      try {
        var pos = offset

        while (pos >= 0 && p(document(pos)))
          pos -= 1

        start = pos

        pos = offset
        val len = document.length
        while (pos < len && p(document(pos)))
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

    val idRegion = find(isIdentifierPart)
    if (idRegion == null || idRegion.getLength == 0)
      find(isOperatorPart)
    else
      idRegion
  }

  def findCompletionPoint(document : IDocument, offset : Int) : IRegion =
    findCompletionPoint(docToSeq(document), offset)

  def findCompletionPoint(buffer : IBuffer, offset : Int) : IRegion =
    findCompletionPoint(bufferToSeq(buffer), offset)

  def findCompletionPoint(document : Seq[Char], offset0 : Int) : IRegion = {
    def isWordPart(ch : Char) = isIdentifierPart(ch) || isOperatorPart(ch)

    val offset = if (offset0 >= document.length) (document.length - 1) else offset0
    val ch = document(offset)
    if (isWordPart(ch))
      findWord(document, offset)
    else if(offset > 0 && isWordPart(document(offset-1)))
      findWord(document, offset-1)
    else
      new Region(offset, 0)
  }
}
