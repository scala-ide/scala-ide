/*
 * Copyright (c) 2014 Contributor. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Scala License which accompanies this distribution, and
 * is available at http://www.scala-lang.org/node/146
 */
package org.scalaide.debug.internal.expression.proxies

import org.scalaide.debug.internal.expression.ScalaFunctions

/**
 * base class for all functions
 * subclass are created for typesafety in proxies stubs
 */
trait AbstractFunctionJdiProxy extends BaseNewClassProxy {
  /** arguments for constructor */
  final def constructorArguments: Seq[Seq[JdiProxy]] = Seq(Seq())
}

private[expression] object FunctionJdiProxy {

  /** Maps Scala function types to it's proxies. */
  val functionToProxyMap = Map(
    ScalaFunctions.PartialFunction -> classOf[PartialFunctionProxy].getSimpleName,
    ScalaFunctions.Function0 -> classOf[Function0JdiProxy].getSimpleName,
    ScalaFunctions.Function1 -> classOf[Function1JdiProxy].getSimpleName,
    ScalaFunctions.Function2 -> classOf[Function2JdiProxy].getSimpleName,
    ScalaFunctions.Function3 -> classOf[Function3JdiProxy].getSimpleName,
    ScalaFunctions.Function4 -> classOf[Function4JdiProxy].getSimpleName,
    ScalaFunctions.Function5 -> classOf[Function5JdiProxy].getSimpleName,
    ScalaFunctions.Function6 -> classOf[Function6JdiProxy].getSimpleName,
    ScalaFunctions.Function7 -> classOf[Function7JdiProxy].getSimpleName,
    ScalaFunctions.Function8 -> classOf[Function8JdiProxy].getSimpleName,
    ScalaFunctions.Function9 -> classOf[Function9JdiProxy].getSimpleName,
    ScalaFunctions.Function10 -> classOf[Function10JdiProxy].getSimpleName,
    ScalaFunctions.Function11 -> classOf[Function11JdiProxy].getSimpleName,
    ScalaFunctions.Function12 -> classOf[Function12JdiProxy].getSimpleName,
    ScalaFunctions.Function13 -> classOf[Function13JdiProxy].getSimpleName,
    ScalaFunctions.Function14 -> classOf[Function14JdiProxy].getSimpleName,
    ScalaFunctions.Function15 -> classOf[Function15JdiProxy].getSimpleName,
    ScalaFunctions.Function16 -> classOf[Function16JdiProxy].getSimpleName,
    ScalaFunctions.Function17 -> classOf[Function17JdiProxy].getSimpleName,
    ScalaFunctions.Function18 -> classOf[Function18JdiProxy].getSimpleName,
    ScalaFunctions.Function19 -> classOf[Function19JdiProxy].getSimpleName,
    ScalaFunctions.Function20 -> classOf[Function20JdiProxy].getSimpleName,
    ScalaFunctions.Function21 -> classOf[Function21JdiProxy].getSimpleName,
    ScalaFunctions.Function22 -> classOf[Function22JdiProxy].getSimpleName)

}

trait PartialFunctionProxy extends AbstractFunctionJdiProxy

trait Function0JdiProxy extends AbstractFunctionJdiProxy

trait Function1JdiProxy extends AbstractFunctionJdiProxy

trait Function2JdiProxy extends AbstractFunctionJdiProxy

trait Function3JdiProxy extends AbstractFunctionJdiProxy

trait Function4JdiProxy extends AbstractFunctionJdiProxy

trait Function5JdiProxy extends AbstractFunctionJdiProxy

trait Function6JdiProxy extends AbstractFunctionJdiProxy

trait Function7JdiProxy extends AbstractFunctionJdiProxy

trait Function8JdiProxy extends AbstractFunctionJdiProxy

trait Function9JdiProxy extends AbstractFunctionJdiProxy

trait Function10JdiProxy extends AbstractFunctionJdiProxy

trait Function11JdiProxy extends AbstractFunctionJdiProxy

trait Function12JdiProxy extends AbstractFunctionJdiProxy

trait Function13JdiProxy extends AbstractFunctionJdiProxy

trait Function14JdiProxy extends AbstractFunctionJdiProxy

trait Function15JdiProxy extends AbstractFunctionJdiProxy

trait Function16JdiProxy extends AbstractFunctionJdiProxy

trait Function17JdiProxy extends AbstractFunctionJdiProxy

trait Function18JdiProxy extends AbstractFunctionJdiProxy

trait Function19JdiProxy extends AbstractFunctionJdiProxy

trait Function20JdiProxy extends AbstractFunctionJdiProxy

trait Function21JdiProxy extends AbstractFunctionJdiProxy

trait Function22JdiProxy extends AbstractFunctionJdiProxy