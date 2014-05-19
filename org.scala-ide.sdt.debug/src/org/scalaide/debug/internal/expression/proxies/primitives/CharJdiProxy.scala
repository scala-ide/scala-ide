/*
 * Copyright (c) 2014 Contributor. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Scala License which accompanies this distribution, and
 * is available at http://www.scala-lang.org/node/146
 */
package org.scalaide.debug.internal.expression.proxies.primitives

import scala.runtime.RichChar

import org.scalaide.debug.internal.expression.JavaBoxed
import org.scalaide.debug.internal.expression.JavaPrimitives
import org.scalaide.debug.internal.expression.context.JdiContext

import com.sun.jdi.CharValue
import com.sun.jdi.ObjectReference
import com.sun.jdi.Value

/**
 * JdiProxy implementation for `char` and `scala.Char` and `java.lang.Character`.
 */
case class CharJdiProxy(context: JdiContext, underlying: ObjectReference)
  extends IntegerNumberJdiProxy[Char, CharJdiProxy](CharJdiProxy) {

  override def unary_- : IntJdiProxy = context.proxy(-primitiveValue)
  override def unary_~ : IntJdiProxy = context.proxy(~primitiveValue)

  def asDigit: IntJdiProxy = context.proxy(Character.digit(primitiveValue, Character.MAX_RADIX))

  def isControl: BooleanJdiProxy = context.proxy(Character.isISOControl(primitiveValue))
  def isDigit: BooleanJdiProxy = context.proxy(Character.isDigit(primitiveValue))
  def isLetter: BooleanJdiProxy = context.proxy(Character.isLetter(primitiveValue))
  def isLetterOrDigit: BooleanJdiProxy = context.proxy(Character.isLetterOrDigit(primitiveValue))
  def isWhitespace: BooleanJdiProxy = context.proxy(Character.isWhitespace(primitiveValue))
  def isSpaceChar: BooleanJdiProxy = context.proxy(Character.isSpaceChar(primitiveValue))
  def isHighSurrogate: BooleanJdiProxy = context.proxy(Character.isHighSurrogate(primitiveValue))
  def isLowSurrogate: BooleanJdiProxy = context.proxy(Character.isLowSurrogate(primitiveValue))
  def isSurrogate: BooleanJdiProxy = isHighSurrogate || isLowSurrogate
  def isUnicodeIdentifierStart: BooleanJdiProxy = context.proxy(Character.isUnicodeIdentifierStart(primitiveValue))
  def isUnicodeIdentifierPart: BooleanJdiProxy = context.proxy(Character.isUnicodeIdentifierPart(primitiveValue))
  def isIdentifierIgnorable: BooleanJdiProxy = context.proxy(Character.isIdentifierIgnorable(primitiveValue))
  def isMirrored: BooleanJdiProxy = context.proxy(Character.isMirrored(primitiveValue))

  def isLower: BooleanJdiProxy = context.proxy(Character.isLowerCase(primitiveValue))
  def isUpper: BooleanJdiProxy = context.proxy(Character.isUpperCase(primitiveValue))
  def isTitleCase: BooleanJdiProxy = context.proxy(Character.isTitleCase(primitiveValue))

  def toLower: CharJdiProxy = context.proxy(Character.toLowerCase(primitiveValue))
  def toUpper: CharJdiProxy = context.proxy(Character.toUpperCase(primitiveValue))
  def toTitleCase: CharJdiProxy = context.proxy(Character.toTitleCase(primitiveValue))

  def getType: IntJdiProxy = context.proxy(Character.getType(primitiveValue))
  def getNumericValue: IntJdiProxy = context.proxy(Character.getNumericValue(primitiveValue))
  def getDirectionality: ByteJdiProxy = context.proxy(Character.getDirectionality(primitiveValue))
  def reverseBytes: CharJdiProxy = context.proxy(Character.reverseBytes(primitiveValue))

  protected override def primitiveValue = this.primitive.asInstanceOf[CharValue].value()
  protected override def numberProxy = new RichChar(primitiveValue)
}

object CharJdiProxy extends BoxedJdiProxyCompanion[Char, CharJdiProxy](JavaBoxed.Character, JavaPrimitives.char) {
  protected def mirror(value: Char, context: JdiContext): Value = context.mirrorOf(value)
}