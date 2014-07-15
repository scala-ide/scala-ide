package org.scalaide.ui.internal.editor

trait ScalaLineNumberMacroEditor { self: ScalaMacroEditor =>
  import org.eclipse.jface.text.source.ISharedTextColors
  import org.eclipse.jface.text.source.LineNumberChangeRulerColumn

  class LineNumbersCorresponder {
    private var internalBuffer = getLineNumberBuffer

    def refreshLineNumbers() {
      internalBuffer = getLineNumberBuffer
    }

    private def getLineNumberBuffer = {
      import scala.collection.mutable.ArrayBuffer
      val internalBuffer = new ArrayBuffer[Int]()
      internalBuffer += 0
      internalBuffer
    }

    def lineNotCountedInMacroExpansion(line: Int) = {
      macroExpansionRegions.exists(region => region.startLine < line && line <= region.endLine)
    }

    def getCorrespondingToLine(lineNumber: Int): Int = {
      if (internalBuffer.isDefinedAt(lineNumber)) internalBuffer(lineNumber)
      else {
        val correspondingLineNumber = if (lineNotCountedInMacroExpansion(lineNumber)) getCorrespondingToLine(lineNumber - 1)
        else getCorrespondingToLine(lineNumber - 1) + 1
        internalBuffer += correspondingLineNumber
        correspondingLineNumber
      }
    }
  }

  val lineNumberCorresponder = new LineNumbersCorresponder

  class LineNumberChangeRulerColumnWithMacro(sharedColors: ISharedTextColors)
    extends LineNumberChangeRulerColumn(sharedColors) {
    override def createDisplayString(line: Int): String = {
      (lineNumberCorresponder.getCorrespondingToLine(line) + 1).toString
    }
  }
}
