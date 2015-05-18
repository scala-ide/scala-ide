package org.scalaide.core.quickassist

import org.junit.Test

class ChangeCaseTests {
  import UiQuickAssistTests._

  @Test def changeCase(): Unit = {
    withManyQuickFixesPerLine("changecase/ChangeCase.scala")(
      List(
        List("Change to 'meThod1'",
             "Change to 'metHod1'"),
        List("Change to 'subSequence'"),
        List("Change to 'meThod2'",
             "Change to 'metHod2'")
      )
    )
  }

}
