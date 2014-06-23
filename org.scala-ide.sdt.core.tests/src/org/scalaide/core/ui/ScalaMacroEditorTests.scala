package org.scalaide.core.ui

import org.junit.runner.RunWith
import org.junit.Test
import org.junit.Assert
import org.junit.Before
import org.scalaide.ui.internal.editor.ScalaMacroEditor
import org.junit.runner.RunWith
import org.junit.internal.runners.JUnit4ClassRunner
import org.scalaide.ui.internal.editor.ScalaLineNumberMacroEditor
import org.scalaide.ui.internal.editor.MyRange
import org.scalaide.ui.internal.editor.ScalaSourceFileEditor

@RunWith(classOf[JUnit4ClassRunner])
class ScalaLineNumberMacroEditorTests{
  @Test
  def setMyRange() {
    val scalaMacroEditor = new ScalaSourceFileEditor with ScalaMacroEditor with ScalaLineNumberMacroEditor
    scalaMacroEditor.macroExpansionRegions = List(new MyRange(1,2), new MyRange(4,5))
    val expectedLineNumbers = Array(0,1,1,2,3,3,4,5,6,7)
    var counter = 0;
    for(t <- expectedLineNumbers){
      Assert.assertEquals(t,scalaMacroEditor.lineNumberCorresponder.getCorrespondingToLine(counter))
      counter += 1
    }
  }

  @Test
  def notResetedMyRange() {
    val scalaMacroEditor = new ScalaSourceFileEditor with ScalaMacroEditor with ScalaLineNumberMacroEditor
    scalaMacroEditor.macroExpansionRegions = List(new MyRange(1,2), new MyRange(4,5))
    val expectedLineNumbers = Array(0,1,1,2,3,3,4,5,6,7)
    var counter = 0;
    for(t <- expectedLineNumbers){
      Assert.assertEquals(t,scalaMacroEditor.lineNumberCorresponder.getCorrespondingToLine(counter))
      counter += 1
    }

    counter = 0;
    scalaMacroEditor.macroExpansionRegions = List(new MyRange(2,5))
    for(t <- expectedLineNumbers){
      Assert.assertEquals(t,scalaMacroEditor.lineNumberCorresponder.getCorrespondingToLine(counter))
      counter += 1
    }
  }

  @Test
  def resetMyRange() {
    val scalaMacroEditor = new ScalaSourceFileEditor with ScalaMacroEditor with ScalaLineNumberMacroEditor
    scalaMacroEditor.macroExpansionRegions = List(new MyRange(1,2), new MyRange(4,5))
    val expectedLineNumbers = Array(0,1,1,2,3,3,4,5,6,7)
    var counter = 0;
    for(t <- expectedLineNumbers){
      Assert.assertEquals(t,scalaMacroEditor.lineNumberCorresponder.getCorrespondingToLine(counter))
      counter += 1
    }

    counter = 0;
    scalaMacroEditor.macroExpansionRegions = List(new MyRange(2,5))
    scalaMacroEditor.lineNumberCorresponder.refreshLineNumbers()
    val newlyExpectedLineNumbers = Array(0,1,2,2,2,2,3,4,5,6,7)
    for(t <- newlyExpectedLineNumbers){
      Assert.assertEquals(t,scalaMacroEditor.lineNumberCorresponder.getCorrespondingToLine(counter))
      counter += 1
    }
  }
}