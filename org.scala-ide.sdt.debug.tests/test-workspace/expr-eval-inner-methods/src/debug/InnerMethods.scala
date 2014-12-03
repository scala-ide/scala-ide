package debug

object InnerMethods extends App {
  def main(): Unit = {
    val ala = "ala"

    def innerMethod() = ala
    def innerMethod2(name: String) = name + ala

    val out = innerMethod() + innerMethod2("ala")
  }

  def main(i: Int): Unit = {
    def innerMethod() = "ola"
    innerMethod()
  }

  main()
}