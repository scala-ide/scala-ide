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

  /** Maps Scala function types to their proxies. */
  val functionToProxyMap = Map(
    ScalaFunctions.PartialFunction -> classOf[PartialFunctionProxy],
    ScalaFunctions.Function0 -> classOf[Function0JdiProxy],
    ScalaFunctions.Function1 -> classOf[Function1JdiProxy],
    ScalaFunctions.Function2 -> classOf[Function2JdiProxy],
    ScalaFunctions.Function3 -> classOf[Function3JdiProxy],
    ScalaFunctions.Function4 -> classOf[Function4JdiProxy],
    ScalaFunctions.Function5 -> classOf[Function5JdiProxy],
    ScalaFunctions.Function6 -> classOf[Function6JdiProxy],
    ScalaFunctions.Function7 -> classOf[Function7JdiProxy],
    ScalaFunctions.Function8 -> classOf[Function8JdiProxy],
    ScalaFunctions.Function9 -> classOf[Function9JdiProxy],
    ScalaFunctions.Function10 -> classOf[Function10JdiProxy],
    ScalaFunctions.Function11 -> classOf[Function11JdiProxy],
    ScalaFunctions.Function12 -> classOf[Function12JdiProxy],
    ScalaFunctions.Function13 -> classOf[Function13JdiProxy],
    ScalaFunctions.Function14 -> classOf[Function14JdiProxy],
    ScalaFunctions.Function15 -> classOf[Function15JdiProxy],
    ScalaFunctions.Function16 -> classOf[Function16JdiProxy],
    ScalaFunctions.Function17 -> classOf[Function17JdiProxy],
    ScalaFunctions.Function18 -> classOf[Function18JdiProxy],
    ScalaFunctions.Function19 -> classOf[Function19JdiProxy],
    ScalaFunctions.Function20 -> classOf[Function20JdiProxy],
    ScalaFunctions.Function21 -> classOf[Function21JdiProxy],
    ScalaFunctions.Function22 -> classOf[Function22JdiProxy]).mapValues(_.getSimpleName)

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