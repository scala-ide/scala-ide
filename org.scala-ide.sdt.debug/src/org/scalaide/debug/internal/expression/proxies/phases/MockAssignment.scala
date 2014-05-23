/*
 * Copyright (c) 2014 Contributor. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Scala License which accompanies this distribution, and
 * is available at http://www.scala-lang.org/node/146
 */
package org.scalaide.debug.internal.expression.proxies.phases

import scala.reflect.runtime.universe
import scala.tools.reflect.ToolBox
import org.scalaide.debug.internal.expression.AstTransformer
import org.scalaide.debug.internal.expression.TransformationPhase
import org.scalaide.debug.internal.expression.DebuggerSpecific
import org.scalaide.debug.internal.expression.TypesContext

case class MockAssignment(toolbox: ToolBox[universe.type], typesContext: TypesContext) extends AstTransformer {

  import toolbox.u._

  override final def transformSingleTree(tree: Tree, transformFurther: Tree => Tree): Tree = tree match {
    case Assign(Ident(termName), value) if typesContext.unboundVariables.contains(termName.toString) =>
      Apply(
        Select(Ident(newTermName(DebuggerSpecific.thisValName)), newTermName(termName + "_$eq")),
        List(value))
    case other => transformFurther(other)
  }
}