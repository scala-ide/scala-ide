package org.scalaide.ui.internal.editor.decorators.macros

import org.scalaide.core.extensions.SemanticHighlightingParticipant

/** This class is referenced through plugin.xml. */
class MacroHighlightingParticipant extends SemanticHighlightingParticipant(
    viewer => new MacroHighlightingPresenter(viewer))