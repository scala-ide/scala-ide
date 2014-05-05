/*
 * Copyright (c) 2011 Fabian Steeg. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Scala License which accompanies this distribution, and
 * is available at http://www.scala-lang.org/node/146
 */
package org.scalaide.refactoring.internal

import org.eclipse.core.commands.Command
import org.eclipse.jdt.internal.ui.actions.JDTQuickMenuCreator
import org.eclipse.jdt.internal.ui.javaeditor.JavaEditor
import org.eclipse.jface.action.Action
import org.eclipse.jface.action.IMenuListener
import org.eclipse.jface.action.IMenuManager
import org.eclipse.jface.action.Separator
import org.eclipse.ui.commands.ICommandService
import org.eclipse.ui.handlers.IHandlerService
import org.eclipse.ui.part.EditorPart

/**
 * Unfortunately, it seems we cannot simply define a context menu on the Scala source editor via
 * plugin.xml for the refactorings, as the editor is a CompilationUnitEditor, which controls our
 * editor's context menu. Stuff we add in plugin.xml is programmatically changed internally in
 * CompilationUnitEditor. So instead, we add our refactorings to the context and quick menus
 * programmatically after CompilationUnitEditor, using the commands declared in plugin.xml.
 *
 */
protected[scalaide] object RefactoringMenu {

  private object Id {
    val QuickMenu = "org.scalaide.ui.menu.quickMenu"
    val ContextMenu = "org.eclipse.jdt.ui.refactoring.menu"
    val CommandsCategory = "org.scalaide.ui.menu.refactoring"
    val CommandsMethodSignatureCategory = "org.scalaide.ui.menu.refactoring.methodsignature"
  }

  def fillContextMenu(menu: IMenuManager, editor: EditorPart): Unit = {
    val refactorSubmenu = Option(menu.findMenuUsingPath(Id.ContextMenu))
    /* Add actions in a listener to refill every time RefactorActionGroup's listener empties it for us: */
    refactorSubmenu foreach {
      _.addMenuListener(new IMenuListener() {
        def menuAboutToShow(menu: IMenuManager): Unit = fillFromPluginXml(menu, editor)
      })
    }
  }

  def fillQuickMenu(editor: JavaEditor): Unit = {
    val handler = new JDTQuickMenuCreator(editor) {
      protected def fillMenu(menu: IMenuManager): Unit = fillFromPluginXml(menu, editor)
    }.createHandler

    handlerService(editor).activateHandler(Id.QuickMenu, handler)
  }

  private def fillFromPluginXml(menu: IMenuManager, editor: EditorPart): Unit = {

    menu.removeAll

    val service = commandService(editor)
    val categories = {
      val refactoringCategory = service.getCategory(Id.CommandsCategory)
      val methodSignatureCategory = service.getCategory(Id.CommandsMethodSignatureCategory)
      List(refactoringCategory, methodSignatureCategory)
    }

    val refactoringCommandsDefinedInPluginXml =
      service.getDefinedCommands filter {
        command => categories contains command.getCategory
      }

    for(category <- categories)
      menu.add(new Separator(category.getId))

    for (command <- refactoringCommandsDefinedInPluginXml)
      menu.appendToGroup(command.getCategory.getId, wrapped(command))

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
