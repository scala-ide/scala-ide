package util

sealed abstract class Box[+A] extends Product {
  type myInt = Int
}

@serializable
final case class Full[A](value: A) extends Box[A] {
  def productArity = error("not implemented")
}
