/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.proxies.primitives.operations.numeric

import scala.annotation.implicitNotFound

import org.scalaide.debug.internal.expression.proxies.primitives.ByteJdiProxy
import org.scalaide.debug.internal.expression.proxies.primitives.CharJdiProxy
import org.scalaide.debug.internal.expression.proxies.primitives.DoubleJdiProxy
import org.scalaide.debug.internal.expression.proxies.primitives.FloatJdiProxy
import org.scalaide.debug.internal.expression.proxies.primitives.IntJdiProxy
import org.scalaide.debug.internal.expression.proxies.primitives.LongJdiProxy
import org.scalaide.debug.internal.expression.proxies.primitives.NumberJdiProxy
import org.scalaide.debug.internal.expression.proxies.primitives.ShortJdiProxy

/**
 * Type class responsible for correct return types from numeric operations.
 */
@implicitNotFound("Cannot perform numeric operation on ${This} and ${That}")
sealed trait NumOpRes[This <: NumberJdiProxy[_, This], That <: NumberJdiProxy[_, That], Res <: NumberJdiProxy[_, Res]]

/** Implementations of `NumOpRes` for all allowed type combinations. */
object NumOpRes {

  implicit val ByteByte = new NumOpRes[ByteJdiProxy, ByteJdiProxy, IntJdiProxy] {}
  implicit val ShortByte = new NumOpRes[ShortJdiProxy, ByteJdiProxy, IntJdiProxy] {}
  implicit val CharByte = new NumOpRes[CharJdiProxy, ByteJdiProxy, IntJdiProxy] {}
  implicit val IntByte = new NumOpRes[IntJdiProxy, ByteJdiProxy, IntJdiProxy] {}
  implicit val FloatByte = new NumOpRes[FloatJdiProxy, ByteJdiProxy, FloatJdiProxy] {}
  implicit val DoubleByte = new NumOpRes[DoubleJdiProxy, ByteJdiProxy, DoubleJdiProxy] {}
  implicit val LongByte = new NumOpRes[LongJdiProxy, ByteJdiProxy, LongJdiProxy] {}

  implicit val ByteShort = new NumOpRes[ByteJdiProxy, ShortJdiProxy, IntJdiProxy] {}
  implicit val ShortShort = new NumOpRes[ShortJdiProxy, ShortJdiProxy, IntJdiProxy] {}
  implicit val CharShort = new NumOpRes[CharJdiProxy, ShortJdiProxy, IntJdiProxy] {}
  implicit val IntShort = new NumOpRes[IntJdiProxy, ShortJdiProxy, IntJdiProxy] {}
  implicit val FloatShort = new NumOpRes[FloatJdiProxy, ShortJdiProxy, FloatJdiProxy] {}
  implicit val DoubleShort = new NumOpRes[DoubleJdiProxy, ShortJdiProxy, DoubleJdiProxy] {}
  implicit val LongShort = new NumOpRes[LongJdiProxy, ShortJdiProxy, LongJdiProxy] {}

  implicit val ByteChar = new NumOpRes[ByteJdiProxy, CharJdiProxy, IntJdiProxy] {}
  implicit val ShortChar = new NumOpRes[ShortJdiProxy, CharJdiProxy, IntJdiProxy] {}
  implicit val CharChar = new NumOpRes[CharJdiProxy, CharJdiProxy, IntJdiProxy] {}
  implicit val IntChar = new NumOpRes[IntJdiProxy, CharJdiProxy, IntJdiProxy] {}
  implicit val FloatChar = new NumOpRes[FloatJdiProxy, CharJdiProxy, FloatJdiProxy] {}
  implicit val DoubleChar = new NumOpRes[DoubleJdiProxy, CharJdiProxy, DoubleJdiProxy] {}
  implicit val LongChar = new NumOpRes[LongJdiProxy, CharJdiProxy, LongJdiProxy] {}

  implicit val ByteInt = new NumOpRes[ByteJdiProxy, IntJdiProxy, IntJdiProxy] {}
  implicit val ShortInt = new NumOpRes[ShortJdiProxy, IntJdiProxy, IntJdiProxy] {}
  implicit val CharInt = new NumOpRes[CharJdiProxy, IntJdiProxy, IntJdiProxy] {}
  implicit val IntInt = new NumOpRes[IntJdiProxy, IntJdiProxy, IntJdiProxy] {}
  implicit val FloatInt = new NumOpRes[FloatJdiProxy, IntJdiProxy, FloatJdiProxy] {}
  implicit val DoubleInt = new NumOpRes[DoubleJdiProxy, IntJdiProxy, DoubleJdiProxy] {}
  implicit val LongInt = new NumOpRes[LongJdiProxy, IntJdiProxy, LongJdiProxy] {}

  implicit val ByteLong = new NumOpRes[ByteJdiProxy, LongJdiProxy, LongJdiProxy] {}
  implicit val ShortLong = new NumOpRes[ShortJdiProxy, LongJdiProxy, LongJdiProxy] {}
  implicit val CharLong = new NumOpRes[CharJdiProxy, LongJdiProxy, LongJdiProxy] {}
  implicit val IntLong = new NumOpRes[IntJdiProxy, LongJdiProxy, LongJdiProxy] {}
  implicit val FloatLong = new NumOpRes[FloatJdiProxy, LongJdiProxy, FloatJdiProxy] {}
  implicit val DoubleLong = new NumOpRes[DoubleJdiProxy, LongJdiProxy, DoubleJdiProxy] {}
  implicit val LongLong = new NumOpRes[LongJdiProxy, LongJdiProxy, LongJdiProxy] {}

  implicit val ByteFloat = new NumOpRes[ByteJdiProxy, FloatJdiProxy, FloatJdiProxy] {}
  implicit val ShortFloat = new NumOpRes[ShortJdiProxy, FloatJdiProxy, FloatJdiProxy] {}
  implicit val CharFloat = new NumOpRes[CharJdiProxy, FloatJdiProxy, FloatJdiProxy] {}
  implicit val IntFloat = new NumOpRes[IntJdiProxy, FloatJdiProxy, FloatJdiProxy] {}
  implicit val FloatFloat = new NumOpRes[FloatJdiProxy, FloatJdiProxy, FloatJdiProxy] {}
  implicit val DoubleFloat = new NumOpRes[DoubleJdiProxy, FloatJdiProxy, DoubleJdiProxy] {}
  implicit val LongFloat = new NumOpRes[LongJdiProxy, FloatJdiProxy, FloatJdiProxy] {}

  implicit val ByteDouble = new NumOpRes[ByteJdiProxy, DoubleJdiProxy, DoubleJdiProxy] {}
  implicit val ShortDouble = new NumOpRes[ShortJdiProxy, DoubleJdiProxy, DoubleJdiProxy] {}
  implicit val CharDouble = new NumOpRes[CharJdiProxy, DoubleJdiProxy, DoubleJdiProxy] {}
  implicit val IntDouble = new NumOpRes[IntJdiProxy, DoubleJdiProxy, DoubleJdiProxy] {}
  implicit val FloatDouble = new NumOpRes[FloatJdiProxy, DoubleJdiProxy, DoubleJdiProxy] {}
  implicit val DoubleDouble = new NumOpRes[DoubleJdiProxy, DoubleJdiProxy, DoubleJdiProxy] {}
  implicit val LongDouble = new NumOpRes[LongJdiProxy, DoubleJdiProxy, DoubleJdiProxy] {}

}