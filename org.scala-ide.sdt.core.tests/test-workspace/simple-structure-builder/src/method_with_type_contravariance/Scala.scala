package method_with_type_contravariance

import java.util.ArrayList

class Scala {
  def foo[T <: Foo, U <: ArrayList[_ >: Class[T]]](u: U, t: T):T  = t
}