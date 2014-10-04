package org.scalaide.core.quickassist

import org.junit.Test

class CreateClassTests {
  import QuickFixesTests._

  @Test
  def createClassQuickFixes() {
    withQuickFixes("createclass/UsesMissingClass.scala")("Create class 'ThisClassDoesNotExist'")
  }
}