package org.scalaide.ui.internal.editor.macros

import org.eclipse.jface.text.source.ISharedTextColors
import org.eclipse.jface.text.source.LineNumberChangeRulerColumn
import scala.collection.mutable.ArrayBuffer

/*
 * When macro expands to several lines, all this lines should
 * have the same line number in text editor. ScalaLineNumberMacroEditor
 * trait contains handling that classes.
 * */
trait ScalaLineNumberMacroEditor {
  protected[macros] def macroExpansionRegions: List[MacroLineRange]

  /*
   * Keep virtual line numbers, that corresponds to real line numbers in the editor
   * */
  class LineNumbersCorresponder {
    private val internalBuffer = new ArrayBuffer[Int]()
    internalBuffer += 0

    def refreshLineNumbers() {
      internalBuffer.clear
      internalBuffer += 0
    }

    private def lineNotCountedInMacroExpansion(line: Int) = {
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

  /* Substitude LineNumberChangeRulerColumn in ScalaSourceFileEditor.
   * Changes line numbers in the editor so, that multiple line macro
   * expansion corresponds to a single line */
  class LineNumberChangeRulerColumnWithMacro(sharedColors: ISharedTextColors)
    extends LineNumberChangeRulerColumn(sharedColors) {
    override def createDisplayString(line: Int): String = {
      (lineNumberCorresponder.getCorrespondingToLine(line) + 1).toString
    }
  }

  val lineNumberCorresponder = new LineNumbersCorresponder
}
