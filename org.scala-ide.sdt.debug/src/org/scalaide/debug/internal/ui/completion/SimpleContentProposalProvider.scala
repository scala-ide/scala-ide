/*
 * Copyright (c) 2014 Contributor. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Scala License which accompanies this distribution, and
 * is available at http://www.scala-lang.org/node/146
 */
package org.scalaide.debug.internal.ui.completion

import scala.collection.JavaConversions.collectionAsScalaIterable
import scala.reflect.NameTransformer
import scala.reflect.runtime.universe
import scala.reflect.runtime.{ universe => ru }

import org.eclipse.jface.fieldassist.ContentProposal
import org.eclipse.jface.fieldassist.IContentProposal
import org.eclipse.jface.fieldassist.IContentProposalProvider
import org.scalaide.debug.internal.expression.ExpressionManager

import com.sun.jdi.ReferenceType
import com.sun.jdi.StackFrame

/**
 * FIXME add real, serious code completion
 * Naive code completion implementation for expression evaluator. This one is just a trivial, temporary approach.
 * It contains only proposals from context's this, current variables, scala package object and scala.Predef.
 *
 * It uses the highest stack frame to get information about current this and variables available in scope.
 * If it's possible (the same class is available on debugger's classpath) reflection is used to get more detailed
 * information about fields and methods of this. Otherwise there's used information taken via JDI like in the case of
 * variables in scope.
 * In addition there are constant proposals taken once from scala package object and scala.Predef using reflection.
 *
 * So this implementation doesn't take into account imports and variables created in expression written inside expression evaluator.
 * Moreover it doesn't support proposals for methods and fields after dot (e.g. myVariable.toSt).
 * It would be better to e.g. somehow access and reuse properly code completion implemented in core for code editor.
 */
class SimpleContentProposalProvider extends IContentProposalProvider {

  override def getProposals(contents: String, pos: Int): Array[IContentProposal] =
    if (isCodeCompletionEnabled()) prepareProposals(contents, pos)
    else Array[IContentProposal]()

  protected def isCodeCompletionEnabled() = true

  // TODO maybe in certain cases it would be possible to try to evaluate some part of expression
  // if there's dot and then use only members of returned type instead of all proposals
  protected def proposalsForCurrentContext() = SimpleContentProposalProvider.proposalsForCurrentContext()

  private def prepareProposals(contents: String, pos: Int): Array[IContentProposal] = {
    val prefix = currentNamePrefix(contents, pos)

    val proposalsMatchingToCurrentText = proposalsForCurrentContext()
      .filter(_.getContent().startsWith(prefix))

    val currentProposals = for (p <- proposalsMatchingToCurrentText)
      yield new ContentProposal(p.getContent().substring(prefix.length()), p.getLabel(), p.getDescription())
    currentProposals.toArray[IContentProposal]
  }

  private[completion] def currentNamePrefix(contents: String, pos: Int) = {
    val separators = List(' ', '\t', '\n')

    val withoutPrefix = pos == 0 || pos > contents.length || separators.contains(contents(pos - 1))

    if (withoutPrefix) ""
    else {
      val words = contents.substring(0, pos).split("[ \t\n]")
      words.last.trim
    }
  }

}

object SimpleContentProposalProvider {
  import org.scalaide.debug.internal.expression.TypeNameMappings.javaNameToScalaName

  private lazy val constantProposals = createConstantProposals()

  /**
   * Proposals related to current debugging context plus constant proposals from scala package object and scala.Predef
   */
  def proposalsForCurrentContext(): Seq[IContentProposal] =
    // at first show sorted proposals from current context and then sorted constant proposals
    proposalsForEvaluationContext() ++ constantProposals

  private def proposalsForEvaluationContext() = {
    ExpressionManager.currentStackFrame().map { stackFrame =>
      Option(stackFrame.thisObject()).map { objectRef =>
        val thisReference = objectRef.referenceType()

        val members = getMembersOfThis(thisReference)
        val currentVariables = getAccessibleVariablesProposals(stackFrame)

        (members ++ currentVariables).sortBy(_.getLabel())
      }.getOrElse(Nil)
    }.getOrElse(Nil)
  }

  private def getMembersOfThis(thisReference: ReferenceType) = {
    val currentThisClassName = thisReference.name()
    try {
      getMembersForThisClassUsingReflection(currentThisClassName)
    } catch {
      // cannot create class for name localy so use information available via JDI
      case e: Throwable => getDistinctProposalsForThisViaJdi(thisReference, currentThisClassName)
    }
  }

  private def getMembersForThisClassUsingReflection(currentThisClassName: String) = {
    // there are only things currently available via current classpath so likely in most cases JDI will be used
    val mirror = ru.runtimeMirror(this.getClass.getClassLoader)
    val classSymbol = mirror.staticClass(currentThisClassName)
    val thisType = classSymbol.selfType
    val proposalsForCurrentThis = createContentProposalsForType(thisType, currentThisClassName)

    val owner = classSymbol.owner
    val proposalsForOwnerOfCurrentThis = // if we have outer class
      if (owner.toString() != "<none>") {
        createContentProposalsForType(owner.asClass.selfType, owner.name.decoded)
      } else Nil

    getDistinct(proposalsForCurrentThis ++ proposalsForOwnerOfCurrentThis)
  }

  private def getDistinctProposalsForThisViaJdi(thisReference: ReferenceType, currentThisClassName: String) = {
    val proposals = createContentProposalsForStrings(getMembersNamesViaJdi(thisReference), currentThisClassName)

    // in this case we don't show arguments lists so we could have many the same entries - we need to remove redundant ones
    getDistinct(proposals)
  }

  private def getAccessibleVariablesProposals(stackFrame: StackFrame) =
    stackFrame.visibleVariables().map { variable =>
      val typeName = variable.typeName()
      (variable.name(), s"${variable.name()}: ${javaNameToScalaName(typeName)}")
    }.groupBy { case (content, label) => content }
      .map {
        case (_, proposals) =>
          val (content, label) = proposals.last
          new ContentProposal(content, label, null)
      }(collection.breakOut) // we get only the latest variable with given name (shadowing)

  private def getMembersNamesViaJdi(thisReference: ReferenceType) =
    thisReference.allMethods()
      .flatMap { m =>
        val name = NameTransformer.decode(m.name())
        if (shouldBeProposal(name)) {
          // this is type visible from Java so it's e.g. void instead of Unit
          val returnedType = NameTransformer.decode(m.returnType().name())
          val returnedScalaType = javaNameToScalaName(returnedType)
          List((name, s"$name: ${returnedScalaType}"))
        } else Nil
      }(collection.breakOut)

  /**
   * Creates proposals that are not related to current execution context (scala package object and scala.Predef)
   */
  private def createConstantProposals() = {
    val proposalsFromPredef = createContentProposalsForType(getTypeTag(scala.Predef).tpe, "scala.Predef")

    // scala.package$.MODULE$ works from Eclipse like scala.Predef but it causes error during maven build via console
    // so it's a kind of workaround
    val mirror = ru.runtimeMirror(this.getClass.getClassLoader)
    val scalaPackageObjectSymbol = mirror.staticClass("scala.package$")

    val proposalsFromScalaPackageObject = createContentProposalsForType(scalaPackageObjectSymbol.selfType, "scala.package")

    // it would be nice to have also an access to all names from scala and java.lang packages
    getDistinct(proposalsFromScalaPackageObject ++ proposalsFromPredef)
      .sortBy(_.getLabel())
  }

  private def getMembersForType(tpe: ru.Type) = {
    val pairs = tpe.members.map(symbol => (symbol, symbol.name.decoded.trim()))
      .filter { case (symbol, name) => shouldBeProposal(name) }

    pairs.flatMap {
      case (symbol, name) =>
        if (symbol.isMethod)
          List((name, formatMethodLabel(name, symbol)))
        else if (symbol.isClass || symbol.isModule)
          List((name, name))
        else if (!symbol.isType)
          List((name, formatVariableLabel(name, symbol)))
        else
          Nil
    }(collection.breakOut)
  }

  private def getTypeTag[T: ru.TypeTag](obj: T) = ru.typeTag[T]

  private[completion] def formatMethodLabel(name: String, symbol: ru.Symbol) =
    s"${name}${formatMethodLabelParamsLists(symbol)}: ${symbol.asMethod.returnType}"

  private[completion] def formatMethodLabelParamsLists(symbol: ru.Symbol) =
    symbol.asMethod.paramss.map(formatParamsList).mkString("(", ")", ")")

  private[completion] def formatParamsList(args: Seq[ru.Symbol]) =
    args.map(arg => s"${arg.name.decoded}: ${arg.typeSignature}").mkString(", ")

  private[completion] def formatVariableLabel(name: String, symbol: ru.Symbol) = s"$name: ${symbol.typeSignature}"

  private def createContentProposalsForType(tpe: ru.Type, typeName: String) =
    createContentProposalsForStrings(getMembersForType(tpe), typeName)

  private[completion] def createContentProposalsForStrings(contentLabelPairs: Seq[(String, String)], typeName: String) =
    contentLabelPairs.map { case (content, label) => new ContentProposal(content, s"$label - $typeName", null) }

  private[completion] def shouldBeProposal(name: String) = !name.contains("$") && name != "<init>" && name != "<clinit>"

  private def getDistinct(proposals: Seq[IContentProposal]) =
    proposals.groupBy(_.getLabel)
      .flatMap { case (_, proposals) => List(proposals.head) }(collection.breakOut)
}
