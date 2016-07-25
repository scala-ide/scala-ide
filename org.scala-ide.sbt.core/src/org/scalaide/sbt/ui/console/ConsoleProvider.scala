package org.scalaide.sbt.ui.console

import java.io.File
import org.eclipse.ui.PlatformUI
import org.eclipse.ui.console.ConsolePlugin
import org.eclipse.ui.console.IConsole
import org.eclipse.ui.console.IConsoleConstants
import org.eclipse.ui.console.IConsoleView
import org.eclipse.ui.console.MessageConsole

object ConsoleProvider {

  def apply(buildRoot: File): MessageConsole = {
    val console = obtainConsole(buildRoot.getCanonicalPath())
    showConsole(console)
    console
  }

  private def obtainConsole(name: String): MessageConsole = {
    val plugin = ConsolePlugin.getDefault
    val manager = plugin.getConsoleManager()
    val consoles = manager.getConsoles
    consoles.find(_.getName == name).map(_.asInstanceOf[MessageConsole]) getOrElse {
      // no console found, so create a new one
      val console = new MessageConsole(name, null)
      manager.addConsoles(Array[IConsole](console))
      console
    }
  }

  private def showConsole(console: MessageConsole): Unit = {
    for {
      window <- Option(PlatformUI.getWorkbench.getActiveWorkbenchWindow)
      page <- Option(window.getActivePage)
      id = IConsoleConstants.ID_CONSOLE_VIEW
      view <- Option(page.showView(id))
    } (view.asInstanceOf[IConsoleView]).display(console)
  }
}