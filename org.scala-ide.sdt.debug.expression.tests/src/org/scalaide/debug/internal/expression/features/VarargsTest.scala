/*

 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.features

import org.junit.Ignore
import org.junit.Test
import org.scalaide.debug.internal.expression.Names.Java
import org.scalaide.debug.internal.expression.BaseIntegrationTestCompanion
import org.scalaide.debug.internal.expression.BaseIntegrationTest
import org.scalaide.debug.internal.expression.TestValues.VarargsTestCase

/**
 * Test uses a class with many overloaded methods containing both constant list of arguments and varargs
 */
class VarargsTest extends BaseIntegrationTest(VarargsTest) {

  @Test
  def simpleVararg(): Unit = {
    eval(""" vararg.f("1", "2") """, "s*", Java.String)
    eval(""" vararg.g(1, "2") """, "i,s*", Java.String)
    eval(""" vararg.h("1", 2, "3") """, "s,i,s*", Java.String)
  }

  @Test
  def simpleVarargWithStrangeName(): Unit = {
    eval(""" vararg.f_+("1", "2") """, "s*", Java.String)
    eval(""" vararg.g_+(1, "2") """, "i,s*", Java.String)
  }

  @Test
  def simpleVarargWithNoArgs(): Unit = eval(""" vararg.f() """, "s*", Java.String)

  @Test
  def varargWithOneArgWithNoArgs(): Unit = eval(""" vararg.g(1) """, "i,s*", Java.String)

  @Ignore("TODO - O-4581 - proper method with varargs should be chosen when erased signature is the same")
  @Test
  def sameErasedSignatureVararg(): Unit = {
    eval(""" sameErasedSignature.f(1, 2, 3) """, 6, Java.boxed.Integer)
    eval(""" sameErasedSignature.f("1", "2", "3") """, "123", Java.String)
  }

  @Test
  def argumentAndVararg(): Unit = {
    eval(""" argumentAndVarArg.f("1") """, "s", Java.String)
    eval(""" argumentAndVarArg.f("1", "2") """, "s,s*", Java.String)
  }

  @Test
  def varargWithSimpleOverloads(): Unit = {
    eval(""" varargWithSimpleOverloads.f() """, s(""), Java.String)
    eval(""" varargWithSimpleOverloads.f("1") """, "s", Java.String)
    eval(""" varargWithSimpleOverloads.f("1", "2") """, "s*", Java.String)
  }

  @Ignore("TODO - O-4581 - proper method with varargs should be chosen with subtypes")
  @Test
  def varargWithSubtyping(): Unit = {
    eval(""" varargsAndSubtyping.f(new A) """, 1, Java.boxed.Integer)
    eval(""" varargsAndSubtyping.f(new B) """, "2", Java.String)
    eval(""" varargsAndSubtyping.f(new B, new A) """, 1, Java.boxed.Integer)
    eval(""" varargsAndSubtyping.f(new A, new B) """, 1, Java.boxed.Integer)
    eval(""" varargsAndSubtyping.f() """, "2", Java.String)
  }

  @Ignore("TODO - O-4581 - proper method with varargs should be chosen with primitives coercion")
  @Test
  def varargWithPrimitiveCoercion(): Unit = {
    eval(""" varargsAndPrimitiveCoercion.f(1) """, 1, Java.boxed.Integer)
    eval(""" varargsAndPrimitiveCoercion.f(1.0) """, 1.0, Java.boxed.Double)
    eval(""" varargsAndPrimitiveCoercion.f(1, 1.0) """, 2.0, Java.boxed.Double)
    expectReflectiveCompilationError(""" varargsAndPrimitiveCoercion.f() """)
  }
}

object VarargsTest extends BaseIntegrationTestCompanion(VarargsTestCase)
