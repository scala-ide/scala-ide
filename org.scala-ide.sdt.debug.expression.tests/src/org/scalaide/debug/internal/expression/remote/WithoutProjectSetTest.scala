/*
 * Copyright (c) 2015 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression
package remote

import Names.Java
import Names.Scala
import org.junit.Ignore
import org.junit.Test
import org.junit.BeforeClass
import org.junit.Assert._
import TestValues.ValuesTestCase._
import org.scalaide.debug.internal.expression.BaseIntegrationTest
import org.scalaide.debug.internal.expression.BaseIntegrationTestCompanion
import org.scalaide.debug.internal.expression.EclipseProjectContext
import org.scalaide.debug.internal.expression.ReflectiveCompilationFailedWithClassNotFound

class WithoutProjectSetTest extends BaseIntegrationTest(WithoutProjectSetTest) {

  @Test(expected = classOf[MethodTypeInferred])
  def testMethodAsLambda(): Unit =
    eval(""" list.map(alaMethod) """, list.map(alaMethod), Scala.::)

  @Test
  def objectAccess(): Unit =
    expectReflectiveCompilationError(""" LibObject.id """)

  @Test
  def javaStaticMemberTest(): Unit =
    expectReflectiveCompilationError("JavaLibClass.staticString")

  @Test
  def localValAccess(): Unit =
    eval(""" list """, list, Scala.::)

  @Test
  def classValAccess(): Unit =
    eval(""" outer """, outer, Java.String)

  @Test
  def localMethod(): Unit =
    eval(""" alaMethod(1) """, alaMethod(1), Java.String)

  @Test
  def nestedMethod(): Unit =
    expectReflectiveCompilationError(""" testFunction() """)

  @Test
  def typedLambda(): Unit =
    eval(""" list.map( (i: Int)=> i + 1) """, list.map((i: Int) => i + 1), Scala.::)

  @Test(expected = classOf[FunctionProxyArgumentTypeNotInferredException])
  def lambda(): Unit =
    eval(""" list.map( _.toString) """, list.map(_.toString), Scala.::)

  @Test
  def lambdaThatRequireType(): Unit =
    expectReflectiveCompilationError(""" list.map( _ + 1) """)

  @Test
  def lambdaThatRequireReturnValue(): Unit =
    expectReflectiveCompilationError(""" list.filter( _ > 0) """)

  @Test
  def typedLambdaThatRequireReturnValue(): Unit =
    eval(""" list.filter(( _: Int) > 0) """, list.filter(_ > 0), Scala.::)

  @Test
  def expectReflectiveCompilationError(): Unit =
    expectReflectiveCompilationError(""" list.map( _ + int) """)

  @Test
  def typedLambdaWithClosure(): Unit =
    eval(""" list.map( (i: Int)=> i + int) """, list.map((i: Int) => i + 1), Scala.::)

  @Test(expected = classOf[MethodInvocationException])
  def nestedTypedLambda(): Unit =
    eval(""" multilist.flatMap( (list: List[Int])=> list.map(_ + 1)) """,
      multilist.flatMap((list: List[Int]) => list.map(_ + 1)),
      Scala.::)

  @Test(expected = classOf[ReflectiveCompilationFailedWithClassNotFound])
  def constructor(): Unit =
    runCode(""" new LibClassWithoutArgs""")

  @Test
  def standardLibConstructor(): Unit =
    eval(""" new Tuple2(1,2) """, new Tuple2(1, 2), "scala.Tuple2")

  @Test
  def primitiveOperation(): Unit =
    eval(""" int + 2""", int + 2, Java.primitives.int)

  @Test
  def anyVals: Unit =
    expectReflectiveCompilationError(""" 2.printMe """)

  @Test
  def standardLibsAnyVals(): Unit =
    eval(""" 1 -> 2 """, (1, 2), "scala.Tuple2")
}

object WithoutProjectSetTest extends BaseIntegrationTestCompanion with RemoteTestCase