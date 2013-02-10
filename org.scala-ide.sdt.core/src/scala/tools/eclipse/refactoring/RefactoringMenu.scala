/*
 * Copyright (c) 2011 Fabian Steeg. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Scala License which accompanies this distribution, and
 * is available at http://www.scala-lang.org/node/146
 */
package scala.tools.eclipse.refactoring

import org.eclipse.ui.handlers.IHandlerService
import org.eclipse.ui.commands.{ ICommandService, IHandler }
import org.eclipse.ui.part.EditorPart
import org.eclipse.core.commands.Command
import org.eclipse.jface.action.{ Action, IAction, IMenuManager, IMenuListener, ActionContributionItem }
import org.eclipse.jdt.internal.ui.javaeditor.JavaEditor
import org.eclipse.jdt.internal.ui.actions.JDTQuickMenuCreator
import org.eclipse.jface.action.Separator

/**
 * Unfortunately, it seems we cannot simply define a context menu on the Scala source editor via
 * plugin.xml for the refactorings, as the editor is a CompilationUnitEditor, which controls our
 * editor's context menu. Stuff we add in plugin.xml is programmatically changed internally in
 * CompilationUnitEditor. So instead, we add our refactorings to the context and quick menus
 * programmatically after CompilationUnitEditor, using the commands declared in plugin.xml.
 *
 * @author Fabian Steeg <fsteeg@gmail.com>
 */
protected[eclipse] object RefactoringMenu {

  private object Id extends Enumeration {
    val QuickMenu = Value("scala.tools.eclipse.refactoring.commands.quickMenu")
    val ContextMenu = Value("org.eclipse.jdt.ui.refactoring.menu")
    val CommandsCategory = Value("scala.tools.eclipse.refactoring.commands.refactoring")
    val CommandsMethodSignatureCategory = Value("scala.tools.eclipse.refactoring.commands.methodsignature")
    type Id = Value; implicit def toString(id: Id.Value) = id.toString
  }

  def fillContextMenu(menu: IMenuManager, editor: EditorPart): Unit = {
    val refactorSubmenu = Option(menu.findMenuUsingPath(Id.ContextMenu))
    /* Add actions in a listener to refill every time RefactorActionGroup's listener empties it for us: */
    refactorSubmenu foreach {
      _.addMenuListener(new IMenuListener() {
        def menuAboutToShow(menu: IMenuManager): Unit = fillFromPluginXml(menu, editor, true)
      })
    }
  }

  def fillQuickMenu(editor: JavaEditor): Unit = {
    val handler = new JDTQuickMenuCreator(editor) {
      protected def fillMenu(menu: IMenuManager): Unit = fillFromPluginXml(menu, editor, false)
    }.createHandler
    /* Activating our handler here enables the binding specified in plugin.xml, but clears the
     * binding for the context submenu, so the binding does not show up in the context menu. To
     * show the binding to the user, we add the quick menu command to the context submenu, but
     * not to the quick menu (see fillFromPluginXml below). */
    handlerService(editor).activateHandler(Id.QuickMenu, handler)
  }

  private def fillFromPluginXml(menu: IMenuManager, editor: EditorPart, all: Boolean): Unit = {

    menu.removeAll

    val service = commandService(editor)
    val categories = {
      val refactoringCategory = service.getCategory(Id.CommandsCategory)
      val methodSignatureCategory = service.getCategory(Id.CommandsMethodSignatureCategory)
      List(refactoringCategory, methodSignatureCategory)
    }

    for(category <- categories)
      menu.add(new Separator(category.getId))

    for (command <- refactoringCommandsDefinedInPluginXml)
      menu.appendToGroup(command.getCategory.getId, wrapped(command))

    def refactoringCommandsDefinedInPluginXml = {
      val refactoringCommands = service.getDefinedCommands.filter((command: Command) =>
        (categories contains command.getCategory) && (all || command.getId != Id.QuickMenu.toString))
      refactoringCommands
    }

    def wrapped(command: Command) = new Action {
      setActionDefinitionId(command.getId) // adds the key binding defined for command in plugin.xml
      setText(command.getName)
      setEnabled(command.isEnabled)
      override def run(): Unit = handlerService(editor).executeCommand(command.getId, null)
    }

  }

  private def commandService(editor: EditorPart) = service(classOf[ICommandService], editor)
  private def handlerService(editor: EditorPart) = service(classOf[IHandlerService], editor)
  private def service[T](t: Class[T], e: EditorPart) = e.getEditorSite.getService(t).asInstanceOf[T]

}
