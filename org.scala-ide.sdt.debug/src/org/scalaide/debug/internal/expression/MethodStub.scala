/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression

import org.scalaide.debug.internal.expression.context.JdiContext
import org.scalaide.debug.internal.expression.proxies.primitives.NullJdiProxy
import org.scalaide.debug.internal.expression.proxies.primitives.UnitJdiProxy

import Names.Debugger
import Names.Scala

/**
 * Describes method signature for stub creation.
 *
 * @param name name of the method
 * @param thisType type of 'this' from scala point of view (eg. RichInt instead of int)
 * @param returnType fully qualified name of return type
 * @param paramTypes types of function parameters
 * @param implicitParamTypes types of function implicit parameters
 */
case class MethodStub(name: String,
  thisType: String,
  returnType: Option[String],
  paramTypes: Seq[Seq[String]] = Seq.empty) {

  require(returnType != Some("scala.Array"), "Cannot set return type to 'Array' witout generic parameter.")

  /** All types that are used inside this function */
  final def allTypes: Seq[String] = returnType.toSeq ++ paramTypes.flatten
}

/**
 * Source code generator for method stubs.
 */
final class StubCodeGenerator(typesContext: TypesContext) {

  private val paramPrefix = "v"
  private val implicitParamPrefix = "vi"

  /**
   * Generates source code for method.
   *
   * @throws [[org.scalaide.debug.internal.expression.NothingTypeInferred]] if function stub has no returnType set
   */
  def apply(stub: MethodStub): String = {
    val stubType = stub.returnType.getOrElse(throw NothingTypeInferredException)

    val returnType = typesContext.stubType(stubType)
    val parameters = parametersCode(stub.paramTypes)
    val signature = s"def ${stub.name} $parameters: $returnType"
    val implementation = generateCall(stub.name, stub.thisType, stubType, stub.paramTypes)
    s"$signature = $implementation"
  }

  /** Generates code for function parameters list */
  private def generateParametersList(number: Int, paramTypes: Seq[String]) = {
    paramTypes.zipWithIndex.map {
      case (paramType, index) => s"$paramPrefix$number$index: ${typesContext.stubType(paramType)}"
    }.mkString("(", ", ", ")")
  }

  /** generates function's parameters list - all of them */
  private def parametersCode(paramTypes: Seq[Seq[String]]) = paramTypes.zipWithIndex.map {
    case (list, nr) => generateParametersList(nr, list)
  }.mkString

  /** Generates parameters passed to one instance of Seq(v01, v02) for passing to context.invokeMethod */
  private def generateParams(prefix: String, params: Seq[String]): String =
    params.zipWithIndex.map {
      case (_, nr) => s"$prefix$nr"
    }.mkString(", ")

  /** Generate parameters part of calling context invoke method. */
  private def callParameters(paramTypes: Seq[Seq[String]]) = paramTypes.zipWithIndex.map {
    case (list, nr) =>
      if (list.isEmpty) "Seq.empty" else s"Seq(${generateParams(s"$paramPrefix$nr", list)})"
  }.mkString("Seq(", ", ", ")")

  /** Code representing implicits passed to context's invoke method  */
  private def callImplicits(params: Seq[String]) =
    if (params.isEmpty) "Seq.empty" else s"Seq(${generateParams(implicitParamPrefix, params)})"

  /** Check if given call should be wrapped in stub case class */
  private def shouldCallNotBeStubbed(returnType: String): Boolean =
    returnType == Debugger.proxyName ||
      returnType == Debugger.proxyFullName

  /** Generates function body - basicly context.invokeMethod call */
  private def generateCall(stubName: String,
    stubRealThisType: String,
    stubType: String,
    paramTypes: Seq[Seq[String]]) = {

    val parameters = callParameters(paramTypes)

    def creationCode(returnType: String = "JdiProxy") = {
      import Debugger._
      s"""$contextParamName.$invokeMethodName[$returnType]($proxyContextName, Some("$stubRealThisType"), "$stubName", $parameters)"""
    }

    stubType match {
      case Scala.nullType =>
        val returnType = classOf[NullJdiProxy].getSimpleName
        creationCode(returnType)
      case Scala.unitType =>
        val returnType = classOf[UnitJdiProxy].getSimpleName
        creationCode(returnType)
      case tpe if shouldCallNotBeStubbed(tpe) =>
        // JdiProxy or solid stub types should not be wrapped
        creationCode()
      case _ =>
        s"${typesContext.stubType(stubType)}(${creationCode()})"
    }
  }
}