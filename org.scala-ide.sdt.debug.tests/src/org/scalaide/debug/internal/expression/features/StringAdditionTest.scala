/*
 * Copyright (c) 2014 Contributor. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Scala License which accompanies this distribution, and
 * is available at http://www.scala-lang.org/node/146
 */
package org.scalaide.debug.internal.expression.features

import org.junit.Test
import org.scalaide.debug.internal.expression.JavaBoxed
import org.scalaide.debug.internal.expression.BaseIntegrationTestCompanion
import org.scalaide.debug.internal.expression.BaseIntegrationTest

class StringAdditionTest extends BaseIntegrationTest(StringAdditionTest) {

  //objects
  @Test
  def `Object + String`(): Unit = eval("List() + string", "List()Ala", JavaBoxed.String)

  @Test
  def `String + Object`(): Unit = eval("string + List()", "AlaList()", JavaBoxed.String)

  //chars
  @Test
  def `char + string`(): Unit = eval("char + string", "cAla", JavaBoxed.String)

  @Test
  def `string + char`(): Unit = eval("string + char", "Alac", JavaBoxed.String)

  //ints
  @Test
  def `int + string`(): Unit = eval("int + string", "1Ala", JavaBoxed.String)

  @Test
  def `string + int`(): Unit = eval("string + int", "Ala1", JavaBoxed.String)

  //floats
  @Test
  def `float + string`(): Unit = eval("float + string", "1.1Ala", JavaBoxed.String)

  @Test
  def `string + float`(): Unit = eval("string + float", "Ala1.1", JavaBoxed.String)

  //doubles
  @Test
  def `double + string`(): Unit = eval("double + string", "1.1Ala", JavaBoxed.String)

  @Test
  def `string + double`(): Unit = eval("string + double", "Ala1.1", JavaBoxed.String)

  //longs
  @Test
  def `long + string`(): Unit = eval("long + string", "1Ala", JavaBoxed.String)

  @Test
  def `string + long`(): Unit = eval("string + long", "Ala1", JavaBoxed.String)

  //booleans
  @Test
  def `boolean + string`(): Unit = eval("boolean + string", "falseAla", JavaBoxed.String)

  @Test
  def `string + boolean`(): Unit = eval("string + boolean", "Alafalse", JavaBoxed.String)

}

object StringAdditionTest extends BaseIntegrationTestCompanion
