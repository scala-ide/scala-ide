/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression

import scala.reflect.runtime.universe

object AstMatchers {

  /** Matches TypeRef that represents Array, extracts it's type parameter. */
  object ArrayRef {
    def unapply(typeRef: universe.TypeRef): Option[universe.Type] =
      if (typeRef.sym == universe.definitions.ArrayClass) Some(typeRef.args.head)
      else None
  }
}