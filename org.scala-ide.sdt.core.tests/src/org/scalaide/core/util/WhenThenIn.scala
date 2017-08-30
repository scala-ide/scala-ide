package org.scalaide.core.util

class when private {
  def `then`(description: String) = this
  def in(body: => Unit): Unit = body
}

object when {
  def apply(description: String) = new when
}