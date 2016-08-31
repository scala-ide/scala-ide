package org.scalaide.ui.internal.preferences

import scala.annotation.tailrec

/**
 * Simple utility for encoding/decoding lists of arbitrary strings.
 */
object StringListMapper {
  private final val StopChar = '|'

  /**
   * Encodes a list of strings into a single string in a bijective manner
   *
   * Only use this method in connection with `decode`. The used encoding,
   * that should be regarded as implementation detail, does not impose any
   * restrictions on the input strings.
   */
  def encode(strs: Seq[String]): String = {
    strs.map(s => s.length() + (StopChar + s)).mkString("")
  }

  /**
   * Inverse operation to `encode`
   */
  def decode(str: String): Seq[String] = {
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
