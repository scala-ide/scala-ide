/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.context.extensions

import scala.reflect.runtime.universe

import org.scalaide.debug.internal.expression.Names

import com.sun.jdi.ReferenceType

/**
 * Representing value that on combination with others can simulate logical Scala this.
 * @param referenceType
 * @param dependOn if this element depends on another (e.g. is created as field from another)
 * @creationCode code that initialize those value. It it not transformed so it must use contexts etc.
 */
class ThisElement(val referenceType: ReferenceType, val dependOn: Option[ThisElement], val creationCode: Option[universe.TermName] => String)

object ThisElement {

  def apply(referenceType: ReferenceType): ThisElement = {
    import Names.Debugger._
    new ThisElement(referenceType, None, _ => s"""$contextParamName.$thisObjectProxyMethodName""")
  }
}

/**
 * Representing import that is done from given this element
 * @param dependedOn if this import depends on given thisElement
 * @param creationCode generates code for given import (like $name._ not import this._ )
 */
class ImportElement(val dependedOn: Option[ThisElement], val creationCode: Option[universe.TermName] => Option[String])

/** Import element for import all like _this._ */
class ImportAllElement(dependsOn: ThisElement) extends ImportElement(Some(dependsOn), _.map(_ + "._"))

/**
 * Transformation of this value
 * Containing list of middle values that are used and sequence of import from them.
 * Example use case:
 * val __this1 = ...
 * val __this = context.proxyForField(__this1, "outer")
 * import __this1._
 * import __this._
 * @param thisHistory sequence of this values that will create this context
 * @param importHistory sequence of imports from this values
 */
final case class ThisTransformation private(thisHistory: Seq[ThisElement], importHistory: Seq[ImportElement]) {

  def headElement = thisHistory.head

  def headTypeReference = headElement.referenceType

  def fromField(fieldName: String, from: ReferenceType): ThisTransformation = {
    val refType = from.fieldByName(fieldName).`type`.asInstanceOf[ReferenceType]

    import org.scalaide.debug.internal.expression.Names.Debugger._

    def code(valName: Option[universe.TermName]) =
      s"""$contextParamName.$objectProxyForFieldMethodName(${valName.get}, "$fieldName")"""

    val newType = new ThisElement(refType, Some(headElement), code)

    copy(
      thisHistory = newType +: thisHistory, //this is oldThis.$outer
      importHistory = new ImportAllElement(newType) +: importHistory
    )
  }

  /** Maps elements to names */
  private[context] lazy val nameMap: Map[ThisElement, universe.TermName] = thisHistory.zipWithIndex.map {
    case (thisElement, 0) => thisElement -> universe.TermName(Names.Debugger.thisValName)
    case (thisElement, nr) => thisElement -> universe.TermName(Names.Debugger.thisValName + "_" + nr)
  }(collection.breakOut)

  /** Maps names to elements */
  private[context] lazy val elementMap: Map[universe.TermName, ThisElement] = nameMap.map(_.swap)
}

object ThisTransformation {
  def apply(initialThis: ThisElement, initialImport: ImportElement) = new ThisTransformation(Seq(initialThis), Seq(initialImport))
}
