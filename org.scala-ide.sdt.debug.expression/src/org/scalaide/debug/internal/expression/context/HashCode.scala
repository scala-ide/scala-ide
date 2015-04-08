/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.context

import org.scalaide.debug.internal.expression.proxies.JdiProxy

/**
 * Implementation of 'hashCode'.
 */
private[context] trait HashCode {
  self: JdiContext =>

  final def generateHashCode(proxy: JdiProxy): JdiProxy =
    this.invokeMethod(proxy, None, "hashCode")

}
