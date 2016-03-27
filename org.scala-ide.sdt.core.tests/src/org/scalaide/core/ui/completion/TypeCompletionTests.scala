package org.scalaide.core.ui.completion

import org.junit.Test

object TypeCompletionTests extends CompletionTests

class TypeCompletionTests {
  import TypeCompletionTests._

  @Test
  def trivialCompletion() = """
    class Foo(i: In^
    """ expectCompletions(Seq("Int"))

  @Test
  def nestedClassCompletion() = """
    package test

    object SomeTypes {
      class InnerClass
      object InnerObject
      type InnerType = String
    }""" andInSeparateFile """
    package com.github.mlangc.experiments

    class Bug {
      val ic: InnerC^
    }
    """ expectCompletions(Seq("InnerClass - test.SomeTypes"))

  @Test
  def nestedObjectCompletion() = """
    package test

    object SomeTypes {
      class InnerClass
      object InnerObject
      type InnerType = String
    }""" andInSeparateFile """
    package com.github.mlangc.experiments

    class Bug {
      val io: InnerO^
    }
    """ expectCompletions(Seq("InnerObject - test.SomeTypes"))

  @Test
  def nestedTypeCompletion() = """
    package test

    object SomeTypes {
      class InnerClass
      object InnerObject
      type InnerType = String
    }""" andInSeparateFile """
    package com.github.mlangc.experiments

    class Bug {
      val io: InnerT^
    }
    """ expectCompletions(Seq("InnerType - test.SomeTypes"))
}
