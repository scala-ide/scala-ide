package org.scalaide.util.internal

object Commons {

  /**
   * In contrast to `String.split` this provides a more accurate implementation
   * of a function that splits a string on _every_ occurence of a given character
   * in its subparts (a property that does not hold for `String.split`):
   *
   * {{{
   * scala> split("", '/')
   * res29: Seq[String] = Vector("")
   *
   * scala> split("/", '/')
   * res30: Seq[String] = Vector("", "")
   *
   * scala> split("a//a//", '/')
   * res31: Seq[String] = Vector(a, "", a, "", "")
   *
   * // this behavior is inaccurate, it removes valid subparts
   * scala> "a//a//".split('/')
   * res32: Array[String] = Array(a, "", a)
   * }}}
   */
  def split(str: String, c: Char): Seq[String] = {
    var xs = Vector[String]()
    var pos, end = 0
    while ({end = str.indexOf(c, pos); end} >= 0) {
      xs :+= str.substring(pos, end)
      pos = end+1
    }
    xs :+= str.substring(pos)
    xs
  }
}