/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.proxies.phases

import scala.reflect.runtime.universe
import scala.tools.reflect.ToolBox

import org.scalaide.debug.internal.expression.TransformationPhase
import org.scalaide.logging.HasLogger

/**
 * @param newTypes code for stubs to generate
 */
class GenerateStubs(val toolbox: ToolBox[universe.type], newTypes: => String)
  extends TransformationPhase
  with HasLogger {

  import toolbox.u._

  /**
   * If code contains single ClassDef it's returned in Seq,
   * if it's block of ClassDefs they are returned in Seq.
   */
  private def extractClassDefs(code: Tree): Seq[Tree] = code match {
    case classDef: ClassDef => Seq(classDef)
    case block: Block => block.children
    case empty @ universe.EmptyTree => Nil
    case any => throw new IllegalArgumentException(s"Unsupported tree: $any")
  }

  override def transform(tree: Tree): Tree = {
    val typesToCreate = newTypes
    if (!typesToCreate.isEmpty) {
      val newTypesCode = try {
        toolbox.parse(typesToCreate)
      } catch {
        case t: Throwable =>
          logger.error("Could not parse:\n" + typesToCreate)
          throw t
      }
      val newCodeLines = extractClassDefs(newTypesCode)

      Block(newCodeLines.toList, tree)
    } else tree
  }
}