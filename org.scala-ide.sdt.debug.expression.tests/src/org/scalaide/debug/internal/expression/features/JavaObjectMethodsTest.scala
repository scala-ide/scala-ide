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

  import TestValues.any2String
  import TestValues.ValuesTestCase._

  @Test
  def toStringWithParentheses(): Unit = eval("int.toString()", int, Java.boxed.String)

  @Test
  def toStringWithoutParentheses(): Unit = eval("int.toString", int, Java.boxed.String)

  @Test
  def toStringOnObject(): Unit = eval("list.toString", list, Java.boxed.String)

  @Test
  def toStringOnString(): Unit = eval("string.toString", string, Java.boxed.String)

  @Test
  def equalsWithNull(): Unit = {
    eval("libClass == null", false, Java.boxed.Boolean)
    eval("libClass != null", true, Java.boxed.Boolean)
    eval("libClass.selfRef() != null", true, Java.boxed.Boolean)
    eval("libClass.selfRef() != null", true, Java.boxed.Boolean)
  }

  @Test
  def hashCodeWithoutParens(): Unit = eval("int.hashCode", int, Java.boxed.Integer)

  @Test
  def hashCodeWithParens(): Unit = eval("int.hashCode()", int, Java.boxed.Integer)

}

object JavaObjectMethodsTest extends BaseIntegrationTestCompanion
