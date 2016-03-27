package org.scalaide.core.ui.completion

import org.junit.ComparisonFailure
import org.scalaide.core.completion.ScalaCompletions
import org.scalaide.core.ui.CompilerSupport
import org.scalaide.core.ui.TextEditTests
import org.scalaide.util.ScalaWordFinder
import org.scalaide.core.completion.CompletionProposal
import org.scalaide.core.internal.jdt.model.ScalaCompilationUnit
import org.scalaide.core.internal.jdt.search.ScalaIndexBuilder
import org.scalaide.core.testsetup.BlockingProgressMonitor
import org.eclipse.core.resources.IResource
import org.scalaide.core.testsetup.SDTTestUtils
import org.eclipse.core.runtime.NullProgressMonitor
import scala.reflect.internal.util.SourceFile
import org.eclipse.core.resources.IncrementalProjectBuilder

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
   *
   *        Use an empty string if you only want to check the suggested completions.
   *        (Making this argument an `Option` might be cleaner, but then all tests
   *        that use this parameter would have to wrap it in `Some(...)`, unless
   *        we declare apply functions, which has its own drawbacks.)
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
      completionToApply: String = "",
      enableOverwrite: Boolean = false,
      expectedCompletions: Seq[String] = Nil,
      expectedNumberOfCompletions: Int = -1,
      respectOrderOfExpectedCompletions: Boolean = false,
      sourcesToPreload: Seq[String] = Nil)
        extends Operation {

    override def execute() = {
      def basename(path: String) = {
        path.split("/").last
      }

      def lastIndexBuilderTraversals: Seq[String] = withCompiler {
        case indexBuilder: ScalaIndexBuilder => indexBuilder.lastIndexBuilderTraversals
      }.get

      def loadCompilationUnit(input: String) = {
        val unit = mkScalaCompilationUnit(input)
        unit
      }

      val preloadedUnits = sourcesToPreload.map(loadCompilationUnit)
      val unit = loadCompilationUnit(source)
      val filesToIndex = preloadedUnits.map(_.file.canonicalPath).toSet

      def filesNotYetIndexed: Set[String] = {
        filesToIndex.diff(lastIndexBuilderTraversals.toSet)
      }

      SDTTestUtils.buildWorkspace()

      while(filesNotYetIndexed.nonEmpty) {
        println(s"Waiting for ${filesNotYetIndexed.map(basename)} to be indexed (already indexed ${lastIndexBuilderTraversals.map(basename)})...")
        Thread.sleep(250)
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

      if (completionToApply.nonEmpty) {
        val completion = findCompletion(completionToApply)

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

      }

      // Clean up so that subsequent tests find an empty environment:
      val unitsToDelete = (unit +: preloadedUnits)
      val sourceFilesToDelete = unitsToDelete.map(_.lastSourceMap().sourceFile)

      project.presentationCompiler(_.askFilesDeleted(sourceFilesToDelete.toList).get)
      unitsToDelete.foreach(_.resource().delete(true, new NullProgressMonitor()))
    }
  }

  implicit class CompletionTestExpectationOps(source: String) {
    def expectCompletions(completions: Seq[String]) = {
      source.isNotModified.after(Completion(expectedCompletions = completions, sourcesToPreload = sourcesToPreload))
    }

    def sourcesToPreload: Seq[String] = Nil
  }

  implicit class CompletionTestSetupOps(sourceToPreload: String) {
    def andInSeparateFile(otherSource: String) = {
      new CompletionTestExpectationOps(otherSource) {
        override def sourcesToPreload = Seq(sourceToPreload)
      }
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
