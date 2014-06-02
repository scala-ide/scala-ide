package org.scalaide.ui.internal.editor.macros

import org.eclipse.jface.text.source.ISharedTextColors
import org.eclipse.jface.text.source.LineNumberChangeRulerColumn
import scala.collection.mutable.ArrayBuffer

trait ScalaLineNumberMacroEditor {
  var macroExpansionRegions: List[MacroLineRange]

  class LineNumbersCorresponder {
    private val internalBuffer = new ArrayBuffer[Int]()
    internalBuffer += 0

    def refreshLineNumbers() {
      internalBuffer.clear
      internalBuffer += 0
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

  class LineNumberChangeRulerColumnWithMacro(sharedColors: ISharedTextColors)
    extends LineNumberChangeRulerColumn(sharedColors) {
    override def createDisplayString(line: Int): String = {
      (lineNumberCorresponder.getCorrespondingToLine(line) + 1).toString
    }
  }

  val lineNumberCorresponder = new LineNumbersCorresponder
}
