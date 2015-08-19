package org.scalaide.debug.internal.command

import org.scalaide.debug.internal.async.BreakOnDeadLetters

import org.scalaide.ui.internal.actions.AbstractToggleHandler

/**
 * Toggles break on dead letters functionality of the async debugger.
 */
class BreakOnDeadLettersAction extends AbstractToggleHandler(
    "org.scala-ide.sdt.debug.stopOnDeadLetters",
    BreakOnDeadLetters.PreferenceId)
