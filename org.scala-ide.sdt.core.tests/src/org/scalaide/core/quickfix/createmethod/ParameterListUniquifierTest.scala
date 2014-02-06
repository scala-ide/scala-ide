package org.scalaide.core.quickfix.createmethod

import org.junit.Test
import org.junit.Assert._
import org.scalaide.core.internal.quickfix.createmethod.ParameterList
import org.scalaide.core.internal.quickfix.createmethod.ParameterListUniquifier

class ParameterListUniquifierTest {
  @Test def makeNamesUnique() {
    val expected: ParameterList = List(List(("someName", "Any"), ("arg", "Any"), ("arg1", "Any")), List(("arg2", "Any")))
    assertEquals(expected, ParameterListUniquifier.uniquifyParameterNames(List(List(("someName", "Any"), ("arg", "Any"), ("arg", "Any")), List(("arg", "Any")))))
  }
}