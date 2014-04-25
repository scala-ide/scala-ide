/*
 * Copyright (c) 2014 Contributor. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Scala License which accompanies this distribution, and
 * is available at http://www.scala-lang.org/node/146
 */
package org.scalaide.debug.internal.expression

import org.scalaide.debug.internal.expression.context.JdiContext
import org.scalaide.debug.internal.expression.proxies.UnitJdiProxy

/**
 * Stubs function call.
 *
 * @param name name of the function
 * @param returnType fully qualified name of return type
 * @argumentTypes
 * @implicitArgumentTypes
 */
case class FunctionStub(name: String,
  returnType: Option[String],
  argumentTypes: Seq[Seq[String]] = Seq.empty,
  implicitArgumentTypes: Seq[String] = Seq.empty) {

  /** All types that are used inside this function */
  final def allTypes: Seq[String] = returnType.toSeq ++ implicitArgumentTypes ++ argumentTypes.flatten
}

/**
 * Source code generator for function stubs.
 */
final class StubCodeGenerator(typesContext: TypesContext) {

  private val argumentPrefix = "v"
  private val implicitArgumentPrefix = "vi"

  /**
   * Generates source code for method.
   *
   * @throws [[org.scalaide.debug.internal.expression.NothingTypeInferred]] if function stub has no returnType set
   */
  def apply(stub: FunctionStub): String = {
    val stubType = stub.returnType.getOrElse(throw new NothingTypeInferred)

    val returnType = typesContext.typeNameFor(stubType)
    val arguments = argumentsCode(stub.argumentTypes)
    val implicits = generateImplicitParameterList(stub)
    val signature = s"def ${stub.name} $arguments $implicits: $returnType"
    val implementation = generateCall(stub.name, stubType, stub.argumentTypes, stub.implicitArgumentTypes)
    s"$signature = $implementation"
  }

  /** Generates code for function parameter list */
  private def generateParamList(number: Int, argTypes: Seq[String]) = {
    argTypes.zipWithIndex.map {
      case (paramType, index) => s"$argumentPrefix$number$index: ${typesContext.typeNameFor(paramType)}"
    }.mkString("(", ", ", ")")
  }

  /** Generates code for function implicit parameter list */
  private def generateImplicitParameterList(stub: FunctionStub) = {
    if (stub.implicitArgumentTypes.isEmpty) ""
    else
      stub.implicitArgumentTypes.zipWithIndex.map {
        case (paramType, index) => s"$implicitArgumentPrefix$index: ${typesContext.typeNameFor(paramType)}"
      }.mkString("(implicit ", ", ", ")")
  }

  /** generates function's argument list - all of them */
  private def argumentsCode(argumentTypes: Seq[Seq[String]]) = argumentTypes.zipWithIndex.map {
    case (list, nr) => generateParamList(nr, list)
  }.mkString

  /** Generates params pass to one instance of Seq(v01, v02) for passing to context.invokeMethod */
  private def generateParams(prefix: String, args: Seq[String]): String =
    args.zipWithIndex.map {
      case (_, nr) => s"$prefix$nr"
    }.mkString(", ")

  /** Generate arguments part of calling context invoke method. Pass all arguments lists */
  private def callArguments(argumentTypes: Seq[Seq[String]]) = argumentTypes.zipWithIndex.map {
    case (list, nr) =>
      if (list.isEmpty) "Seq.empty" else s"Seq(${generateParams(s"$argumentPrefix$nr", list)})"
  }.mkString("Seq(", ", ", ")")

  /** Code representing implicits passed to context's invoke method  */
  private def callImplicits(args: Seq[String]) =
    if (args.isEmpty) "Seq.empty" else s"Seq(${generateParams(implicitArgumentPrefix, args)})"

  /** Check if given call should be wrapped in stub case class */
  private def shouldCallNotBeStubbed(returnType: String): Boolean =
    returnType == DebuggerSpecific.proxyName ||
      returnType == DebuggerSpecific.proxyFullName

  /** Generates function body - basicly context.invokeMethod call */
  private def generateCall(stubName: String, stubType: String, argumentTypes: Seq[Seq[String]], implicitArgumentTypes: Seq[String]) = {
    val arguments = callArguments(argumentTypes)
    val implicits = callImplicits(implicitArgumentTypes)

    def creationCode(returnType: String = "JdiProxy") = {
      import DebuggerSpecific._
      s"""$contextParamName.$invokeMethodName[$returnType](proxy, "$stubName", $arguments, $implicits)"""
    }

    if (stubType == ScalaOther.unitType) {
      val returnType = classOf[UnitJdiProxy].getSimpleName
      creationCode(returnType)
    } else if (shouldCallNotBeStubbed(stubType)) {
      // JdiProxy or solid stub types should not be wrapped
      creationCode()
    } else {
      s"${typesContext.typeNameFor(stubType)}(${creationCode()})"
    }
  }
}