package test

object A {
  class ATestInner
  object C {
    object ACTestInner
  }
}

object B {
  new ATestI /*!*/ // Should be test.A.ATestInner
}
