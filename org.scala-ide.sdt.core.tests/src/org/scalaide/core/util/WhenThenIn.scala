package org.scalaide.core.util

class when private (description: String) {
  def therefore(description: String) = this
  def in(body: => Unit): Unit = body
}

object when {
  def apply(description: String) = new when(description)
}