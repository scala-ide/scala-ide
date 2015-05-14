package org.scalaide.core.ui.completion

import org.junit.Test

object AccessibilityTests extends CompletionTests
class AccessibilityTests {

  import AccessibilityTests._

  def mkTestObj(
      packageName: String,
      additionInClass: String = "",
      additionInPackage: String = "",
      additionOutsideOfPackage: String = "") = s"""
    package $packageName {
      class Foo {
        private def secretPrivate(): Unit = ()
        private[this] def secretPrivateThis(): Unit = ()
        protected def secretProtected(): Unit = ()
        protected[$packageName] def secretProtectedInPackage(): Unit = ()
        def secretPublic(): Unit = ()

        $additionInClass
      }
      $additionInPackage
    }
    $additionOutsideOfPackage
  """

  @Test
  def noAccessToPrivateThisOutsideOfInstance() = {
    val pkgName = s"noAccessToPrivateThisOutsideOfInstance"
    mkTestObj(pkgName, additionInClass = """
      def someTests(other: Foo): Unit = {
        other.secr^
      }
    """) becomes
    mkTestObj(pkgName, additionInClass = """
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
  }

  @Test
  def accessToPrivateThisInsideOfInstance() = {
    val pkgName = "accessToPrivateThisInsideOfInstance"
    mkTestObj(pkgName, additionInClass = """
      def someTests(other: Foo): Unit = {
        this.secr^
      }
    """) becomes
    mkTestObj(pkgName, additionInClass = """
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
  }

  @Test
  def noAccessToPrivateInSubclass() = {
    val pkgName = "noAccessToPrivateInSubclass"
    mkTestObj(pkgName, additionInPackage = """
      class AccessibilityChecks extends Foo {
        def someTests: Unit = {
          this.secr^
        }
      }
    """) becomes
    mkTestObj(pkgName, additionInPackage = """
      class AccessibilityChecks extends Foo {
        def someTests: Unit = {
          this.secretProtected()^
        }
      }
    """) after Completion(
      completionToApply = "secretProtected(): Unit",
      expectedCompletions = Seq(
        "secretProtected(): Unit",
        "secretProtectedInPackage(): Unit",
        "secretPublic(): Unit"))
  }

  @Test
  def noAccessToPrivateInUnrelatedClass() = {
    val pkgName = "noAccessToPrivateInUnrelatedClass"
    mkTestObj(pkgName, additionInPackage = """
      class UnrelatedClass {
        def someTests(foo: Foo): Unit = {
          foo.secr^
        }
      }
    """) becomes
    mkTestObj(pkgName, additionInPackage = """
      class UnrelatedClass {
        def someTests(foo: Foo): Unit = {
          foo.secretPublic()^
        }
      }
    """) after Completion(
      completionToApply = "secretPublic(): Unit",
      expectedCompletions = Seq(
        "secretProtectedInPackage(): Unit",
        "secretPublic(): Unit"))
  }

  @Test
  def noAccessToPackageVisibilityInDifferentPackage() = {
    val pkgName = "noAccessToPackageVisibilityInDifferentPackage"
    mkTestObj(pkgName, additionOutsideOfPackage = s"""
      package other {
        class SomeChecsk {
          def foo(o: $pkgName.Foo): Unit = {
            o.secr^
          }
        }
      }
    """) becomes
    mkTestObj(pkgName, additionOutsideOfPackage = s"""
      package other {
        class SomeChecsk {
          def foo(o: $pkgName.Foo): Unit = {
            o.secretPublic()^
          }
        }
      }
    """) after Completion(
      completionToApply = "secretPublic(): Unit",
      expectedCompletions = Seq(
        "secretPublic(): Unit"))
  }
}
