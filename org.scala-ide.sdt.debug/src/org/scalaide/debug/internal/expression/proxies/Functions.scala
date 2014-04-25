/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.proxies

import org.scalaide.debug.internal.expression.Names.Scala

/**
 * base class for all functions
 * subclass are created for typesafety in proxies stubs
 */
trait AbstractFunctionJdiProxy extends BaseNewClassProxy {
  /** arguments for constructor */
  protected def constructorArguments: Seq[Seq[JdiProxy]] = Seq(Seq())
}

private[expression] object FunctionJdiProxy {

  /** Maps Scala function types to their proxy type names. */
  def functionToProxy(functionType: String): Option[String] =
    functionToProxyMap.get(functionType)

  def functionProxy(arity: Int): String = {
    require(arity <= 22 && arity >= 0, "Scala support functions from 0 to 22")
    functionsArray(arity + 1)._1 // +1 cos we have Partial Function on start of seq
  }

  private val functionsArray = Array(
    Scala.functions.PartialFunction -> classOf[PartialFunctionProxy],
    Scala.functions.Function0 -> classOf[Function0JdiProxy],
    Scala.functions.Function1 -> classOf[Function1JdiProxy],
    Scala.functions.Function2 -> classOf[Function2JdiProxy],
    Scala.functions.Function3 -> classOf[Function3JdiProxy],
    Scala.functions.Function4 -> classOf[Function4JdiProxy],
    Scala.functions.Function5 -> classOf[Function5JdiProxy],
    Scala.functions.Function6 -> classOf[Function6JdiProxy],
    Scala.functions.Function7 -> classOf[Function7JdiProxy],
    Scala.functions.Function8 -> classOf[Function8JdiProxy],
    Scala.functions.Function9 -> classOf[Function9JdiProxy],
    Scala.functions.Function10 -> classOf[Function10JdiProxy],
    Scala.functions.Function11 -> classOf[Function11JdiProxy],
    Scala.functions.Function12 -> classOf[Function12JdiProxy],
    Scala.functions.Function13 -> classOf[Function13JdiProxy],
    Scala.functions.Function14 -> classOf[Function14JdiProxy],
    Scala.functions.Function15 -> classOf[Function15JdiProxy],
    Scala.functions.Function16 -> classOf[Function16JdiProxy],
    Scala.functions.Function17 -> classOf[Function17JdiProxy],
    Scala.functions.Function18 -> classOf[Function18JdiProxy],
    Scala.functions.Function19 -> classOf[Function19JdiProxy],
    Scala.functions.Function20 -> classOf[Function20JdiProxy],
    Scala.functions.Function21 -> classOf[Function21JdiProxy],
    Scala.functions.Function22 -> classOf[Function22JdiProxy])
    .map {
      case (name, clazz) => name -> clazz.getSimpleName
    }

  private val functionToProxyMap = functionsArray.toMap

  /** Names of all scala Functions */
  val functionNames = functionToProxyMap.keys

  /** Names of all function proxies */
  val functionProxyNames = functionToProxyMap.values
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