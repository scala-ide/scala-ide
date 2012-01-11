package scala.tools.eclipse.completion

object HasArgs extends Enumeration {
  val NoArgs, EmptyArgs, NonEmptyArgs = Value
  
  /** Given a list of method's parameters it tells if the method 
   * arguments should be adorned with parenthesis. */
  def from(params: List[List[_]]) = params match {
  	case Nil => NoArgs
    case Nil :: Nil => EmptyArgs
    case _ => NonEmptyArgs
  }
}

/** A completion proposal coming from the Scala compiler. This 
 *  class holds together data about completion proposals.
 *  
 *  This class is independent of both the Scala compiler (does not
 *  know about Symbols and Types), and the UI elements used to
 *  display it to the user.
 */
case class CompletionProposal(kind: MemberKind.Value,
  startPos: Int,             // position where the 'completion' string should be inserted
  completion: String,        // the string to be inserted in the document
  display: String,           // the display string in the completion list
  tooltip: String,           // tooltip info showed after a completion has been selected
  displayDetail: String,     // additional details to be display in the completion list (like package for a class)
  relevance: Int,
  hasArgs: HasArgs.Value,
  isJava: Boolean,
  explicitParamNames: List[List[String]], // parameter names (excluding any implicit parameter sections)
  fullyQualifiedName: String, // for Class, Trait, Type, Objects: the fully qualified name
  needImport: Boolean        // for Class, Trait, Type, Objects: import statement has to be added
)

/** The kind of a completion proposal. */
object MemberKind extends Enumeration {
  val Class, Trait, Type, Object, Package, PackageObject, Def, Val, Var = Value
}
