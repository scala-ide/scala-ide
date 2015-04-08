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
  def ObjectPlusString(): Unit = disableOnJava8 { eval("List() + string", "List()Ala", Java.String) }

  @Test
  def StringPlusObject(): Unit = disableOnJava8 { eval("string + List()", "AlaList()", Java.String) }

  //chars
  @Test
  def charPlusString(): Unit = disableOnJava8 { eval("char + string", "cAla", Java.String) }

  @Test
  def stringPlusChar(): Unit = disableOnJava8 { eval("string + char", "Alac", Java.String) }

  //ints
  @Test
  def intPlusString(): Unit = disableOnJava8 { eval("int + string", "1Ala", Java.String) }

  @Test
  def stringPlusInt(): Unit = disableOnJava8 { eval("string + int", "Ala1", Java.String) }

  //floats
  @Test
  def floatPlusString(): Unit = eval("float + string", "1.1Ala", Java.String)

  @Test
  def stringPlusFloat(): Unit = disableOnJava8 { eval("string + float", "Ala1.1", Java.String) }

  //doubles
  @Test
  def doublePlusString(): Unit = disableOnJava8 { eval("double + string", "1.1Ala", Java.String) }

  @Test
  def stringPlusDouble(): Unit = disableOnJava8 { eval("string + double", "Ala1.1", Java.String) }

  //longs
  @Test
  def longPlusString(): Unit = eval("long + string", "1Ala", Java.String)

  @Test
  def stringPlusLong(): Unit = disableOnJava8 { eval("string + long", "Ala1", Java.String) }

  //booleans
  @Test
  def booleanPlusString(): Unit = eval("boolean + string", "falseAla", Java.String)

  @Test
  def stringPlusBoolean(): Unit = disableOnJava8 { eval("string + boolean", "Alafalse", Java.String) }

}

object StringAdditionTest extends BaseIntegrationTestCompanion
