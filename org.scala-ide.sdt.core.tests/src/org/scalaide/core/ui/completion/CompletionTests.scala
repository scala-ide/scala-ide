package org.scalaide.core.ui.completion

import org.junit.ComparisonFailure
import org.scalaide.core.completion.ScalaCompletions
import org.scalaide.core.ui.CompilerSupport
import org.scalaide.core.ui.TextEditTests
import org.scalaide.util.ScalaWordFinder
import org.scalaide.core.completion.CompletionProposal

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
   * @param respectOrderOfExpectedCompletions
   *        If `true` then expected completions will be additionally checked to match
   *        proposal order
   */
  case class Completion(
      completionToApply: String,
      enableOverwrite: Boolean = false,
      expectedCompletions: Seq[String] = Nil,
      expectedNumberOfCompletions: Int = -1,
      respectOrderOfExpectedCompletions: Boolean = false)
        extends Operation {

    override def execute() = {
      val unit = mkScalaCompilationUnit(doc.get())

      val src = unit.sourceMap(doc.get.toCharArray()).sourceFile

      // first, 'open' the file by telling the compiler to load it
      unit.scalaProject.presentationCompiler { compiler =>
        compiler.askReload(List(unit)).get

        compiler.askLoadedTyped(src, false).get
      }

      val completions = new ScalaCompletions()
        .findCompletions(ScalaWordFinder.findWord(doc, caretOffset), caretOffset, unit)
        .sorted(CompletionProposalOrdering).to[IndexedSeq]

      def completionList = completions.map(_.display).mkString("\n")

      def indexOfCompletion(rawCompletion: String) =
        if (!rawCompletion.contains("-"))
          completions.indexWhere(_.display == rawCompletion)
        else {
          val Array(completion, qualifier) = rawCompletion.split(" *- *")
          completions.indexWhere(c => c.display == completion && c.displayDetail == qualifier)
        }

      def findCompletion(rawCompletion: String): Option[CompletionProposal] =
        indexOfCompletion(rawCompletion) match {
          case -1 => None
          case index => Some(completions.apply(index))
        }

      val completion = findCompletion(completionToApply)

      val missingCompletions = expectedCompletions.filter(c => findCompletion(c).isEmpty)
      if (missingCompletions.nonEmpty)
        throw new ComparisonFailure("There are expected completions that do not exist.", missingCompletions.mkString("\n"), completionList)

      if (expectedCompletions.nonEmpty && respectOrderOfExpectedCompletions) {
        val indexes = expectedCompletions.map(c => indexOfCompletion(c))
        val sorted = indexes.zip(indexes.tail).forall(i => i._2 - i._1 == 1)
        if (!sorted)
          throw new ComparisonFailure(s"The order of completition is wrong", expectedCompletions.mkString("\n"), completionList)
        if (indexes.head != 0)
          throw new ComparisonFailure(s"The completition list should start from", expectedCompletions.head, completionList)
      }

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
            caretOffset =
              if (!applyLinkedMode)
                cursorPos
              else
                applyLinkedModel(doc, cursorPos, completion.linkedModeGroups)
        }
      }

      // unload given unit, otherwise the compiler will keep type-checking it together with the other tests
      unit.scalaProject.presentationCompiler(_.discardCompilationUnit(unit))
    }
  }
}

/**
 * This provides a default ordering for completion proposal. The implementation is
 * based on CompletionProposalComparator that is used in JDT to sort completions
 */
private object CompletionProposalOrdering extends Ordering[CompletionProposal] {

  def compare(a: CompletionProposal, b: CompletionProposal) =
    b.relevance - a.relevance match {
      case 0 => a.display.compareToIgnoreCase(b.display)
      case diff => diff
    }

}
