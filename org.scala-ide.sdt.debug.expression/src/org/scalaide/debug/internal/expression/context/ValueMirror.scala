/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.context

import scala.annotation.implicitNotFound
import com.sun.jdi.BooleanValue
import com.sun.jdi.CharValue
import com.sun.jdi.DoubleValue
import com.sun.jdi.FloatValue
import com.sun.jdi.IntegerValue
import com.sun.jdi.LongValue
import com.sun.jdi.StringReference
import com.sun.jdi.VirtualMachine
import com.sun.jdi.ByteValue
import com.sun.jdi.ShortValue

/**
 * Type class for mirroring primitives and String on JVM in JdiContext.
 */
@implicitNotFound("Mirroring type $T is not supported.")
sealed trait ValueMirror[ValueType, ReturnType] {
  def mirrorOf(value: ValueType, jvm: VirtualMachine): ReturnType
}

/**
 * Instances of `ValueMirror` for `java.lang.String` and Java primitives.
 */
object ValueMirror {

  /** Helps with ValueMirror creation */
  private def apply[ValueType, ReturnType](f: (VirtualMachine, ValueType) => ReturnType) =
    new ValueMirror[ValueType, ReturnType] {
      def mirrorOf(value: ValueType, jvm: VirtualMachine) = f(jvm, value)
    }

  implicit val stringMirror = ValueMirror[String, StringReference](_ mirrorOf _)

  implicit val booleanMirror = ValueMirror[Boolean, BooleanValue](_ mirrorOf _)

  implicit val byteMirror = ValueMirror[Byte, ByteValue](_ mirrorOf _)

  implicit val shortMirror = ValueMirror[Short, ShortValue](_ mirrorOf _)

  implicit val intMirror = ValueMirror[Int, IntegerValue](_ mirrorOf _)

  implicit val longMirror = ValueMirror[Long, LongValue](_ mirrorOf _)

  implicit val charMirror = ValueMirror[Char, CharValue](_ mirrorOf _)

  implicit val floatMirror = ValueMirror[Float, FloatValue](_ mirrorOf _)

  implicit val doubleMirror = ValueMirror[Double, DoubleValue](_ mirrorOf _)
}