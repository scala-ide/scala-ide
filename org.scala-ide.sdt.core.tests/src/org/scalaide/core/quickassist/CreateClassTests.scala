package org.scalaide.core.quickassist

import org.junit.Test

class CreateClassTests {
  import UiQuickAssistTests._

  @Test
  def createClassQuickFixes(): Unit = {
    withQuickFixes("createclass/UsesMissingClass.scala")("Create class 'ThisClassDoesNotExist'")
  }
}
