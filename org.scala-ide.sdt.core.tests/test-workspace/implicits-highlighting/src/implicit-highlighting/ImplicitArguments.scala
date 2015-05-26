package implicits

class ImplicitArguments {
  implicit val s = "implicit"

  def takesImplArg(implicit s: String): Unit

  takesImplArg
  takesImplArg("explicit")
}