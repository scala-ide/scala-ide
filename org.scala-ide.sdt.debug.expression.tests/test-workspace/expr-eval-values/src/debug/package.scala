package object debug{

  trait LibAnyTrait extends Any {
    def ala = "ala"
  }

  implicit class LibAnyVal(int: Int) extends AnyRef with LibAnyTrait {
    def printMe() = int.toString
    def asInt() = int
  }

}