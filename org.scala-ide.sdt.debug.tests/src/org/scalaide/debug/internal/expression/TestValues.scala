/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression

/**
 * Represents values from files in test workspaces which are used in tests.
 */
object TestValues {

  object AppObjectTestCase extends IntegrationTestCaseSettings {
    val projectName = ValuesTestCase.projectName
    val fileName = ValuesTestCase.fileName
    val breakpointLine = 49
  }

  object ArgumentsTestCase extends IntegrationTestCaseSettings {
    val projectName = "expr-eval-arguments"
    val fileName = "Arguments"
    val breakpointLine = 11
  }

  object ArraysTestCase extends IntegrationTestCaseSettings {
    val projectName = "expr-eval-arrays"
    val fileName = "Arrays"
    val breakpointLine = 25

    val emptyArray = Array[Int]()

    val intArray = Array(1, 2, 3)

    val stringArray = Array("Ala", "Ola", "Ula")

    val nestedArray = Array(
      Array(1, 2, 3),
      Array(4, 5, 6),
      Array(7, 8, 9))

    val nestedObjectArray = Array(
      Array("1", "2", "3"),
      Array("4", "5", "6"),
      Array("7", "8", "9"))

    val arrayIdentity = "arrayIdentity"
  }

  object CodeCompletionTestCase extends IntegrationTestCaseSettings {
    val projectName = "expr-eval-code-completion"
    val fileName = "CodeCompletion"
    val breakpointLine = 26
  }

  object ExceptionsTestCase extends IntegrationTestCaseSettings {
    val projectName = "expr-eval-exceptions"
    val fileName = "Exceptions"
    val breakpointLine = 12
  }

  object FileImportsTestCase extends IntegrationTestCaseSettings {
    val projectName = "expr-eval-values"
    val fileName = "FileImports"
    val breakpointLine = 12
  }

  object GenericsTestCase extends IntegrationTestCaseSettings {
    val projectName = "expr-eval-generics"
    val fileName = "Generics"
    val breakpointLine = 9
  }

  object ImplicitsTestCase extends IntegrationTestCaseSettings {
    val projectName = "expr-eval-values"
    val fileName = "Implicits"
    val breakpointLine = 15
  }

  object InnerMethodsTestCase extends IntegrationTestCaseSettings {
    val projectName = "expr-eval-inner-methods"
    val fileName = "InnerMethods"
    val breakpointLine = 10
  }

  object JavaTestCase extends IntegrationTestCaseSettings {
    val projectName = "expr-eval-java"
    val fileName = "Java"
    val breakpointLine = 8

    object JavaLibClass {
      val staticInt = 1
      val staticString = "staticString"
      val staticIntMethod = 700

      val normalInt = 1
      val normalString = "it's not static"
      val asString = "JavaLibClass()"

      object InnerStaticClass {
        val staticString = "otherString"
        val innerStaticDouble = 2.5 + staticInt;

        object InnerStaticInStatic {
          val staticInt = -4
          def innerStaticStringMethod(prefix: String, someNumber: Int) = prefix + someNumber + "_otherSuffix"
          def innerStaticIntMethod = 77
        }

        def innerStaticMethod(prefix: String) = staticStringMethod(prefix) + "_additionalSuffix"
      }

      def staticStringMethod(prefix: String) = prefix + "_suffix"
    }

    object JavaLibInterface {
      val staticInt = 1
      val staticString = "staticString"
    }
  }

  object NestedClassesTestCase extends IntegrationTestCaseSettings {
    val projectName = "expr-eval-nested-classes"
    val fileName = "NestedClasses"
    val breakpointLine = 25
  }

  object NestedTestCase extends IntegrationTestCaseSettings {
    val projectName = "expr-eval-nested-scope"
    val fileName = "Nested"
    val breakpointLine = 15

    val outerUsed = 1
    val outerUnused = 2
  }

  object NewInstancesTestCase extends IntegrationTestCaseSettings {
    val projectName = "expr-eval-values"
    val fileName = "NewInstances"
    val breakpointLine = 8
  }

  object TraitsTestCase extends IntegrationTestCaseSettings {
    val projectName = "expr-eval-traits"
    val fileName = "Traits"
    val breakpointLine = 11
  }

  object ThisTestCase extends IntegrationTestCaseSettings {
    val projectName = "expr-eval-this"
    val fileName = "This"
    val breakpointLine = 23

    val traitParam = 1
    val traitMethod = 1
    val classParam = 2
    val classMethod = 2
    val objectParam = 3
    val objectMethod = 3
  }

  object DifferentStackFramesTestCase extends IntegrationTestCaseSettings {
    val projectName = "expr-eval-different-stack-frames"
    val fileName = "DifferentStackFrames"
    val breakpointLine = 9

    val demonThreadName = "lib-deamon"
    val mainThread = "main-thread"
  }

  object ValuesTestCase extends IntegrationTestCaseSettings {
    val projectName = "expr-eval-values"
    val fileName = "Values"
    val breakpointLine = 44

    val byte: Byte = 4
    val byte2: Byte = 3
    val short: Short = 6
    val short2: Short = 5
    val int = 1
    val int2 = 2
    val double = 1.1
    val double2 = 2.3
    val float = 1.1f
    val float2 = 0.7f
    val char = 'c'
    val char2 = 'd'
    val boolean = false
    val boolean2 = true
    val string = "Ala"
    val list = List(1, 2, 3)
    val * = 1
    val long = 1l
    val long2 = 2l
  }

  object VarargsTestCase extends IntegrationTestCaseSettings {
    val projectName = "expr-eval-varargs"
    val fileName = "Varargs"
    val breakpointLine = 43

    val x = 13
    val y = 17
    val i1 = 1
    val i2 = 2
    val i3 = 3
    val i4 = 4
    val l1 = 1L
    val l2 = 2L
    val l4 = 4L
  }

  object VariablesTestCase extends IntegrationTestCaseSettings {
    val projectName = "expr-eval-variables"
    val fileName = "Variables"
    val breakpointLine = 17
  }

  object VisibilityTestCase extends IntegrationTestCaseSettings {
    val projectName = "expr-eval-visibility"
    val fileName = "Visibility"
    val breakpointLine = 23

    val traitParam = 1
    val traitMethod = 1
    val classParam = 2
    val classMethod = 2
    val objectParam = 3
    val objectMethod = 3
  }

  import scala.language.implicitConversions

  /**
   * To enable tests like:
   *
   * import TestValues.any2String
   * import TestValues.Values._
   *
   * @Test
   * def testSomething(): Unit = eval("int + double * long", int + double * long, Names.Java.boxed.Double)
   */
  implicit def any2String(x: Any) = x.toString()
}
