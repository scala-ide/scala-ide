package org.scalaide.ui.internal.preferences

import scala.annotation.tailrec

/**
 * Simple utility for serializing/deserializing lists of arbitrary strings.
 */
object StringListSerializer {
  private final val StopChar = '|'

  def serialize(strs: Seq[String]): String = {
    strs.map(s => s.length() + (StopChar + s)).mkString("")
  }

  def deserialize(str: String): Seq[String] = {
    @tailrec
    def go(str: String = str, acc: List[String] = Nil): List[String] = {
      if (str.isEmpty()) {
        acc
      } else {
        val header = str.takeWhile(_ != StopChar)
        val nChars = header.toInt
        val offset = header.length() + 1
        val end = offset + nChars
        if (end > str.size) {
          throw new IllegalArgumentException(s"Error parsing $str: $header is out of range")
        } else {
          go(str.substring(end), str.substring(offset, end) :: acc)
        }
      }
    }

    go().reverse
  }
}
