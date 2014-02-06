package org.scalaide.util.internal

object Trim {
  def apply(v: String): Option[String] = Option(v).map(_.trim).filter(_.length > 0)

  def apply(v: Option[String]): Option[String] = v.flatMap(apply)
}
