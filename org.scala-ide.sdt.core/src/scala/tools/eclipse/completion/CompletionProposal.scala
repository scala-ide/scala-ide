package scala.tools.eclipse.completion

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
  tooltip: String,           // tooltop info showed after a completion has been selected
  additionalInfo: String,    // info displayed on the right of the current completion selection
  relevance: Int,
  hasArgs: Boolean,
  isJava: Boolean
)

/** The kind of a completion proposal. */
object MemberKind extends Enumeration {
  val Class, Trait, Type, Object, Package, PackageObject, Def, Val, Var = Value
}
