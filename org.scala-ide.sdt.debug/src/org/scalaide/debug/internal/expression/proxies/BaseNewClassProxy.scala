/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.proxies

import org.scalaide.debug.internal.expression.context.JdiContext
import com.sun.jdi.ObjectReference

/**
 * Runtime representation of newly loaded class
 * Underlaying is created on first use of it (should we remove this lazines?)
 */
trait BaseNewClassProxy extends JdiProxy {

  /**
   * to avoid protected[expression] - when we implementing proxy we are not inside expression package
   * See [[org.scalaide.debug.internal.expression.proxies.JdiContext]] for more information.
   */
  protected def newClassContext: JdiContext

  /**
   * Context in which debug is running.
   * See [[org.scalaide.debug.internal.expression.proxies.JdiContext]] for more information.
   */
  override protected[expression] def proxyContext: JdiContext = newClassContext

  /**
   * Full name of underlying class - required for creation of new instance. Usually something like
   * ``__wrapper$1$2ab3b0162985417799c5c79317ee533c.__wrapper$1$2ab3b0162985417799c5c79317ee533c$CustomFucntion$1``
   */
  protected val className: String

  /** arguments for constructor */
  protected def constructorArguments: Seq[Seq[JdiProxy]]

  /** creates new object on first use of underlying object */
  lazy val __underlying: ObjectReference = proxyContext.newInstance[JdiProxy](className, constructorArguments).__underlying
}
