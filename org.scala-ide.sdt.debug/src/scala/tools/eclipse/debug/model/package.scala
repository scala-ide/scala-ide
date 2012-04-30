package scala.tools.eclipse.debug

import sun.reflect.generics.reflectiveObjects.NotImplementedException
package object model {

  // copied from scala trunk (2.10) for 2.9 support
  // TODO: remove when not using '???' any more

  final class NotImplementedError(msg: String) extends Error(msg) {
    def this() = this("an implementation is missing")
  }

  def ??? : Nothing = throw new NotImplementedError

}