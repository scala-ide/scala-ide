package org.scalaide.ui.internal.editor.decorators.bynameparams

import org.scalaide.core.extensions.SemanticHighlightingParticipant

class CallByNameParamAtCreationHighlightingParticipant extends
  SemanticHighlightingParticipant(viewer => new CallByNameParamAtCreationPresenter(viewer)) {

}
