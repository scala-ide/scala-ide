/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.sources

import org.scalaide.core.compiler.IScalaPresentationCompiler
import scala.reflect.internal.util.SourceFile
import org.scalaide.logging.HasLogger

/** provides access to types obtained from source code (via Presentation Compiler) for current frame */
object GenericTypes extends SPCIntegration with HasLogger {

  sealed trait GenericEntryType

  case class LocalMethod(parametersListsCount: Int, from: Int, to: Int) extends GenericEntryType

  object Field extends GenericEntryType

  case class GenericEntry(entryType: GenericEntryType, name: String, genericType: String, isLocalImplicit: Boolean)

  /**
   * Finds generics from source code for current (from debugger point of view) file and line
   * @return Returns Some(Map(valueName, valueType)) if sources are accessible and SPC can read types
   *         None otherwise
   */
  class GenericProvider {
    // Tries to obtain generic arguments twice on failure (SPC is fragile)
    private lazy val entries: Map[String, GenericEntry] = genericTypeForValues().orElse(genericTypeForValues()).getOrElse(Map())

    /** Try to find type for field */
    def typeForField(name: String): Option[String] = entries.get(name).filter(_.entryType == Field).map(_.genericType)

    /** try to find nested method type information */
    def typeForNestedMethod(name: String): Option[GenericEntry] =
      entries.get(name).filter(_.entryType.isInstanceOf[LocalMethod])

    /** gets all implicit local fields and methods */
    def implicits: Seq[GenericEntry] =
      entries.values.filter(_.isLocalImplicit).toSeq
  }

  private def genericTypeForValues(): Option[Map[String, GenericEntry]] =
    forCurrentStackFrame(getGenerics, reportProblemWithMissingGenerics)

  private def reportProblemWithMissingGenerics(reason: String) = {
    logger.info(s"No generic due to: $reason")
    None
  }

  private val SPC_TIMEOUT_IN_MILLIS = 1000

  private def memberEntry(spc: IScalaPresentationCompiler)(member: spc.Member): Option[(String, GenericEntry)] = {
    if (member.tpe.isError) None
    else {
      val name = member.sym.nameString
      import spc._

      member.sym.keyString match {
        case "def" if member.sym.isLocalToBlock =>

          var argumentListCount = 0
          def methodSig(t: Type): String = t match {
            case mt @ MethodType(params, ret) =>
              argumentListCount += 1
              val paramsTypes = params.map(_.tpe.toLongString).mkString(", ")
              s"($paramsTypes) => ${methodSig(ret)}"
            case a @ NullaryMethodType(ret) =>
              s"() => ${methodSig(ret)}"
            case other => other.toLongString
          }
          val signature = methodSig(member.tpe)

          val pos = member.sym.pos
          val startLine = pos.line

          val positionFixingForOffsetComputation = 1
          val fixingLineNumberAfterOffsetToLineTransformation = 1

          val endLine = pos.source.offsetToLine(pos.end + positionFixingForOffsetComputation) +
            fixingLineNumberAfterOffsetToLineTransformation

          Some(name -> GenericEntry(
            LocalMethod(argumentListCount, startLine, endLine),
            name,
            signature,
            member.sym.isImplicit))
        case "var" | "val" =>

          Some(name -> GenericEntry(
            Field,
            name,
            member.tpe.toLongString,
            member.sym.isImplicit && member.sym.isLocalToBlock))
        case other =>
          None
      }
    }
  }

  private def getGenerics(spc: IScalaPresentationCompiler, sourceFile: SourceFile, line: Int): Option[Map[String, GenericEntry]] = {
    spc.askScopeCompletion(sourceFile.position(sourceFile.lineToOffset(line))).get(SPC_TIMEOUT_IN_MILLIS) match {
      case None =>
        logger.warn(s"No source file or presentation compiler for: ${sourceFile.path} in line $line")
        None
      case Some(Right(error)) =>
        logger.error(s"During generics for: ${sourceFile.path} in line $line", error)
        None
      case Some(Left(content)) =>
        spc.asyncExec(content.flatMap(memberEntry(spc)).toMap).get(SPC_TIMEOUT_IN_MILLIS) match {
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
}
