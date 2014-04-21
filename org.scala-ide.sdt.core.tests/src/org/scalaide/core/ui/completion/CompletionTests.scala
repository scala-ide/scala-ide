package org.scalaide.core.ui.completion

import org.scalaide.core.completion.ScalaCompletions
import org.scalaide.core.ui.CompilerSupport
import org.scalaide.core.ui.TextEditTests
import org.scalaide.util.internal.ScalaWordFinder

/**
 * This provides a test suite for the code completion functionality.
 * It can not only find out which completion exists, it also checks if the source
 * file after the insertion of a completion is inserted is correct.
 *
 * It can also handle Eclipse linked mode model. To depict such a model in the test
 * simply surround the identifiers that should be considered by the linked model
 * with [[ and ]]. The cursor is always represented by a ^.
 */
abstract class CompletionTests extends TextEditTests with CompilerSupport {

  case class Completion(
      completionToApply: String,
      enableOverwrite: Boolean = false,
      expectedCompletions: Seq[String] = Nil)
        extends Operation {

    def execute() = {
      val src = createLoadedSourceFile(doc.get())
      val r = ScalaWordFinder.findWord(doc, caretOffset)

      val unit = compilationUnitOfSourceFile(src)
      val completions = new ScalaCompletions().findCompletions(r)(caretOffset, unit)(src, compiler)
      val completion = completions.find(_.display == completionToApply)

      val missingCompletions = expectedCompletions.filter(c => !completions.exists(_.display == c))
      if (missingCompletions.nonEmpty)
        throw new IllegalArgumentException(s"the following completions do not exist:\n\t${missingCompletions.mkString("\t\n")}")

      completion.fold(throw new IllegalArgumentException(s"the completion '$completionToApply' does not exist")) {
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