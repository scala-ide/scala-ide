/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.proxies.primitives.operations.numeric

import org.junit.Test
import org.scalaide.debug.internal.expression.Names.Java
import org.scalaide.debug.internal.expression.BaseIntegrationTest
import org.scalaide.debug.internal.expression.BaseIntegrationTestCompanion
import org.scalaide.debug.internal.expression.TestValues

class MultiplicationTest extends BaseIntegrationTest(MultiplicationTest) {

  import TestValues.ValuesTestCase._

  @Test
  def `byte * sth`(): Unit = {
    eval("byte * byte2", byte * byte2, Java.boxed.Integer)
    eval("byte * short2", byte * short2, Java.boxed.Integer)
    eval("byte * char2", byte * char2, Java.boxed.Integer)
    eval("byte * int2", byte * int2, Java.boxed.Integer)
    eval("byte * long2", byte * long2, Java.boxed.Long)
    eval("byte * float", byte * float, Java.boxed.Float)
    eval("byte * double", byte * double, Java.boxed.Double)
  }

  @Test
  def `short * sth`(): Unit = {
    eval("short * byte2", short * byte2, Java.boxed.Integer)
    eval("short * short2", short * short2, Java.boxed.Integer)
    eval("short * char2", short * char2, Java.boxed.Integer)
    eval("short * int2", short * int2, Java.boxed.Integer)
    eval("short * long2", short * long2, Java.boxed.Long)
    eval("short * float", short * float, Java.boxed.Float)
    eval("short * double", short * double, Java.boxed.Double)
  }

  @Test
  def `char * sth`(): Unit = {
    eval("char * byte2", char * byte2, Java.boxed.Integer)
    eval("char * short2", char * short2, Java.boxed.Integer)
    eval("char * char2", char * char2, Java.boxed.Integer)
    eval("char * int2", char * int2, Java.boxed.Integer)
    eval("char * long2", char * long2, Java.boxed.Long)
    eval("char * float", char * float, Java.boxed.Float)
    eval("char * double", char * double, Java.boxed.Double)
  }

  @Test
  def `int * sth`(): Unit = {
    eval("int * byte2", int * byte2, Java.boxed.Integer)
    eval("int * short2", int * short2, Java.boxed.Integer)
    eval("int * char", int * char, Java.boxed.Integer)
    eval("int * int2", int * int2, Java.boxed.Integer)
    eval("int * long2", int * long2, Java.boxed.Long)
    eval("int * float", int * float, Java.boxed.Float)
    eval("int * double", int * double, Java.boxed.Double)
  }

  @Test
  def `long * sth`(): Unit = {
    eval("long * byte2", long * byte2, Java.boxed.Long)
    eval("long * short2", long * short2, Java.boxed.Long)
    eval("long * char", long * char, Java.boxed.Long)
    eval("long * int2", long * int2, Java.boxed.Long)
    eval("long * long2", long * long2, Java.boxed.Long)
    eval("long * float", long * float, Java.boxed.Float)
    eval("long * double", long * double, Java.boxed.Double)
  }

  @Test
  def `float * sth`(): Unit = {
    eval("float * byte2", float * byte2, Java.boxed.Float)
    eval("float * short2", float * short2, Java.boxed.Float)
    eval("float * char", float * char, Java.boxed.Float)
    eval("float * int2", float * int2, Java.boxed.Float)
    eval("float * long2", float * long2, Java.boxed.Float)
    eval("float * float2", float * float2, Java.boxed.Float)
    eval("float * double", float * double, Java.boxed.Double)
  }

  @Test
  def `double * sth`(): Unit = {
    eval("double * byte2", double * byte2, Java.boxed.Double)
    eval("double * short2", double * short2, Java.boxed.Double)
    eval("double * char", double * char, Java.boxed.Double)
    eval("double * int2", double * int2, Java.boxed.Double)
    eval("double * long2", double * long2, Java.boxed.Double)
    eval("double * float", double * float, Java.boxed.Double)
    eval("double * double2", double * double2, Java.boxed.Double)
  }
}

object MultiplicationTest extends BaseIntegrationTestCompanion
