/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.proxies.primitives.operations.bitwise

import scala.annotation.implicitNotFound

import org.scalaide.debug.internal.expression.proxies.primitives.ByteJdiProxy
import org.scalaide.debug.internal.expression.proxies.primitives.CharJdiProxy
import org.scalaide.debug.internal.expression.proxies.primitives.IntJdiProxy
import org.scalaide.debug.internal.expression.proxies.primitives.IntegerNumberJdiProxy
import org.scalaide.debug.internal.expression.proxies.primitives.LongJdiProxy
import org.scalaide.debug.internal.expression.proxies.primitives.ShortJdiProxy

/**
 * Type class responsible for correct return types from bitwise operations.
 */
@implicitNotFound("Cannot perform bitwise operation on ${This} and ${That}")
sealed trait BitOpRes[This <: IntegerNumberJdiProxy[_, This], That <: IntegerNumberJdiProxy[_, That], Res <: IntegerNumberJdiProxy[_, Res]]

/** Implementations of `BitOpRes` for all allowed type combinations. */
object BitOpRes {

  implicit val ByteByte = new BitOpRes[ByteJdiProxy, ByteJdiProxy, IntJdiProxy] {}
  implicit val ShortByte = new BitOpRes[ShortJdiProxy, ByteJdiProxy, IntJdiProxy] {}
  implicit val CharByte = new BitOpRes[CharJdiProxy, ByteJdiProxy, IntJdiProxy] {}
  implicit val IntByte = new BitOpRes[IntJdiProxy, ByteJdiProxy, IntJdiProxy] {}
  implicit val LongByte = new BitOpRes[LongJdiProxy, ByteJdiProxy, LongJdiProxy] {}

  implicit val ByteShort = new BitOpRes[ByteJdiProxy, ShortJdiProxy, IntJdiProxy] {}
  implicit val ShortShort = new BitOpRes[ShortJdiProxy, ShortJdiProxy, IntJdiProxy] {}
  implicit val CharShort = new BitOpRes[CharJdiProxy, ShortJdiProxy, IntJdiProxy] {}
  implicit val IntShort = new BitOpRes[IntJdiProxy, ShortJdiProxy, IntJdiProxy] {}
  implicit val LongShort = new BitOpRes[LongJdiProxy, ShortJdiProxy, LongJdiProxy] {}

  implicit val ByteChar = new BitOpRes[ByteJdiProxy, CharJdiProxy, IntJdiProxy] {}
  implicit val ShortChar = new BitOpRes[ShortJdiProxy, CharJdiProxy, IntJdiProxy] {}
  implicit val CharChar = new BitOpRes[CharJdiProxy, CharJdiProxy, IntJdiProxy] {}
  implicit val IntChar = new BitOpRes[IntJdiProxy, CharJdiProxy, IntJdiProxy] {}
  implicit val LongChar = new BitOpRes[LongJdiProxy, CharJdiProxy, LongJdiProxy] {}

  implicit val ByteInt = new BitOpRes[ByteJdiProxy, IntJdiProxy, IntJdiProxy] {}
  implicit val ShortInt = new BitOpRes[ShortJdiProxy, IntJdiProxy, IntJdiProxy] {}
  implicit val CharInt = new BitOpRes[CharJdiProxy, IntJdiProxy, IntJdiProxy] {}
  implicit val IntInt = new BitOpRes[IntJdiProxy, IntJdiProxy, IntJdiProxy] {}
  implicit val LongInt = new BitOpRes[LongJdiProxy, IntJdiProxy, LongJdiProxy] {}

  implicit val ByteLong = new BitOpRes[ByteJdiProxy, LongJdiProxy, LongJdiProxy] {}
  implicit val ShortLong = new BitOpRes[ShortJdiProxy, LongJdiProxy, LongJdiProxy] {}
  implicit val CharLong = new BitOpRes[CharJdiProxy, LongJdiProxy, LongJdiProxy] {}
  implicit val IntLong = new BitOpRes[IntJdiProxy, LongJdiProxy, LongJdiProxy] {}
  implicit val LongLong = new BitOpRes[LongJdiProxy, LongJdiProxy, LongJdiProxy] {}
}

/**
 * Type class responsible for correct return types from bitwise shifts.
 */
@implicitNotFound("Cannot perform bitwise shift on ${This} and ${That}")
sealed trait BitShiftRes[This <: IntegerNumberJdiProxy[_, This], That <: IntegerNumberJdiProxy[_, That], Res <: IntegerNumberJdiProxy[_, Res]]

/** Implementations of `BitShiftRes` for all allowed type combinations. */
object BitShiftRes {

  implicit val ByteByte = new BitShiftRes[ByteJdiProxy, ByteJdiProxy, IntJdiProxy] {}
  implicit val ShortByte = new BitShiftRes[ShortJdiProxy, ByteJdiProxy, IntJdiProxy] {}
  implicit val CharByte = new BitShiftRes[CharJdiProxy, ByteJdiProxy, IntJdiProxy] {}
  implicit val IntByte = new BitShiftRes[IntJdiProxy, ByteJdiProxy, IntJdiProxy] {}
  implicit val LongByte = new BitShiftRes[LongJdiProxy, ByteJdiProxy, LongJdiProxy] {}

  implicit val ByteShort = new BitShiftRes[ByteJdiProxy, ShortJdiProxy, IntJdiProxy] {}
  implicit val ShortShort = new BitShiftRes[ShortJdiProxy, ShortJdiProxy, IntJdiProxy] {}
  implicit val CharShort = new BitShiftRes[CharJdiProxy, ShortJdiProxy, IntJdiProxy] {}
  implicit val IntShort = new BitShiftRes[IntJdiProxy, ShortJdiProxy, IntJdiProxy] {}
  implicit val LongShort = new BitShiftRes[LongJdiProxy, ShortJdiProxy, LongJdiProxy] {}

  implicit val ByteChar = new BitShiftRes[ByteJdiProxy, CharJdiProxy, IntJdiProxy] {}
  implicit val ShortChar = new BitShiftRes[ShortJdiProxy, CharJdiProxy, IntJdiProxy] {}
  implicit val CharChar = new BitShiftRes[CharJdiProxy, CharJdiProxy, IntJdiProxy] {}
  implicit val IntChar = new BitShiftRes[IntJdiProxy, CharJdiProxy, IntJdiProxy] {}
  implicit val LongChar = new BitShiftRes[LongJdiProxy, CharJdiProxy, LongJdiProxy] {}

  implicit val ByteInt = new BitShiftRes[ByteJdiProxy, IntJdiProxy, IntJdiProxy] {}
  implicit val ShortInt = new BitShiftRes[ShortJdiProxy, IntJdiProxy, IntJdiProxy] {}
  implicit val CharInt = new BitShiftRes[CharJdiProxy, IntJdiProxy, IntJdiProxy] {}
  implicit val IntInt = new BitShiftRes[IntJdiProxy, IntJdiProxy, IntJdiProxy] {}
  implicit val LongInt = new BitShiftRes[LongJdiProxy, IntJdiProxy, LongJdiProxy] {}

  implicit val ByteLong = new BitShiftRes[ByteJdiProxy, LongJdiProxy, IntJdiProxy] {}
  implicit val ShortLong = new BitShiftRes[ShortJdiProxy, LongJdiProxy, IntJdiProxy] {}
  implicit val CharLong = new BitShiftRes[CharJdiProxy, LongJdiProxy, IntJdiProxy] {}
  implicit val IntLong = new BitShiftRes[IntJdiProxy, LongJdiProxy, IntJdiProxy] {}
  implicit val LongLong = new BitShiftRes[LongJdiProxy, LongJdiProxy, LongJdiProxy] {}
}