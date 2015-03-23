/*
 * Copyright (c) 2015 Contributor. All rights reserved.
*/
package org.scalaide.debug.internal.expression.features

import org.junit.Ignore
import org.junit.Test
import org.scalaide.debug.internal.expression.BaseIntegrationTest
import org.scalaide.debug.internal.expression.Names.Java

trait MethodsAsFunctionsTest { self: BaseIntegrationTest =>

  @Ignore("TODO - O-8464 Add support for using methods from objects as first class functions")
  @Test
  def methodsFromObject(): Unit = {
    eval("List(1, 2).foldLeft(ObjectMethods.zero)(ObjectMethods.sum)", "3", Java.boxed.Integer)
    eval("List(-1, 1).filter(_ > ObjectMethods.zero)", "Array(1)", "scala.Array[scala.Int]")
  }

  @Ignore("Works in IDE, in tests there are some problems with canBuildFrom")
  @Test
  def methodAsMapParam(): Unit = eval("nat.map(inc)", "Array(3, 4, 5)", "scala.Array[scala.Int]")

  @Test
  def methodCall(): Unit = eval("zero", "0", Java.boxed.Integer)

  @Test
  def methodAsFilterParam(): Unit = eval("nat.filter(_ > inc(inc(zero)))", "Array(3, 4)", "scala.Array[scala.Int]")

  @Test
  def methodsAsFoldParam(): Unit = eval("nat.foldLeft(zero)(sum)", "9", Java.boxed.Integer)

  @Test
  def methodAsGetOrElseParam(): Unit = eval("None.getOrElse(zero)", "0", Java.boxed.Integer)

  @Test
  def andThenMethods(): Unit = eval("(inc _ andThen inc _)(zero)", "2", Java.boxed.Integer)

  @Test
  def composeMethods(): Unit = eval("(inc _ compose dec)(zero)", "0", Java.boxed.Integer)
}
