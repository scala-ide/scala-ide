package accessibility {

  class Foo {
    private def secretPrivate(): Unit
    private[this] def secretPrivateThis(): Unit

    protected def secretProtected(): Unit

    protected[accessibility] def secretProtectedInPackage(): Unit

    def secretPublic(): Unit

    def someTests(other: Foo) {
      other.secr /*!*/ // should be all but scretThis

      this.secr /*!*/ // should hit five completions
    }
  }

  class AccessibilityChecks extends Foo {
    def someTests {
      this.secr /*!*/ // should not list secretPrivate*
    }
  }

  class UnrelatedClass {
    def someTests(foo: Foo) {
      foo.secr /*!*/ // should list public and protected[accessiblity]
    }
  }

}

package other {
  class SomeChecsk {
    def foo(o: accessibility.Foo) {
      o.secr /*!*/ // should only match secretPublic
    }
  }
}