package org.scalaide.ui.internal.editor.decorators.implicits

import org.scalaide.core.extensions.SemanticHighlightingParticipant

/** This class is referenced through plugin.xml. */
class ImplicitHighlightingParticipant extends SemanticHighlightingParticipant(
    viewer => new ImplicitHighlightingPresenter(viewer))