/*
 * Copyright (c) 2014 - 2015 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression
package context
package extensions

import scala.collection.JavaConversions._
import scala.reflect.runtime.universe
import scala.util.Try

import com.sun.jdi._

import org.scalaide.debug.internal.expression.Names.Debugger.contextParamName
import org.scalaide.debug.internal.expression.Names.Debugger.objectOrStaticCallProxyMethodName
import org.scalaide.debug.internal.expression.Names.Debugger.valueProxyMethodName
import org.scalaide.logging.HasLogger

/** Responsible for handling all context extension and provide aggregated results for expression context */
case class ExtendedContext(currentFrame: StackFrame, searchForMembers: Boolean)
    extends HasLogger {

  // aggregated current transformation
  private lazy val currentTransformation: Option[ThisTransformation] =
    createInitialTransformationContext
      .map(parentClasses)

  /** Get type for variable - if variable is one of 'this' variables returns its type */
  final def typeFor(name: universe.TermName): Option[Type] =
    currentTransformation.flatMap(_.elementMap.get(name)).map(_.referenceType)

  /**
   * Search for member using JDI in each of this element.
   * This is used only when we missing classpath (e.g. Remote debugging).
   */
  final def findMember(variable: Variable): Option[TypedVariable] =
    if (!searchForMembers) None
    else {
      val name = variable.name.toString
      currentTransformation.flatMap { transformation =>
        def matchingFields = for {
          thisElement <- transformation.thisHistory.toStream
          field <- thisElement.referenceType.allFields if field.name == name
          parentName <- transformation.nameMap.get(thisElement)
        } yield DynamicMemberBasedVariable(variable, parentName.toString)

        def matchingMethods = for {
          thisElement <- transformation.thisHistory.toStream
          method <- thisElement.referenceType.allMethods if method.name == name
          parentName <- transformation.nameMap.get(thisElement)
          arity <- Try(method.arity).toOption
        } yield DynamicMemberBasedMethod(variable, parentName.toString, arity)

        matchingFields.headOption orElse matchingMethods.headOption
      }
    }

  /** List of variables that mock this */
  final def thisFields: Seq[Variable] = currentTransformation match {
    case Some(transformation) =>
      transformation.thisHistory.map(transformation.nameMap).reverse.map(Variable(_))
    case _ => Nil
  }

  /**
   * Find implementation information about nested methods.
   * Returns None when we have cannot determine this object context.
   */
  final def nestedMethod(declaration: NestedMethodDeclaration): Option[NestedMethodImplementation] = {

    def isLocationInMethodDeclaration(location: Location): Boolean =
      declaration.startLine <= location.lineNumber() && location.lineNumber() <= declaration.endLine

    lazy val currentExecutionLine = currentFrame.location().lineNumber()

    def isCandidate(m: Method): Boolean = {
      def nameMatch = m.name().startsWith(declaration.name + '$')
      def placementCorrect = !m.allLineLocations().isEmpty && m.allLineLocations().forall(isLocationInMethodDeclaration)
      def declaredBeforeCurrentLine = m.location().lineNumber() < currentExecutionLine

      nameMatch && placementCorrect && declaredBeforeCurrentLine
    }
    currentTransformation.map {
      transformation =>
        val nestedMethodsImplementations = for {
          thisEntry <- transformation.thisHistory
          candidateMethod <- thisEntry.referenceType.methods() if isCandidate(candidateMethod)
        } yield NestedMethodImplementation(transformation.nameMap(thisEntry),
          candidateMethod.name(),
          candidateMethod.arguments().map(_.name()))

        nestedMethodsImplementations match {
          case Seq(onlyCandidate) =>
            onlyCandidate
          case candidates =>
            throw new MultipleMethodsMatchNestedOne(declaration.name, candidates.map(_.jvmName))
        }
    }
  }

  /** List of variables that mock this */
  final def imports: Seq[String] = currentTransformation match {
    case Some(transformation) =>
      def importCode(importElement: ImportElement): Option[String] = {
        val name = importElement.dependedOn.map(transformation.nameMap)
        importElement.creationCode(name).map(importCode => s"import $importCode")
      }
      transformation.importHistory.flatMap(importCode).reverse
    case _ => Nil
  }

  /** If name is one of this generate code for it */
  final def implementValue(name: universe.TermName): Option[String] =
    for {
      transformation <- currentTransformation
      thisElement <- transformation.elementMap.get(name)
    } yield {
      val name = thisElement.dependOn.map(transformation.nameMap)
      thisElement.creationCode(name)
    }

  private object ScalaTrait {
    def unapply(refType: Option[ReferenceType]): Option[ReferenceType] =
      if (refType.isEmpty) {
        Option(currentFrame.visibleVariableByName("$this")).map {
          variable =>
            currentFrame.getValue(variable).asInstanceOf[ObjectReference].referenceType
        }
      } else None
  }

  /**
   * create initial context:
   * from $this static field for traits
   * normal this for classes
   * none for java static methods
   */
  private def createInitialTransformationContext: Option[ThisTransformation] = {
    Option(currentFrame.thisObject()).map(_.referenceType()) match {
      case ScalaTrait(dollarThis) =>
        logger.info("Applying transformation for Traits")

        import org.scalaide.debug.internal.expression.Names.Debugger._
        def code(valName: Option[universe.TermName]) = s"""$contextParamName.$valueProxyMethodName("$$this")"""
        val newType = new ThisElement(dollarThis, None, code)

        Some(ThisTransformation(newType, new ImportAllElement(newType)))
      case Some(thisReference) =>
        val defElement = ThisElement(thisReference)
        val defImport = new ImportAllElement(defElement)

        Some(ThisTransformation(defElement, defImport))
      case _ =>
        None
    }
  }

  /** class existing in VM */
  private object ExistingClass {
    def unapply(name: String): Option[ReferenceType] =
      currentFrame.virtualMachine().classesByName(name).headOption
  }

  /** class that is nested in other class */
  object NestedScalaClass {
    val outerFieldName = "$outer"

    def unapply(refType: ReferenceType): Option[String] = {
      val field = refType.fieldByName(outerFieldName)
      field match {
        case null => None
        case outerField if refType.toString.startsWith(field.`type`().toString) =>
          Some(outerFieldName)
        case _ => None
      }
    }
  }

  /** search for parent classes */
  def parentClasses(currentTransformation: ThisTransformation): ThisTransformation = {
    currentTransformation.headTypeReference match {
      case referenceType @ NestedScalaClass(fieldName) =>
        parentClasses(currentTransformation.fromField(fieldName, referenceType))
      case _ => parentObjects(currentTransformation)
    }
  }

  /** return next possible parent class name or single $ */
  private def parentObjectName(name: String): String = {
    val tweakedName = if (name.endsWith("$$")) {
      name.dropRight(1) + "_$" //replace $$ with $_$
    } else name
    tweakedName.split("\\$").dropRight(1).mkString("", "$", "$")
  }

  /** search and add all parent objects */
  def parentObjects(currentTransformation: ThisTransformation): ThisTransformation = {
    //get list of all possible parent objects as ReferenceType
    def findParents(name: String): Seq[ReferenceType] = parentObjectName(name) match {
      case "$" => Nil
      case ExistingClass(objectReference) =>
        objectReference +: findParents(objectReference.toString)
      case notObject => findParents(notObject)
    }

    //transform ThisTransformation adding new parent object
    def transformationForParent(currentTransformation: ThisTransformation, objectReference: ReferenceType): ThisTransformation = {
      val objectName = objectReference.toString.dropRight(1) //remove $ at end
      import org.scalaide.debug.internal.expression.Names.Debugger._
      val creationCode = s"""$contextParamName.$objectOrStaticCallProxyMethodName("$objectName")"""
      val thisElement = new ThisElement(objectReference, None, _ => creationCode)
      val importElement = new ImportAllElement(thisElement)

      currentTransformation.copy(
        thisHistory = currentTransformation.thisHistory :+ thisElement,
        importHistory = currentTransformation.importHistory :+ importElement)
    }
    findParents(currentTransformation.headTypeReference.toString).foldLeft(currentTransformation)(transformationForParent)
  }

}
