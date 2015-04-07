/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.features

import org.junit.Test
import org.scalaide.debug.internal.expression.Names.Java
import org.scalaide.debug.internal.expression.BaseIntegrationTestCompanion
import org.scalaide.debug.internal.expression.BaseIntegrationTest

class StringAdditionTest extends BaseIntegrationTest(StringAdditionTest) {

  //objects
  @Test
  def ObjectPlusString(): Unit = disableOnJava8 { eval("List() + string", "List()Ala", Java.boxed.String) }

  @Test
  def StringPlusObject(): Unit = disableOnJava8 { eval("string + List()", "AlaList()", Java.boxed.String) }

  //chars
  @Test
  def charPlusString(): Unit = disableOnJava8 { eval("char + string", "cAla", Java.boxed.String) }

  @Test
  def stringPlusChar(): Unit = disableOnJava8 { eval("string + char", "Alac", Java.boxed.String) }

  //ints
  @Test
  def intPlusString(): Unit = disableOnJava8 { eval("int + string", "1Ala", Java.boxed.String) }

  @Test
  def stringPlusInt(): Unit = disableOnJava8 { eval("string + int", "Ala1", Java.boxed.String) }

  //floats
  @Test
  def floatPlusString(): Unit = eval("float + string", "1.1Ala", Java.boxed.String)

  @Test
  def stringPlusFloat(): Unit = disableOnJava8 { eval("string + float", "Ala1.1", Java.boxed.String) }

  //doubles
  @Test
  def doublePlusString(): Unit = disableOnJava8 { eval("double + string", "1.1Ala", Java.boxed.String) }

  @Test
  def stringPlusDouble(): Unit = disableOnJava8 { eval("string + double", "Ala1.1", Java.boxed.String) }

  //longs
  @Test
  def longPlusString(): Unit = eval("long + string", "1Ala", Java.boxed.String)

  @Test
  def stringPlusLong(): Unit = disableOnJava8 { eval("string + long", "Ala1", Java.boxed.String) }

  //booleans
  @Test
  def booleanPlusString(): Unit = eval("boolean + string", "falseAla", Java.boxed.String)

  @Test
  def stringPlusBoolean(): Unit = disableOnJava8 { eval("string + boolean", "Alafalse", Java.boxed.String) }

}

object StringAdditionTest extends BaseIntegrationTestCompanion
