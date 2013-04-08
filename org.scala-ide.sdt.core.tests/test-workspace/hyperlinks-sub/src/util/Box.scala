package util

sealed abstract class Box[+A] {
  type myInt = Int
}

final case class Full[A](value: A) extends Box[A] with Serializable
