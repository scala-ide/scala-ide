/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.sources

import org.scalaide.core.compiler.IScalaPresentationCompiler
import scala.reflect.internal.util.SourceFile


object GenericTypes extends PCIntegration {

  private def noGenrerics(reason: String): Option[Map[String, String]] = None //TODO logging

  private def getGenerics(pc: IScalaPresentationCompiler, sourceFile: SourceFile, line: Int): Option[Map[String, String]] = {
    def memberEntry(member: pc.Member): (String, String) = {
      val name = member.sym.nameString
      val typeName = member.tpe.toLongString

      name -> typeName
    }

    pc.askScopeCompletion(sourceFile.position(sourceFile.lineToOffset(line))).get(1000)
    match {
      case None =>
        None //TODO logging
      case Some(Right(error)) =>
        println(error)
        None //TODO logging
      case Some(Left(content)) =>
        pc.asyncExec {
          val values = content.filter {
            member =>
              val typeOk = !member.tpe.isError
              val symbolOk = Set("var", "val", "").contains(member.sym.keyString)
              typeOk && symbolOk
          }
          values.map(memberEntry).toMap
        }.get(1000) match {
          case None =>
            None
          case Some(Right(error)) =>
            println(error)
            None //TODO logging
          case Some(Left(content)) =>
            Some(content)
        }
    }
  }

  /**
   * Finds generic from source code for current (from debugger point of view) file and line
   */
  def genericTypeForValues: Option[Map[String, String]] = {
    forCurrentStackFrame(getGenerics, noGenrerics)
  }
}
