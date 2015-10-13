package org.scalaide.core.completion

import scala.tools.refactoring.common.TextChange
import scala.tools.refactoring.implementations.AddImportStatement
import org.eclipse.jface.text.IDocument
import org.eclipse.jface.text.TextSelection
import org.scalaide.util.ScalaWordFinder
import org.scalaide.util.internal.eclipse.TextEditUtils
import org.scalaide.core.compiler.InteractiveCompilationUnit
import org.scalaide.core.internal.ScalaPlugin
import org.scalaide.ui.internal.preferences.EditorPreferencePage
import org.eclipse.jface.internal.text.html.BrowserInput

object CompletionContext {
  trait ContextType
  case object DefaultContext extends ContextType
  case object ApplyContext extends ContextType
  case object NewContext extends ContextType
  case object ImportContext extends ContextType
  case object InfixMethodContext extends ContextType
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
  context: CompletionContext.ContextType,
  startPos: Int,             // position where the 'completion' string should be inserted
  completion: String,        // the string to be inserted in the document
  display: String,           // the display string in the completion list
  displayDetail: String,     // additional details to be display in the completion list (like package for a class)
  relevance: Int,
  isJava: Boolean,
  getParamNames: () => List[List[String]], // parameter names (excluding any implicit parameter sections)
  paramTypes: List[List[String]],          // parameter types matching parameter names (excluding implicit parameter sections)
  fullyQualifiedName: String, // for Class, Trait, Type, Objects: the fully qualified name
  needImport: Boolean,        // for Class, Trait, Type, Objects: import statement has to be added
  documentation: () => Option[BrowserInput]  // on-demand generated documentation HTML.
) {

  /** `getParamNames` is expensive, save this result once computed.
   *
   *  @note This field is lazy to avoid unnecessary computation.
   */
  private lazy val explicitParamNames = getParamNames()

  /** Return the tooltip displayed once a completion has been activated. */
  def tooltip: String = {
    val contextInfo = for {
      (names, tpes) <- getParamNames().zip(paramTypes)
    } yield for { (name, tpe) <- names.zip(tpes) } yield "%s: %s".format(name, tpe)

    contextInfo.map(_.mkString("(", ", ", ")")).mkString("")
  }

  /** The string that will be inserted in the document if this proposal is chosen.
   *  By default, it consists of the method name, followed by all explicit parameter sections,
   *  and inside each section the parameter names, delimited by commas. If `overwrite`
   *  is on, it won't add parameter names
   *
   *  @note It triggers the potentially expensive `getParameterNames` operation.
   */
  private def completionString(overwrite: Boolean, doParamsProbablyExist: => Boolean): String = {
    if ((explicitParamNames.isEmpty || overwrite) && doParamsProbablyExist)
      completion
    else
      completionData.completionString
  }

  private case class CompletionData(completionString: String, linkedModeGroups: IndexedSeq[(Int, Int)])

  /** Compute the full completion string and linked-mode groups.
   *
   *  The default completion string consists of the method name (`completion`) followed
   *  by all explicit argument lists, pre-filled with the name of the corresponding
   *  parameter at that position.
   *
   *  Special cases:
   *    - Java getters. A Java method with no parameters will omit the empty param list
   *    - one-argument higher-order functions. The parameter is surrounded by braces instead
   *      of parenthesis, and a dummy function `x => ???` is placed instead of the parameter
   *      name.
   *    - non-argument higher order functions. The parameter is surrounded by braces and
   *      a dummy function `() => ???` is placed instead of the parameter name
   */
  private lazy val completionData = {
    if (context == CompletionContext.ImportContext
        || (isJava && explicitParamNames == List(Nil) && completion.startsWith("get")))
      CompletionData(completion, IndexedSeq.empty)
    else {
      val offset = startPos + completion.length
      explicitParamNames.zip(paramTypes) match {
        case List((List(_), List(SimpleFunctionType("()", _)))) if shouldInsertLambda =>
          CompletionData(s"""$completion { () => ??? }""", IndexedSeq((offset + 9, 3)))

        case List((List(_), List(SimpleFunctionType(x, _)))) if shouldInsertLambda && !x.contains(",") =>
          CompletionData(s"""$completion { x => ??? }""", IndexedSeq((offset + 3, 1), (offset + 8, 3)))

        case _ =>
          val buffer = new StringBuffer(completion)

          for (section <- explicitParamNames)
            buffer.append(section.mkString("(", ", ", ")"))
          CompletionData(buffer.toString, explicitParamsLinkedGroups)
      }
    }
  }

  private val shouldInsertLambda =
    (context == CompletionContext.InfixMethodContext
        || ScalaPlugin().getPreferenceStore.getBoolean(EditorPreferencePage.P_ENABLE_HOF_COMPLETION))

  /** Match a simple function of form {{{A => B}}}
   *  where neither A nor B are function types.
   */
  private case object SimpleFunctionType {
    def unapply(fun: String): Option[(String, String)] = {
      fun.split("=>") match {
        case Array(a, b) if !a.isEmpty => Some((a.trim, b.trim))
        case _                         => None
      }
    }
  }

  /** Return the pair of offset -> length for linked-mode groups corresponding to
   *  the completion string. Offsets are relative to the document.
   *
   *  For example, for a completion string like {{{meth(a, bar)}}} this method would
   *  return {{{IndexedSeq((offset + 1) -> 1, (offset + 4) -> 3}}}, where offset is
   *  the index at the beginning of the parameter list.
   */
  def linkedModeGroups: IndexedSeq[(Int, Int)] =
    completionData.linkedModeGroups

  /** Non GUI logic that calculates the `(offset, length)` groups for the `LinkedModeModel` */
  private def explicitParamsLinkedGroups: IndexedSeq[(Int, Int)] = {
    var groups = IndexedSeq[(Int, Int)]()
    var offset = startPos + completion.length
    for (section <- explicitParamNames) {
      offset += 1 // open parenthesis
      var idx = 0 // the index of the current argument
      for (proposal <- section) {
        val positionOffset = offset + 2 * idx // each argument is followed by ", "
        val positionLength = proposal.length
        offset += positionLength

        groups :+= positionOffset -> positionLength
        idx += 1
      }
      offset += 1 + 2 * (idx - 1) // close parenthesis around section (and the last argument isn't followed by comma and space)
    }
    groups
  }

  /**
   * This is a heuristic that only needs to be called when 'completion overwrite' is enabled.
   * It checks if an expression _probably_ has already its parameter list. Without
   * this heuristic the IDE would in cases like
   * {{{
   *   List(1).map^
   * }}}
   * not know if the parameter list should be added or not.
   *
   * Because this is a heuristic it will only work in some cases, but hopefully in
   * the most important ones.
   */
  private def doParamsProbablyExist(d: IDocument, offset: Int): Boolean = {
    def terminatesExprProbably(c: Char) =
      c.toString matches "[a-zA-Z_;)},.\n]"

    val terminationChar = Iterator
      .from(offset)
      // prevent BadLocationException at end of file
      .filter(c => if (c < d.getLength()) true else return false)
      .map(d.getChar)
      .dropWhile(Character.isJavaIdentifierPart)
      .dropWhile(" \t" contains _)
      .next()

    !terminatesExprProbably(terminationChar)
  }

  /**
   * Applies the actual completion to the document, while considering if completion
   * overwrite is enabled.
   *
   * This method is UI independent and returns the position of the cursor after
   * the completion is inserted and a boolean value that describes if a linked
   * mode model should be created.
   */
  def applyCompletionToDocument(
      d: IDocument,
      scalaSourceFile: InteractiveCompilationUnit,
      offset: Int,
      overwrite: Boolean): Option[(Int, Boolean)] = {
    // lazy val necessary because the operation may be unnecessary and the
    // underlying document changes during completion insertion
    lazy val paramsProbablyExists = doParamsProbablyExist(d, offset)
    val completionFullString = completionString(overwrite, paramsProbablyExists)

    scalaSourceFile.withSourceFile { (sourceFile, compiler) =>
      val endPos = if (overwrite) startPos + ScalaWordFinder.identLenAtOffset(d, offset) else offset
      val completedIdent = TextChange(sourceFile, startPos, endPos, completionFullString)

      val importStmt =
        if (!needImport)
          Nil
        else {
          import org.scalaide.core.compiler.IScalaPresentationCompiler.Implicits._
          compiler.asyncExec {
            val refactoring = new AddImportStatement { override val global = compiler }
            refactoring.addImport(scalaSourceFile.file, fullyQualifiedName)
          }.getOrElse(Nil)()
        }

      val applyLinkedMode =
        (context != CompletionContext.ImportContext
        && (!overwrite || !paramsProbablyExists)
        && explicitParamNames.flatten.nonEmpty)

      // Apply the two changes in one step, if done separately we would need an
      // another `waitLoadedType` to update the positions for the refactoring
      // to work properly.
      val selection = TextEditUtils.applyChangesToFile(
        d, new TextSelection(d, endPos, 0), scalaSourceFile.file, completedIdent +: importStmt)

      if (applyLinkedMode)
        Some((startPos + completionFullString.length(), applyLinkedMode))
      else
        selection map (_.getOffset() -> applyLinkedMode)
    }.flatten
  }
}

/** The kind of a completion proposal. */
object MemberKind extends Enumeration {
  val Class, Trait, Type, Object, Package, PackageObject, Def, Val, Var = Value
}
