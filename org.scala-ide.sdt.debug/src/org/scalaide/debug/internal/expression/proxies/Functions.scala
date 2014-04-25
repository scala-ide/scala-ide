/*
 * Copyright (c) 2014 Contributor. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Scala License which accompanies this distribution, and
 * is available at http://www.scala-lang.org/node/146
 */
package org.scalaide.debug.internal.expression.proxies

/**
 * base class for all functions
 * subclass are created for typesafety in proxies stubs
 */
trait AbstractFunctionJdiProxy extends BaseNewClassProxy {
  /** arguments for constructor */
  final def constructorArguments: Seq[Seq[JdiProxy]] = Seq(Seq())
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