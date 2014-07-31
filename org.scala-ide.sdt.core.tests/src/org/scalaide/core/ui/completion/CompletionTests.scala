package org.scalaide.core.ui.completion

import org.junit.ComparisonFailure
import org.scalaide.core.completion.ScalaCompletions
import org.scalaide.core.ui.CompilerSupport
import org.scalaide.core.ui.TextEditTests
import org.scalaide.util.internal.ScalaWordFinder

/**
 * This provides a test suite for the code completion functionality.
 * It can not only find out which completions exist, it also checks if the source
 * file after the insertion of a completion is correct.
 *
 * It can also handle Eclipse linked mode model. To depict such a model in the test
 * simply surround the identifiers that should be considered by the linked model
 * with [[ and ]]. The cursor is always represented by a ^.
 */
abstract class CompletionTests extends TextEditTests with CompilerSupport {

  /**
   * These are all the possible options that are considered by the test suite:
   *
   * @param completionToApply
   *        The completion as it is displayed in type notation. This could be
   *        `foo(Int)(Int): Int` for a function that returns an `Int` and has
   *        two parameter lists that both take an `Int` or `foo(): Int - a.b.Type`
   *        for a function that has a zero arg parameter list, returns `Int` and
   *        is located in `a.b.Type`.
   * @param enableOverwrite
   *        If `true` the completion overwrite feature is enabled
   * @param expectedCompletions
   *        All the completions that are _at least_ expected to be found. If `Nil`
   *        this option can be seen as not considered by the test.
   * @param expectedNumberOfCompletions
   *        The number of completions that are expected to be found. A negative
   *        value means that this option is not considered by the test.
   */
  case class Completion(
      completionToApply: String,
      enableOverwrite: Boolean = false,
      expectedCompletions: Seq[String] = Nil,
      expectedNumberOfCompletions: Int = -1)
        extends Operation {

    def execute() = withCompiler { compiler =>
      val unit = mkScalaCompilationUnit(doc.get())
      val src = unit.sourceFile()
      val completions = new ScalaCompletions().findCompletions(ScalaWordFinder.findWord(doc, caretOffset))(caretOffset, unit)(src, compiler)

      def findCompletion(rawCompletion: String) =
        if (!rawCompletion.contains("-"))
          completions.find(_.display == rawCompletion)
        else {
          val Array(completion, qualifier) = rawCompletion.split(" *- *")
          completions.find(c => c.display == completion && c.displayDetail == qualifier)
        }

      val completion = findCompletion(completionToApply)

      val missingCompletions = expectedCompletions.filter(c => findCompletion(c).isEmpty)
      if (missingCompletions.nonEmpty)
        throw new ComparisonFailure("There are expected completions that do not exist.", missingCompletions.mkString("\n"), "")

      def completionList = completions.sortBy(-_.relevance).map(_.display).mkString("\n")

      if (expectedNumberOfCompletions >= 0
          && completions.size != expectedNumberOfCompletions) {
        throw new ComparisonFailure(
            s"There were '$expectedNumberOfCompletions' completions expected, but '${completions.size}' found.",
            s"$expectedNumberOfCompletions completions expected\n\n<only number of expected completions provided>",
            s"${completions.size} completions found:\n\n$completionList")
      }

      completion.fold(
          throw new ComparisonFailure(
              s"The completion '$completionToApply' does not exist.", completionToApply, completionList)) {
        completion => completion.applyCompletionToDocument(doc, unit, caretOffset, enableOverwrite) foreach {
          case (cursorPos, applyLinkedMode) =>
            if (!applyLinkedMode)
              caretOffset = cursorPos
            else {
              val groups = completion.linkedModeGroups.sortBy(-_._1)
              val cursorOffset = groups.takeWhile(_._1 < cursorPos).size*4

              groups foreach {
                case (offset, length) =>
                  doc.replace(offset+length, 0, "]]")
                  doc.replace(offset, 0, "[[")
              }
              caretOffset = cursorPos + cursorOffset
            }
        }
      }
    }
  }
}