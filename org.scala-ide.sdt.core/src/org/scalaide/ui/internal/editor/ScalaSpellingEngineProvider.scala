package org.scalaide.ui.internal.editor

import scala.tools.eclipse.contribution.weaving.jdt.spellingengineprovider.ISpellingEngineProvider

import org.eclipse.core.runtime.AssertionFailedException
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.jdt.internal.ui.text.spelling.JavaSpellingProblem
import org.eclipse.jdt.internal.ui.text.spelling.SpellCheckIterator
import org.eclipse.jdt.internal.ui.text.spelling.SpellingEngine
import org.eclipse.jdt.internal.ui.text.spelling.engine.ISpellChecker
import org.eclipse.jdt.internal.ui.text.spelling.engine.ISpellEvent
import org.eclipse.jdt.internal.ui.text.spelling.engine.ISpellEventListener
import org.eclipse.jdt.ui.PreferenceConstants
import org.eclipse.jface.text.BadLocationException
import org.eclipse.jface.text.IDocument
import org.eclipse.jface.text.IRegion
import org.eclipse.jface.text.TextUtilities
import org.eclipse.ui.texteditor.spelling.ISpellingProblemCollector
import org.scalaide.core.internal.lexical.ScalaPartitions._

/**
 * Provides a replacement for {@link org.eclipse.jdt.internal.ui.text.spelling.JavaSpellingEngine} which is aware of Scala partitions,
 * and so can avoid spell checking in certain regions.
 */
class ScalaSpellingEngineProvider extends ISpellingEngineProvider {

  def getScalaSpellingEngine: SpellingEngine = new ScalaSpellingEngine

  class ScalaSpellingEngine extends SpellingEngine {

    def check(document: IDocument, regions: Array[IRegion], checker: ISpellChecker, collector: ISpellingProblemCollector, monitor: IProgressMonitor) {
      val spellCheckablePartitionTypes = getSpellCheckablePartitionTypes
      val listener = new SpellEventListener(collector, document)
      try {
        for {
          region <- regions
          partition <- TextUtilities.computePartitioning(document, SCALA_PARTITIONING, region.getOffset, region.getLength, false)
          if spellCheckablePartitionTypes contains partition.getType
        } {
          if (monitor != null && monitor.isCanceled || listener.isProblemsThresholdReached)
            return
          checker.execute(listener, new SpellCheckIterator(document, partition, checker.getLocale))
        }
      } catch {
        case _: BadLocationException | _: AssertionFailedException =>
        // Ignore: the document has been changed in another thread and will be checked again
      }
    }

    private def getSpellCheckablePartitionTypes = {
      val ignoreStrings = PreferenceConstants.getPreferenceStore.getBoolean(PreferenceConstants.SPELLING_IGNORE_JAVA_STRINGS)
      Set(SCALADOC, SCALA_MULTI_LINE_COMMENT, SCALA_SINGLE_LINE_COMMENT) ++
        (if (ignoreStrings) Set() else Set(SCALA_STRING, SCALA_MULTI_LINE_STRING))
    }

    private class SpellEventListener(collector: ISpellingProblemCollector, document: IDocument) extends ISpellEventListener {
      val problemsThreshold = PreferenceConstants.getPreferenceStore.getInt(PreferenceConstants.SPELLING_PROBLEMS_THRESHOLD)
      var problemCount = 0

      def handle(event: ISpellEvent) =
        if (problemCount < problemsThreshold) {
          problemCount += 1
          collector.accept(new JavaSpellingProblem(event, document))
        }

      def isProblemsThresholdReached() = problemCount >= problemsThreshold
    }

  }
}

