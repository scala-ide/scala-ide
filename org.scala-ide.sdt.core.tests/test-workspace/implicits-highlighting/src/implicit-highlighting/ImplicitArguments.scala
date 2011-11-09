package implicits

class ImplicitArguments {
  implicit val s = "implicit"
    
  def takesImplArg(implicit s: String)
  
  takesImplArg
  takesImplArg("explicit")
}