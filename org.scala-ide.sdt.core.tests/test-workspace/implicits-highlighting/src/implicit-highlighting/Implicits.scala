package implicits

class Implicits {
  implicit def listToString[T](l: List[T]): String = {
    l.mkString("List(", " ", ")")
  }
  
  val str: String = List(1,2,3) /*<*/
  
  println(List(1,2): String) /*<*/
}