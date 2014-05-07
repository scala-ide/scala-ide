/*
 * Copyright (c) 2014 Contributor. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Scala License which accompanies this distribution, and
 * is available at http://www.scala-lang.org/node/146
 */
package org.scalaide.debug.internal.expression.proxies.phases

import scala.reflect.runtime.universe

import org.scalaide.debug.internal.expression.TransformationPhase
import scala.tools.reflect.ToolBox

case class ResetTypeInformation(toolbox: ToolBox[universe.type]) extends TransformationPhase {
  override def transform(tree: universe.Tree): universe.Tree = {
    // FIXME - localReset (untypecheck) should be used instead of allReset, because latter is removed in Scala 2.11
    // unfortunately, with localReset compilation fails with strange assertion errors
    // toolbox.untypecheck(tree)
    toolbox.resetAllAttrs(tree)
  }
}