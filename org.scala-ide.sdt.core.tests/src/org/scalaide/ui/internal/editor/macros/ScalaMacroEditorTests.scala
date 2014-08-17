package org.scalaide.ui.internal.editor.macros

import org.junit.runner.RunWith
import org.junit.Test
import org.junit.Assert
import org.junit.runner.RunWith
import org.scalaide.ui.internal.editor.macros.ScalaLineNumberMacroEditor
import org.scalaide.ui.internal.editor.macros.MacroLineRange
import org.junit.internal.runners.JUnit4ClassRunner

@RunWith(classOf[JUnit4ClassRunner])
class ScalaLineNumberMacroEditorTests{
  @Test
  def setMyRange() {
    val scalaMacroEditor = new ScalaLineNumberMacroEditor{
      var macroExpansionRegions = List(new MacroLineRange(1,2), new MacroLineRange(4,5))
    }

    val expectedLineNumbers = Array(0,1,1,2,3,3,4,5,6,7)
    var counter = 0;
    for(t <- expectedLineNumbers){
      Assert.assertEquals(t,scalaMacroEditor.lineNumberCorresponder.getCorrespondingToLine(counter))
      counter += 1
    }
  }

  @Test
  def notResetedMyRange() {
    val scalaMacroEditor = new ScalaLineNumberMacroEditor{
      var macroExpansionRegions = List(new MacroLineRange(1,2), new MacroLineRange(4,5))
    }

    val expectedLineNumbers = Array(0,1,1,2,3,3,4,5,6,7)
    var counter = 0;
    for(t <- expectedLineNumbers){
      Assert.assertEquals(t,scalaMacroEditor.lineNumberCorresponder.getCorrespondingToLine(counter))
      counter += 1
    }

    counter = 0;
    scalaMacroEditor.macroExpansionRegions = List(new MacroLineRange(2,5))
    for(t <- expectedLineNumbers){
      Assert.assertEquals(t,scalaMacroEditor.lineNumberCorresponder.getCorrespondingToLine(counter))
      counter += 1
    }
  }

  @Test
  def resetMyRange() {
    val scalaMacroEditor = new ScalaLineNumberMacroEditor{
      var macroExpansionRegions = List(new MacroLineRange(1,2), new MacroLineRange(4,5))
    }

    val expectedLineNumbers = Array(0,1,1,2,3,3,4,5,6,7)
    var counter = 0;
    for(t <- expectedLineNumbers){
      Assert.assertEquals(t,scalaMacroEditor.lineNumberCorresponder.getCorrespondingToLine(counter))
      counter += 1
    }

    counter = 0;
    scalaMacroEditor.macroExpansionRegions = List(new MacroLineRange(2,5))
    scalaMacroEditor.lineNumberCorresponder.refreshLineNumbers()
    val newlyExpectedLineNumbers = Array(0,1,2,2,2,2,3,4,5,6,7)
    for(t <- newlyExpectedLineNumbers){
      Assert.assertEquals(t,scalaMacroEditor.lineNumberCorresponder.getCorrespondingToLine(counter))
      counter += 1
    }
  }
}