/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.proxies.phases

import scala.reflect.runtime.universe
import scala.tools.reflect.ToolBox

import org.scalaide.debug.internal.expression.TransformationPhase
import org.scalaide.logging.HasLogger

case class TypeCheck(toolbox: ToolBox[universe.type])
  extends TransformationPhase
  with HasLogger {

  // TODO - O-6127 - workaround for compiler error - will be resolved with Typesafe
  private val messageExpr =
    ".*loaded from class (.*) in .* with name (.*) and classloader.*".r

  // TODO - O-6127 - workaround for compiler error - will be resolved with Typesafe
  private object NameProblem {
    def unapply(e: Throwable): Option[String] = e match {
      case assertionError: AssertionError =>
        messageExpr.findFirstIn(assertionError.getMessage) match {
          case Some(messageExpr(className, _)) => Some(className)
          case _ => None
        }
      case _ => None
    }
  }

  private object UnknowProblem{
    def unapply(e: Throwable): Option[Boolean] ={
      e match{
        case assertionError: AssertionError =>
          Some(true)
        case _ =>
          None
      }
    }
  }

  override def transform(tree: universe.Tree): universe.Tree = {
    def doTypeCheck = toolbox.typecheck(tree)
    try {
      doTypeCheck
    } catch {
      case problem @ NameProblem(name) =>
        try {
          // TODO - O-6127 - workaround for compiler error - will be resolved with Typesafe
          toolbox.compile(toolbox.parse(s"val a: $name = ???"))
          doTypeCheck
        } catch {
          case newProblem @ NameProblem(`name`) =>
            logger.error("Same exception was thrown when applying workaround", newProblem)
            throw problem
          case UnknowProblem(_) =>
            doTypeCheck
          case e: Throwable =>
            logger.error("Exception was thrown when applying workaround", e)
            throw e
        }
    }
  }
}