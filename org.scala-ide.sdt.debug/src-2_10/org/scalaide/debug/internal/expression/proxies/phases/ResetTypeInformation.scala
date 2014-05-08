/*
 * Copyright (c) 2014 Contributor. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Scala License which accompanies this distribution, and
 * is available at http://www.scala-lang.org/node/146
 */
package org.scalaide.debug.internal.expression.proxies.phases

import scala.reflect.runtime.universe
import org.scalaide.debug.internal.expression.TransformationPhase
import scala.tools.reflect.ToolBox
import scala.reflect.api.{JavaUniverse => universe}

/**
 * Used for 2.11-2.10 source compatibility.
 */
case class ResetTypeInformation(toolbox: ToolBox[universe.type]) extends TransformationPhase {
  override def transform(tree: universe.Tree): universe.Tree = {
    toolbox.resetAllAttrs(tree)
  }
}