/*
 * Copyright (c) 2014 Contributor. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Scala License which accompanies this distribution, and
 * is available at http://www.scala-lang.org/node/146
 */
package org.scalaide.debug.internal.expression.proxies

import com.sun.jdi.ObjectReference

/**
 * Runtime representation of newly loaded class
 * Underlaying is created on first use of it (should we remove this lazines?)
 */
trait BaseNewClassProxy extends JdiProxy {

  /**
   * Full name of underlying class - required for creation of new instance. Usually something like
   * ``__wrapper$1$2ab3b0162985417799c5c79317ee533c.__wrapper$1$2ab3b0162985417799c5c79317ee533c$CustomFucntion$1``
   */
  protected val className: String

  /** arguments for constructor */
  protected def constructorArguments: Seq[Seq[JdiProxy]]

  /** creates new object on first use of underlying object */
  lazy val underlying: ObjectReference = context.newInstance(className, constructorArguments).underlying
}
