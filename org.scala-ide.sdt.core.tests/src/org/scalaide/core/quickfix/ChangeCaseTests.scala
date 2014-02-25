package org.scalaide.core.quickfix

import org.junit.Test

class ChangeCaseTests {
  import QuickFixesTests._

  @Test def changeCase() {
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