package org.scalaide.core.ui

import org.junit.runner.RunWith
import org.junit.internal.runners.JUnit4ClassRunner
import org.eclipse.jface.text.Document
import org.scalaide.ui.internal.templates.ScalaTemplateContextType
import org.scalaide.ui.internal.templates.ScalaTemplateContext
import org.eclipse.jface.text.templates.Template
import org.junit.Test
import org.scalaide.core.SdtConstants
import org.junit.Assert

@RunWith(classOf[JUnit4ClassRunner])
class ScalaTemplateContextTest {

  val CARET = "_|_"

  def runTest( textSoFar: String, template : String, expectedResult : String ) : Unit = {

    val document = new Document(textSoFar.replace(CARET, ""))
    val contextType = new ScalaTemplateContextType
    contextType.setId( SdtConstants.PluginId + ".templates" )

    val context = new ScalaTemplateContext(contextType, document, textSoFar.indexOf(CARET), document.getLength)

    val templateObject = new Template("Test Template", "", contextType.getId, template, /*isAutoInsertable =*/ true)

    val buffer = context.evaluate(templateObject)

    Assert.assertEquals( expectedResult, buffer.getString )
  }

  @Test
  def basicTest(): Unit = {
    val textSoFar =
      "class TestOuterClass { \n" +
      "  " + CARET + "\n" +
      "}"
    val template = "case class Test()"
    val expectedResult = template
    runTest( textSoFar, template, expectedResult )
  }

  @Test
  def testMultilineTemplateWithLinuxLineEndings(): Unit = {
    val textSoFar =
      "class TestOuterClass { \n" +
      "  " + CARET + "\n" +
      "}"
    val template = "/**\n *\n *\n */"
    val expectedResult = "/**\n   *\n   *\n   */"
    runTest( textSoFar, template, expectedResult )
  }

  @Test
  def testMultilineTemplateWithCarriageReturnLineEndings(): Unit = {
    val textSoFar =
      "class TestOuterClass { \n" +
      "  " + CARET + "\n" +
      "}"
    val template = "/**\r *\r *\r */"
    val expectedResult = "/**\r   *\r   *\r   */"
    runTest( textSoFar, template, expectedResult )
  }

  @Test
  def testMultilineTemplateWithWindowsLineEndings(): Unit = {
    val textSoFar =
      "class TestOuterClass { \n" +
      "  " + CARET + "\n" +
      "}"
    val template = "/**\r\n *\r\n *\r\n */"
    val expectedResult = "/**\r\n   *\r\n   *\r\n   */"
    runTest( textSoFar, template, expectedResult )
  }
}
