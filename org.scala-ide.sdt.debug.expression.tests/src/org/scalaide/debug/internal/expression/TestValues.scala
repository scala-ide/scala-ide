/*
 * Copyright (c) 2014 - 2015 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression

/**
 * Represents values from files in test workspaces which are used in tests.
 */
object TestValues {

  object AppObjectTestCase extends IntegrationTestCaseSettings {
    val projectName = ValuesTestCase.projectName
    val fileName = ValuesTestCase.fileName
    val breakpointLine = 53
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

  object InstanceOfTestCase extends IntegrationTestCaseSettings {
    val projectName = "expr-instance-of"
    val fileName = "InstanceOf"
    val breakpointLine = 58

    trait A1
    class A2 extends A1
    object A3 extends A2

    trait B1
    class B2 extends B1
    class B3 extends B2

    trait C1
    trait C2 extends C1
    class C3 extends C2

    trait D1
    trait D2
    class D3 extends D2 with D1

    class Foo
    class Bar extends Foo

    def A1: Any = new A1 {}
    def A2: Any = new A2
    def B2: Any = new B2
    def B3: Any = new B3
    def C2: Any = new C2 {}
    def C3: Any = new C3
    def D3: Any = new D3

    def byte: Any = (4: Byte)
    def short: Any = (6: Short)
    def int: Any = 1
    def long: Any = 1l
    def char: Any = 'c'
    def double: Any = 1.1
    def float: Any = 1.1f
    def boolean: Any = false
    def string: Any = "Ala"
    def intList: Any = List(1, 2, 3)
    def stringList: Any = List("a", "b", "c")
    def unit: Any = ()

    def nullVal: Any = null

    def intArray: Any = Array(1, 2, 3)
    def doubleArray: Any = Array(1.0, 2.0, 3.0)
    def objectArray: Any = Array("a", "b", "c")

    def fooArray: Any = Array(new Foo)
    def barArray: Any = Array(new Bar)
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
    val breakpointLine = 39
  }

  object MethodsAsFunctions {

    class MethodsAsFunctionsTestCaseBase(val breakpointLine: Int) extends IntegrationTestCaseSettings {
      val projectName = "expr-eval-methods-as-functions"
      val fileName = "MethodsAsFunctions"
    }

    object MethodsAsFunctionsInnerTraitTestCase extends MethodsAsFunctionsTestCaseBase(19)

    object MethodsAsFunctionsInnerClassTestCase extends MethodsAsFunctionsTestCaseBase(23)

    object MethodsAsFunctionsInnerObjectTestCase extends MethodsAsFunctionsTestCaseBase(27)
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
    val breakpointLine = 15
  }

  object TraitsTestCase extends IntegrationTestCaseSettings {
    val projectName = "expr-eval-traits"
    val fileName = "Traits"
    val breakpointLine = 11
  }

  object ThisTestCase extends IntegrationTestCaseSettings {
    val projectName = "expr-eval-this"
    val fileName = "This"
    val breakpointLine = 25

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
    val breakpointLine = 48

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
    val bigDecimal = BigDecimal(1.1)
    val bigDecimal2 = BigDecimal(2.3)
    val char = 'c'
    val char2 = 'd'
    val boolean = false
    val boolean2 = true
    val string = "Ala"
    val list = List(1, 2, 3)
    val multilist = List(List(1), List(2, 3))
    val intArray = list.toArray
    val * = 1
    val long = 1l
    val long2 = 2l
  }

  object NamedParametersTestCase extends IntegrationTestCaseSettings {
    val projectName = "expr-eval-values"
    val fileName = "NamedParameters"
    val breakpointLine = 13
  }

  object NestedMethodsTestCase extends IntegrationTestCaseSettings {
    val projectName = "expr-eval-values"
    val fileName = "NestedMethods"
    val breakpointLine = 43
  }

  object OperatorsTestCase extends IntegrationTestCaseSettings {
    val projectName = "expr-eval-operators"
    val fileName = "Operators"
    val breakpointLine = 41
  }

  object SuperTestCase extends IntegrationTestCaseSettings {
    val projectName = "expr-eval-super"
    val fileName = "Super"
    val breakpointLine = 39
  }

  object ToolBoxBugsTestCase extends IntegrationTestCaseSettings {
    val projectName = "expr-eval-toolbox-bugs"
    val fileName = "ToolBoxBugs"
    val breakpointLine = 6
  }

  object VarargsTestCase extends IntegrationTestCaseSettings {
    val projectName = "expr-eval-varargs"
    val fileName = "Varargs"
    val breakpointLine = 45
  }

  object VariablesTestCase extends IntegrationTestCaseSettings {
    val projectName = "expr-eval-variables"
    val fileName = "Variables"
    val breakpointLine = 19
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

  object NestedPackagesTestCase extends IntegrationTestCaseSettings {
    val projectName = "expr-eval-nested-packages"
    val fileName = "NestedPackagesConsumer"
    val breakpointLine = 6
  }

  object NestedLambdaTestCase extends IntegrationTestCaseSettings {
    val projectName = "expr-eval-nested-lambda"
    val fileName = "NestedLambda"
    val breakpointLine = 17
  }

  object NestedLambdaInObjectTestCase extends IntegrationTestCaseSettings {
    val projectName = "expr-eval-nested-lambda-object"
    val fileName = "NestedLambdaObject"
    val breakpointLine = 17
  }
}
