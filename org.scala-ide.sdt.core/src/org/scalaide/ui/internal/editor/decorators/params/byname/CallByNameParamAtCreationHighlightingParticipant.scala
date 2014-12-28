package org.scalaide.ui.internal.editor.decorators.params.byname

import org.scalaide.core.extensions.SemanticHighlightingParticipant

class CallByNameParamAtCreationHighlightingParticipant extends
  SemanticHighlightingParticipant(viewer => new CallByNameParamAtCreationPresenter(viewer)) {

}
