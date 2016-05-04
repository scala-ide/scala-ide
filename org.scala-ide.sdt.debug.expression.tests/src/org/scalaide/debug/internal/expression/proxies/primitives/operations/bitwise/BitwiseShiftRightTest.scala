/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.proxies.primitives.operations.bitwise

import org.junit.Test
import org.scalaide.debug.internal.expression.Names.Java
import org.scalaide.debug.internal.expression.BaseIntegrationTest
import org.scalaide.debug.internal.expression.BaseIntegrationTestCompanion
import org.scalaide.debug.internal.expression.DefaultBeforeAfterAll
import org.scalaide.debug.internal.expression.TestValues

class BitwiseShiftRightTest extends BaseIntegrationTest(BitwiseShiftRightTest) {

  import TestValues.ValuesTestCase._

  @Test
  def `byte >> sth`(): Unit = {
    eval("byte >> byte2", byte >> byte2, Java.primitives.int)
    eval("byte >> short2", byte >> short2, Java.primitives.int)
    eval("byte >> char2", byte >> char2, Java.primitives.int)
    eval("byte >> int2", byte >> int2, Java.primitives.int)
    eval("byte >> long2", byte >> long2, Java.primitives.int)
    expectReflectiveCompilationError("byte >> float")
    expectReflectiveCompilationError("byte >> double")
  }

  @Test
  def `short >> sth`(): Unit = {
    eval("short >> byte2", short >> byte2, Java.primitives.int)
    eval("short >> short2", short >> short2, Java.primitives.int)
    eval("short >> char2", short >> char2, Java.primitives.int)
    eval("short >> int2", short >> int2, Java.primitives.int)
    eval("short >> long2", short >> long2, Java.primitives.int)
    expectReflectiveCompilationError("short >> float")
    expectReflectiveCompilationError("short >> double")
  }

  @Test
  def `char >> sth`(): Unit = {
    eval("char >> byte2", char >> byte2, Java.primitives.int)
    eval("char >> short2", char >> short2, Java.primitives.int)
    eval("char >> char2", char >> char2, Java.primitives.int)
    eval("char >> int2", char >> int2, Java.primitives.int)
    eval("char >> long2", char >> long2, Java.primitives.int)
    expectReflectiveCompilationError("char >> float")
    expectReflectiveCompilationError("char >> double")
  }

  @Test
  def `int >> sth`(): Unit = {
    eval("int >> byte2", int >> byte2, Java.primitives.int)
    eval("int >> short2", int >> short2, Java.primitives.int)
    eval("int >> char", int >> char, Java.primitives.int)
    eval("int >> int2", int >> int2, Java.primitives.int)
    eval("int >> long2", int >> long2, Java.primitives.int)
    expectReflectiveCompilationError("int >> float")
    expectReflectiveCompilationError("int >> double")
  }

  @Test
  def `long >> sth`(): Unit = {
    eval("long >> byte2", long >> byte2, Java.primitives.long)
    eval("long >> short2", long >> short2, Java.primitives.long)
    eval("long >> char", long >> char, Java.primitives.long)
    eval("long >> int2", long >> int2, Java.primitives.long)
    eval("long >> long2", long >> long2, Java.primitives.long)
    expectReflectiveCompilationError("long >> float")
    expectReflectiveCompilationError("long >> double")
  }

  @Test
  def `float >> sth`(): Unit = {
    expectReflectiveCompilationError("float >> byte2")
    expectReflectiveCompilationError("float >> short2")
    expectReflectiveCompilationError("float >> char")
    expectReflectiveCompilationError("float >> int2")
    expectReflectiveCompilationError("float >> long2")
    expectReflectiveCompilationError("float >> float2")
    expectReflectiveCompilationError("float >> double")
  }

  @Test
  def `double >> sth`(): Unit = {
    expectReflectiveCompilationError("double >> byte2")
    expectReflectiveCompilationError("double >> short2")
    expectReflectiveCompilationError("double >> char")
    expectReflectiveCompilationError("double >> int2")
    expectReflectiveCompilationError("double >> long2")
    expectReflectiveCompilationError("double >> float")
    expectReflectiveCompilationError("double >> double2")
  }

  // TODO - SI-8462 - Scala returns incorrect types there
  @Test
  def `'c' >> 2L`(): Unit = eval("'c' >> 2L", 'c' >> 2L, Java.primitives.long)

  // TODO - SI-8462 - Scala returns incorrect types there
  @Test
  def `1 >> 2L`(): Unit = eval("1 >> 2L", 1 >> 2L, Java.primitives.long)
}

object BitwiseShiftRightTest extends BaseIntegrationTestCompanion with DefaultBeforeAfterAll
