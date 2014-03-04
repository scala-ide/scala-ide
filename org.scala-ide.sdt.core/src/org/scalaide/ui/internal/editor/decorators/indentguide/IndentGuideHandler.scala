package org.scalaide.ui.internal.editor.decorators.indentguide

import org.scalaide.ui.internal.actions.AbstractToggleHandler
import org.scalaide.ui.internal.preferences.EditorPreferencePage

class IndentGuideHandler extends AbstractToggleHandler(
    "org.scalaide.core.handler.indentGuide",
    EditorPreferencePage.INDENT_GUIDE_ENABLE)
