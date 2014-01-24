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

/** Context related to the invocation of the Completion.
 *  Can be extended with more context as needed in future
 *
 *  @param contextType The type of completion - e.g. Import, method apply
 *  */
case class CompletionContext(
  contextType: CompletionContext.ContextType
)

object CompletionContext {
  trait ContextType
  case object DefaultContext extends ContextType
  case object ApplyContext extends ContextType
  case object NewContext extends ContextType
  case object ImportContext extends ContextType
}


/** A completion proposal coming from the Scala compiler. This
 *  class holds together data about completion proposals.
 *
 *  This class is independent of both the Scala compiler (does not
 *  know about Symbols and Types), and the UI elements used to
 *  display it to the user.
 *
 *  @note Parameter names are retrieved lazily, since the operation is potentially long-running.
 *  @see  ticket #1001560
 */
case class CompletionProposal(
  kind: MemberKind.Value,
  context: CompletionContext,
  startPos: Int,             // position where the 'completion' string should be inserted
  completion: String,        // the string to be inserted in the document
  display: String,           // the display string in the completion list
  displayDetail: String,     // additional details to be display in the completion list (like package for a class)
  relevance: Int,
  isJava: Boolean,
  getParamNames: () => List[List[String]], // parameter names (excluding any implicit parameter sections)
  paramTypes: List[List[String]],          // parameter types matching parameter names (excluding implicit parameter sections)
  fullyQualifiedName: String, // for Class, Trait, Type, Objects: the fully qualified name
  needImport: Boolean        // for Class, Trait, Type, Objects: import statement has to be added
) {

  /** Return the tooltip displayed once a completion has been activated. */
  def tooltip: String = {
    val contextInfo = for {
      (names, tpes) <- getParamNames().zip(paramTypes)
    } yield for { (name, tpe) <- names.zip(tpes) } yield "%s: %s".format(name, tpe)

    contextInfo.map(_.mkString("(", ", ", ")")).mkString("")
  }
}

/** The kind of a completion proposal. */
object MemberKind extends Enumeration {
  val Class, Trait, Type, Object, Package, PackageObject, Def, Val, Var = Value
}
