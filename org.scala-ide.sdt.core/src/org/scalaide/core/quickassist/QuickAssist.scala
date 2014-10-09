package org.scalaide.core.quickassist

import org.eclipse.jface.text.source.Annotation
import org.scalaide.core.compiler.InteractiveCompilationUnit

/**
 * Represents the quick assist feature of the Scala editor. A quick assist is a
 * feature that needs to be invoked explicitly inside of the Scala editor by
 * pressing a key combination and shows proposals that can be applied to the
 * content of the editor.
 */
trait QuickAssist {

  /**
   * This method is called when the quick assist is asked for help. All returned
   * proposals are shown to users inside of the Scala editor.
   *
   * @param ctx
   *        contains various information that is available at the time when the
   *        quick assist is invoked.
   */
  def compute(ctx: InvocationContext): Seq[BasicCompletionProposal]
}

/**
 * Contains various information that is available at the time when quick assists
 * are invoked.
 *
 * @param icu
 *        Represents the source file on which the editor, where quick assists
 *        are called on, operates on.
 * @param selectionStart
 *        The position of the cursor at the time of the quick assist invocation.
 * @param selectionLength
 *        The length of the selection at the time of the quick assist
 *        invocation.
 * @param problemLocations
 *        All locations of existing annotations at the time of the quick assist
 *        invocation. For more information see the documentation of
 *        [[AssistLocation]].
 */
final case class InvocationContext(
  icu: InteractiveCompilationUnit,
  selectionStart: Int,
  selectionLength: Int,
  problemLocations: Seq[AssistLocation]
)

/**
 * Contains various information to a marker that is shown to users in the editor
 * area.
 *
 * @param offset
 *        The start position of `annotation`.
 * @param length
 *        The length of text that is marked with `annotation`
 * @param annotation
 *        Represents information that belongs to the text between `offset` and
 *        `offset+length` in the editor area. This is either a subclass of
 *        [[org.scalaide.ui.editor.ScalaEditorAnnotation]] or
 *        [[org.eclipse.jdt.internal.ui.javaeditor.IJavaAnnotation]].
 */
final case class AssistLocation(
  offset: Int,
  length: Int,
  annotation: Annotation
)
