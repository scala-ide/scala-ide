/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.sources

import org.scalaide.core.compiler.IScalaPresentationCompiler
import scala.reflect.internal.util.SourceFile
import org.scalaide.logging.HasLogger

/** provides access to types obtained from source code (via Presentation Compiler) for current frame */
object GenericTypes extends SPCIntegration with HasLogger {

  private def reportProblemWithMissingGenerics(reason: String): Option[Map[String, String]] = {
    logger.info(s"No generic due to: $reason")
    None
  }

  private val SPC_TIMEOUT_IN_MILLIS = 1000

  private def getGenerics(spc: IScalaPresentationCompiler, sourceFile: SourceFile, line: Int): Option[Map[String, String]] = {
    def memberEntry(member: spc.Member): (String, String) = {
      val name = member.sym.nameString
      val typeName = member.tpe.toLongString

      name -> typeName
    }

    def mapNamesToTypes(content: List[spc.Member]) = {
      val values = content.filter {
        member =>
          val typeOk = !member.tpe.isError
          val symbolOk = Set("var", "val", "").contains(member.sym.keyString)
          typeOk && symbolOk
      }
      values.map(memberEntry).toMap
    }

    spc.askScopeCompletion(sourceFile.position(sourceFile.lineToOffset(line))).get(SPC_TIMEOUT_IN_MILLIS) match {
      case None =>
        logger.warn(s"No source file or presentation compiler for: ${sourceFile.path} in line $line")
        None
      case Some(Right(error)) =>
        println(error)
        logger.error(s"During generics for: ${sourceFile.path} in line $line", error)
        None
      case Some(Left(content)) =>
        spc.asyncExec(mapNamesToTypes(content)).get(SPC_TIMEOUT_IN_MILLIS) match {
          case None =>
            logger.warn(s"Cannot compute type information for: ${sourceFile.path} in line $line")
            None
          case Some(Right(error)) =>
            logger.error(s"During generics for: ${sourceFile.path} in line $line", error)
            None
          case Some(Left(content)) =>
            Some(content)
        }
    }
  }
  /**
   * Finds generic from source code for current (from debugger point of view) file and line
   * @return Returns Some(Map(valueName, valueType)) if sourcess are acessible and SPC can read types
   *         None otherwise
   */
  def genericTypeForValues(): Option[Map[String, String]] = {
    forCurrentStackFrame(getGenerics, reportProblemWithMissingGenerics)
  }
}
