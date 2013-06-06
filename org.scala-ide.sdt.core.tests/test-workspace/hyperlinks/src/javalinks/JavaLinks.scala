package javalinks

import util.JavaMethods

class JavaLinks {
  def foo {
    val x = JavaMethods.nArray/*^*/(Array("2", "3"))
    val y = JavaMethods.nArray/*^*/(Array(2, 3))
    val over = new JavaMethods[Integer]

    over.typeparam/*^*/(10)
    over.typeparam2/*^*/(Array(10, 11, 12))
  }

}