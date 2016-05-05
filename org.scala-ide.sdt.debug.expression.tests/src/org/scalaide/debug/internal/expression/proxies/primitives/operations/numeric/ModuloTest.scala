/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.proxies.primitives.operations.numeric

import org.junit.Test
import org.scalaide.debug.internal.expression.Names.Java
import org.scalaide.debug.internal.expression.BaseIntegrationTest
import org.scalaide.debug.internal.expression.BaseIntegrationTestCompanion
import org.scalaide.debug.internal.expression.DefaultBeforeAfterAll
import org.scalaide.debug.internal.expression.TestValues
import org.scalaide.debug.internal.expression.DefaultBeforeAfterEach

class ModuloTest extends BaseIntegrationTest(ModuloTest) with DefaultBeforeAfterEach {

  import TestValues.ValuesTestCase._

  @Test
  def `byte % sth`(): Unit = {
    eval("byte % byte2", byte % byte2, Java.primitives.int)
    eval("byte % short2", byte % short2, Java.primitives.int)
    eval("byte % char2", byte % char2, Java.primitives.int)
    eval("byte % int2", byte % int2, Java.primitives.int)
    eval("byte % long2", byte % long2, Java.primitives.long)
    eval("byte % float", byte % float, Java.primitives.float)
    eval("byte % double", byte % double, Java.primitives.double)
  }

  @Test
  def `short % sth`(): Unit = {
    eval("short % byte2", short % byte2, Java.primitives.int)
    eval("short % short2", short % short2, Java.primitives.int)
    eval("short % char2", short % char2, Java.primitives.int)
    eval("short % int2", short % int2, Java.primitives.int)
    eval("short % long2", short % long2, Java.primitives.long)
    eval("short % float", short % float, Java.primitives.float)
    eval("short % double", short % double, Java.primitives.double)
  }

  @Test
  def `char % sth`(): Unit = {
    eval("char % byte2", char % byte2, Java.primitives.int)
    eval("char % short2", char % short2, Java.primitives.int)
    eval("char % char2", char % char2, Java.primitives.int)
    eval("char % int2", char % int2, Java.primitives.int)
    eval("char % long2", char % long2, Java.primitives.long)
    eval("char % float", char % float, Java.primitives.float)
    eval("char % double", char % double, Java.primitives.double)
  }

  @Test
  def `int % sth`(): Unit = {
    eval("int % byte2", int % byte2, Java.primitives.int)
    eval("int % short2", int % short2, Java.primitives.int)
    eval("int % char", int % char, Java.primitives.int)
    eval("int % int2", int % int2, Java.primitives.int)
    eval("int % long2", int % long2, Java.primitives.long)
    eval("int % float", int % float, Java.primitives.float)
    eval("int % double", int % double, Java.primitives.double)
  }

  @Test
  def `long % sth`(): Unit = {
    eval("long % byte2", long % byte2, Java.primitives.long)
    eval("long % short2", long % short2, Java.primitives.long)
    eval("long % char", long % char, Java.primitives.long)
    eval("long % int2", long % int2, Java.primitives.long)
    eval("long % long2", long % long2, Java.primitives.long)
    eval("long % float", long % float, Java.primitives.float)
    eval("long % double", long % double, Java.primitives.double)
  }

  @Test
  def `float % sth`(): Unit = {
    eval("float % byte2", float % byte2, Java.primitives.float)
    eval("float % short2", float % short2, Java.primitives.float)
    eval("float % char", float % char, Java.primitives.float)
    eval("float % int2", float % int2, Java.primitives.float)
    eval("float % long2", float % long2, Java.primitives.float)
    eval("float % float2", float % float2, Java.primitives.float)
    eval("float % double", float % double, Java.primitives.double)
  }

  @Test
  def `double % sth`(): Unit = {
    eval("double % byte2", double % byte2, Java.primitives.double)
    eval("double % short2", double % short2, Java.primitives.double)
    eval("double % char", double % char, Java.primitives.double)
    eval("double % int2", double % int2, Java.primitives.double)
    eval("double % long2", double % long2, Java.primitives.double)
    eval("double % float", double % float, Java.primitives.double)
    eval("double % double2", double % double2, Java.primitives.double)
  }
}

object ModuloTest extends BaseIntegrationTestCompanion with DefaultBeforeAfterAll
