/*
 * Copyright (c) 2014 Contributor. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Scala License which accompanies this distribution, and
 * is available at http://www.scala-lang.org/node/146
 */
package org.scalaide.debug.internal.expression.proxies.phases

import scala.reflect.runtime.universe

import org.scalaide.debug.internal.expression.TransformationPhase
import scala.tools.reflect.ToolBox

case class TypeCheck(toolbox: ToolBox[universe.type]) extends TransformationPhase {
  override def transform(tree: universe.Tree): universe.Tree = {
   toolbox.typeCheck(tree)
  }
}