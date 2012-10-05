package scala.tools.eclipse.ui

import org.junit.Test
import org.junit.Assert._
import org.eclipse.jdt.internal.core.util.SimpleDocument
import org.eclipse.jface.text.DocumentCommand
import org.eclipse.jface.text.IDocument
import org.eclipse.jface.text.TextViewer

import AutoEditStrategyTests._

/**
 * Those are not real test (does not check the document after applying the change), just regression tests.
 */
class TestBracketStrategy {

  @Test
  def autoAddClosing() {
    val document = new SimpleDocument("------")
    
    val command = new TestCommand(3, 0, "{", -1, true, true)
    
    new AutoCloseBracketStrategy().customizeDocumentCommand(document, command)

    checkCommand(3, 0, "{}", 4, false, true, command)
    
  }
  
  @Test
  def jumpClosing() {
    val document = new SimpleDocument("---}---")

    val command = new TestCommand(3, 0, "}", -1, true, true)
    
    new AutoCloseBracketStrategy().customizeDocumentCommand(document, command)

    checkCommand(3, 0, "", 4, true, true, command)
  }
  
  @Test
  def addClosing() {
    val document = new SimpleDocument("---}---")

    val command = new TestCommand(4, 0, "}", -1, true, true)
    
    new AutoCloseBracketStrategy().customizeDocumentCommand(document, command)

    checkCommand(4, 0, "}", -1, true, true, command)
  }
  
  @Test
  def addClosingEndOfFile() {
    val document = new SimpleDocument("------")
    
    val command = new TestCommand(6, 0, "}", -1, true, true)
    
    new AutoCloseBracketStrategy().customizeDocumentCommand(document, command)

    checkCommand(6, 0, "}", -1, true, true, command)
  }
  
  @Test
  def autoDeleteClosing() {
    val document = new SimpleDocument("---{}---")
    
    val command = new TestCommand(3, 1, "", -1, true, true)
    
    new AutoCloseBracketStrategy().customizeDocumentCommand(document, command)

    checkCommand(3, 2, "", -1, true, true, command)
  }
  
  @Test
  def deleteSingleOpening() {
    val document = new SimpleDocument("---{----")
    
    val command = new TestCommand(3, 1, "", -1, true, true)
    
    new AutoCloseBracketStrategy().customizeDocumentCommand(document, command)

    checkCommand(3, 1, "", -1, true, true, command)
  }

  @Test
  def deleteSingleOpeningEndOfFile() {
    val document = new SimpleDocument("------{")
    
    val command = new TestCommand(6, 1, "", -1, true, true)
    
    new AutoCloseBracketStrategy().customizeDocumentCommand(document, command)

    checkCommand(6, 1, "", -1, true, true, command)
  }
}