/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression

import scala.reflect.runtime.universe
import scala.concurrent.duration._

/**
 * Wrapper on variable name, not to use raw Scala AST types.
 */
class Variable(val name: universe.TermName)

object Variable {
  def apply(name: universe.TermName): Variable = new Variable(name)
}

/**
 * Unbound variable used in expression.
 *
 * @param isLocal true if variable is defined inside current frame (method).
 */
case class UnboundVariable(override val name: universe.TermName, isLocal: Boolean) extends Variable(name)

/**
 * Represents new class to be loaded on remote .
 *
 * @param className name of class
 * @param code array of bytes with class contents
 * @param constructorArgsTypes
 */
case class ClassData(className: String, code: Array[Byte], constructorArgsTypes: Seq[String])

case class PhaseCode(name: String, code: universe.Tree)

case class PhaseTime(name: String, time: Duration)

/**
 * Contains current tree and metadata connected with transformation.
 *
 * @param tree tree after last transformation
 * @param history trees after phases
 * @param unboundVariables set of unbound variables found during transformation
 * @param classesToLoad classes to be loaded on debugged jvm
 * @param times execution times (in microseconds)
 */
case class TransformationPhaseData(
    tree: universe.Tree = universe.EmptyTree,
    history: Vector[PhaseCode] = Vector.empty,
    unboundVariables: Set[UnboundVariable] = Set.empty,
    classesToLoad: Seq[ClassData] = Seq.empty,
    times: Vector[PhaseTime] = Vector.empty) {

  final def after(phaseName: String, newTree: universe.Tree): TransformationPhaseData =
    this.copy(tree = newTree, history = this.history :+ PhaseCode(phaseName, newTree))

  final def withTime(phaseName: String, timeInMicros: Long): TransformationPhaseData =
    this.copy(times = this.times :+ PhaseTime(phaseName, timeInMicros.micros))

  final def withUnboundVariables(unboundVariables: Set[UnboundVariable]): TransformationPhaseData =
    this.copy(unboundVariables = unboundVariables)

  final def withClasses(newClasses: Seq[ClassData]): TransformationPhaseData =
    this.copy(classesToLoad = this.classesToLoad ++ newClasses)
}
