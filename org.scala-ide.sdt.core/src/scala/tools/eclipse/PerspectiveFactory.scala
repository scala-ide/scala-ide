/*
 * Copyright 2005-2010 LAMP/EPFL
 * @author Sean McDirmid
 */
// $Id$

package scala.tools.eclipse

import org.eclipse.debug.ui.IDebugUIConstants
import org.eclipse.jdt.ui.JavaUI
import org.eclipse.ui.{ IPageLayout, IPerspectiveFactory }
import org.eclipse.ui.console.IConsoleConstants
import org.eclipse.ui.navigator.resources.ProjectExplorer

class PerspectiveFactory extends IPerspectiveFactory {
  def createInitialLayout(layout : IPageLayout) = {
    createFolders(layout)
    addShortcuts(layout)
    layout.addActionSet(IDebugUIConstants.LAUNCH_ACTION_SET)
    layout.addActionSet(JavaUI.ID_ACTION_SET)
  }
  private def addShortcuts(layout : IPageLayout) = {
    layout.addNewWizardShortcut(ScalaPlugin.plugin.projectWizId)
    layout.addNewWizardShortcut(ScalaPlugin.plugin.netProjectWizId)
    layout.addNewWizardShortcut("org.eclipse.jdt.ui.wizards.NewPackageCreationWizard")
    layout.addNewWizardShortcut(ScalaPlugin.plugin.classWizId)
    layout.addNewWizardShortcut(ScalaPlugin.plugin.traitWizId)
    layout.addNewWizardShortcut(ScalaPlugin.plugin.objectWizId)
    layout.addNewWizardShortcut(ScalaPlugin.plugin.packageObjectWizId)
    layout.addNewWizardShortcut(ScalaPlugin.plugin.applicationWizId)
    layout.addNewWizardShortcut("org.eclipse.ui.wizards.new.folder")
    layout.addNewWizardShortcut("org.eclipse.ui.wizards.new.file")

    layout.addShowViewShortcut(IPageLayout.ID_RES_NAV)
    layout.addShowViewShortcut(ProjectExplorer.VIEW_ID)
    layout.addShowViewShortcut(IPageLayout.ID_OUTLINE)
    layout.addShowViewShortcut("org.eclipse.pde.runtime.LogView")
  }
  private def createFolders(layout : IPageLayout) = {
    val editorArea = layout.getEditorArea()

    val explorerFolder = layout.createFolder("explorer", IPageLayout.LEFT, 0.25f, editorArea)
    explorerFolder.addView(JavaUI.ID_PACKAGES)

    val problemsFolder = layout.createFolder("problems", IPageLayout.BOTTOM, 0.75f, editorArea)
    problemsFolder.addView(IPageLayout.ID_PROBLEM_VIEW)
    problemsFolder.addView(IPageLayout.ID_TASK_LIST)
    problemsFolder.addView(IConsoleConstants.ID_CONSOLE_VIEW)
//    problemsFolder.addView("org.eclipse.pde.runtime.LogView")

    val outlineFolder = layout.createFolder("right", IPageLayout.RIGHT,0.75f,editorArea)
    outlineFolder.addView(IPageLayout.ID_OUTLINE)
  }
}
