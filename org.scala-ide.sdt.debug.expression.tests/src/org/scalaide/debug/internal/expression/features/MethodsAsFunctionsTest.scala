/*
 * Copyright (c) 2015 Contributor. All rights reserved.
*/
package org.scalaide.debug.internal.expression.features

import org.junit.Test
import org.scalaide.debug.internal.expression.BaseIntegrationTest
import org.scalaide.debug.internal.expression.Names.Java
import org.scalaide.debug.internal.expression.Names.Scala

trait MethodsAsFunctionsTest { self: BaseIntegrationTest =>

  @Test
  def methodsFromObject(): Unit = {
    eval("List(1, 2).foldLeft(ObjectMethods.zero)(ObjectMethods.sum)", 3, Java.boxed.Integer)
    eval("List(-1, 1).filter(_ > ObjectMethods.zero)", List(1), Scala.Array(Scala.primitives.Int))
  }

  @Test
  def methodAsMapParam(): Unit = eval("nat.map(inc)", Array(3, 4, 5), Scala.Array(Scala.primitives.Int))

  @Test
  def methodCall(): Unit = eval("zero", 0, Java.boxed.Integer)

  @Test
  def methodAsFilterParam(): Unit = eval("nat.filter(_ > inc(inc(zero)))", Array(3, 4), Scala.Array(Scala.primitives.Int))

  @Test
  def methodsAsFoldParam(): Unit = eval("nat.foldLeft(zero)(sum)", 9, Java.boxed.Integer)

  @Test
  def methodAsGetOrElseParam(): Unit = eval("None.getOrElse(zero)", 0, Java.boxed.Integer)

  @Test
  def andThenMethods(): Unit = eval("(inc _ andThen inc _)(zero)", 2, Java.boxed.Integer)

  @Test
  def composeMethods(): Unit = eval("(inc _ compose dec)(zero)", 0, Java.boxed.Integer)
}
