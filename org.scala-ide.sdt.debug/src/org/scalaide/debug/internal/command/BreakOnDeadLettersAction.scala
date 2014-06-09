package org.scalaide.debug.internal.command

import org.eclipse.core.commands.AbstractHandler
import org.scalaide.ui.internal.actions.AbstractToggleHandler
import org.scalaide.debug.internal.async.BreakOnDeadLetters

class BreakOnDeadLettersAction extends AbstractToggleHandler("org.scala-ide.sdt.debug.stopOnDeadLetters", BreakOnDeadLetters.preferenceID) {

}