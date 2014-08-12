package org.scalaide.core.extensions

import org.eclipse.jdt.internal.ui.javaeditor.JavaSourceViewer
import org.scalaide.ui.internal.editor.decorators.SemanticAction

/**
 * Marker interface that needs to be subclasses by extensions that need to be
 * hooked into the samantic highlighting process.
 *
 * Subclasses of this interface can be instantiated by the IDE an unlimited
 * amount of times, therefore subclasses should not contain any state.
 *
 * `participant` needs to create a [[org.scalaide.ui.internal.editor.decorators.SemanticAction]]
 * from a given [[org.eclipse.jdt.internal.ui.javaeditor.JavaSourceViewer]],
 * which will be passed by the IDE. The source viewer will always be the
 * source viewer of the current active editor.
 *
 * There is no guarantee when this participant is invoked or if it is invoked
 * at all. But it can be expected that invocation happens during the
 * reconciliation phase of the editor. Note, that the IDE is free to disable a
 * participant if its implementation throws errors.
 */
abstract class SemanticHighlightingParticipant(val participant: JavaSourceViewer => SemanticAction)