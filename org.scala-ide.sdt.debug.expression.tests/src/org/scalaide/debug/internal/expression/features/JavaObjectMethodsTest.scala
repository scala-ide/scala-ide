/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.features

import org.junit.Ignore
import org.junit.Test
import org.scalaide.debug.internal.expression.BaseIntegrationTest
import org.scalaide.debug.internal.expression.BaseIntegrationTestCompanion
import org.scalaide.debug.internal.expression.Names.Java
import org.scalaide.debug.internal.expression.TestValues

class JavaObjectMethodsTest extends BaseIntegrationTest(JavaObjectMethodsTest) {

  import TestValues.ValuesTestCase._

  @Test
  def toStringWithParentheses(): Unit = eval("int.toString()", int, Java.String)

  @Test
  def toStringWithoutParentheses(): Unit = eval("int.toString", int, Java.String)

  @Test
  def toStringOnObject(): Unit = eval("list.toString", list, Java.String)

  @Test
  def toStringOnString(): Unit = eval("string.toString", string, Java.String)

  @Test
  def equalsWithNull(): Unit = {
    eval("libClass == null", false, Java.primitives.boolean)
    eval("libClass != null", true, Java.primitives.boolean)
    eval("libClass.selfRef() != null", true, Java.primitives.boolean)
    eval("libClass.selfRef() != null", true, Java.primitives.boolean)
  }

  @Test
  def hashCodeWithoutParens(): Unit = eval("int.hashCode", int, Java.primitives.int)

  @Test
  def hashCodeWithParens(): Unit = eval("int.hashCode()", int, Java.primitives.int)

}

object JavaObjectMethodsTest extends BaseIntegrationTestCompanion
