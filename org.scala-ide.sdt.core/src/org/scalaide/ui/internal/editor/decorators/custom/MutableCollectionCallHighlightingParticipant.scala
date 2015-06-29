package org.scalaide.ui.internal.editor.decorators.custom

import org.eclipse.jface.text.source.ISourceViewer
import org.scalaide.core.extensions.SemanticHighlightingParticipant
import org.scalaide.ui.internal.editor.decorators.SemanticAction

/**
 * Highlights calls of all methods on `collection.mutable.Traversable` and it's descendants.
 */
class MutableCollectionCallHighlightingParticipant
  extends SemanticHighlightingParticipant(MutableCollectionCallHighlightingParticipant.action)

object MutableCollectionCallHighlightingParticipant {

  private val traverser = AllMethodsTraverserDef(
    message = name => s"Method call on a mutable collection: '$name'",
    typeDefinition = TraverserDef.TypeDefinition("scala" :: "collection" :: "mutable" :: Nil, "Traversable"))

  def action(viewer: ISourceViewer): SemanticAction =
    new CustomSemanticAction(Seq(traverser), viewer, MutableCollectionCallAnnotation.ID)

}

object MutableCollectionCallAnnotation {
  final val ID = "scala.tools.eclipse.semantichighlighting.custom.mutableCollectionsCallAnnotation"
}