package valfinding

object Main {
  val zz = 24

  def main(args: Array[String]): Unit = {
    val a = new ScalaClass("nonFieldClassParam", "fieldClassParam")

    val outer = new OuterClass
    val inner = new outer.InnerClass

    val derived = new DerivedClass(47) with TheTrait
    derived.traitFunc()

    (new ExplicitExtenderOfTheTrait).fun

    new EnclosingTrait{}

    Objectt.f("Obj f param")

    (new ClosureTest).useClosures()
  }
}
