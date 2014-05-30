package org.scalaide.core.ui.completion

import org.junit.Test

object AccessibilityTests extends CompletionTests
class AccessibilityTests {

  import AccessibilityTests._

  def mkTestObj(
      additionInClass: String = "",
      additionInPackage: String = "",
      additionOutsideOfPackage: String = "") = s"""
    package accessibility {
      class Foo {
        private def secretPrivate(): Unit = ()
        private[this] def secretPrivateThis(): Unit = ()
        protected def secretProtected(): Unit = ()
        protected[accessibility] def secretProtectedInPackage(): Unit = ()
        def secretPublic(): Unit = ()

        $additionInClass
      }
      $additionInPackage
    }
    $additionOutsideOfPackage
  """

  @Test
  def noAccessToPrivateThisOutsideOfInstance() =
    mkTestObj(additionInClass = """
      def someTests(other: Foo): Unit = {
        other.secr^
      }
    """) becomes
    mkTestObj(additionInClass = """
      def someTests(other: Foo): Unit = {
        other.secretPrivate()^
      }
    """) after Completion(
      completionToApply = "secretPrivate(): Unit",
      expectedCompletions = Seq(
        "secretPrivate(): Unit",
        "secretProtected(): Unit",
        "secretProtectedInPackage(): Unit",
        "secretPublic(): Unit"))

  @Test
  def accessToPrivateThisInsideOfInstance() =
    mkTestObj(additionInClass = """
      def someTests(other: Foo): Unit = {
        this.secr^
      }
    """) becomes
    mkTestObj(additionInClass = """
      def someTests(other: Foo): Unit = {
        this.secretPrivateThis()^
      }
    """) after Completion(
      completionToApply = "secretPrivateThis(): Unit",
      expectedCompletions = Seq(
        "secretPrivate(): Unit",
        "secretPrivateThis(): Unit",
        "secretProtected(): Unit",
        "secretProtectedInPackage(): Unit",
        "secretPublic(): Unit"))

  @Test
  def noAccessToPrivateInSubclass() =
    mkTestObj(additionInPackage = """
      class AccessibilityChecks extends Foo {
        def someTests {
          this.secr^
        }
      }
    """) becomes
    mkTestObj(additionInPackage = """
      class AccessibilityChecks extends Foo {
        def someTests {
          this.secretProtected()^
        }
      }
    """) after Completion(
      completionToApply = "secretProtected(): Unit",
      expectedCompletions = Seq(
        "secretProtected(): Unit",
        "secretProtectedInPackage(): Unit",
        "secretPublic(): Unit"))

  @Test
  def noAccessToPrivateInUnrelatedClass() =
    mkTestObj(additionInPackage = """
      class UnrelatedClass {
        def someTests(foo: Foo) {
          foo.secr^
        }
      }
    """) becomes
    mkTestObj(additionInPackage = """
      class UnrelatedClass {
        def someTests(foo: Foo) {
          foo.secretPublic()^
        }
      }
    """) after Completion(
      completionToApply = "secretPublic(): Unit",
      expectedCompletions = Seq(
        "secretProtectedInPackage(): Unit",
        "secretPublic(): Unit"))

  @Test
  def noAccessToPackageVisibilityInDifferentPackage() =
    mkTestObj(additionOutsideOfPackage = """
      package other {
        class SomeChecsk {
          def foo(o: accessibility.Foo) {
            o.secr^
          }
        }
      }
    """) becomes
    mkTestObj(additionOutsideOfPackage = """
      package other {
        class SomeChecsk {
          def foo(o: accessibility.Foo) {
            o.secretPublic()^
          }
        }
      }
    """) after Completion(
      completionToApply = "secretPublic(): Unit",
      expectedCompletions = Seq(
        "secretPublic(): Unit"))
}