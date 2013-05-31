package t1001005

import java.io.IOException
import java.net.SocketException

class Callee @throws(classOf[InstantiationException]) () {

  @throws(classOf[NoSuchFieldException])
  def this(i: Int) {
    this()
  }

  @throws(classOf[IOException])
  @throws(classOf[SocketException])
  def doStuff(i: Int) {
    if (i > 0) throw new IOException
    else throw new SocketException
  }
}