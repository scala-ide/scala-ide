/*
 * Copyright 2005-2008 LAMP/EPFL
 * @author Sean McDirmid
 */
// $Id$

package scala.tools.eclipse;
import org.eclipse.ui._
import org.eclipse.ui.console._
import org.eclipse.jdt.ui._
import org.eclipse.debug.ui._
import org.eclipse.ui.navigator.resources._

class PerspectiveFactory extends IPerspectiveFactory {
  def createInitialLayout(layout : IPageLayout) = {
    createFolders(layout);
    addShortcuts(layout);
    layout.addActionSet(IDebugUIConstants.LAUNCH_ACTION_SET);
  }
  private def addShortcuts(layout : IPageLayout) = {
    layout.addNewWizardShortcut(ScalaPlugin.plugin.projectWizId)
    layout.addNewWizardShortcut(ScalaPlugin.plugin.netProjectWizId)
    layout.addNewWizardShortcut("org.eclipse.jdt.ui.wizards.NewPackageCreationWizard")
    layout.addNewWizardShortcut(ScalaPlugin.plugin.classWizId)
    layout.addNewWizardShortcut(ScalaPlugin.plugin.traitWizId)
    layout.addNewWizardShortcut(ScalaPlugin.plugin.objectWizId)
    layout.addNewWizardShortcut(ScalaPlugin.plugin.applicationWizId)
    layout.addNewWizardShortcut("org.eclipse.ui.wizards.new.folder")
    layout.addNewWizardShortcut("org.eclipse.ui.wizards.new.file")
    
    layout.addShowViewShortcut(IPageLayout.ID_RES_NAV)
    layout.addShowViewShortcut(ProjectExplorer.VIEW_ID)
    layout.addShowViewShortcut(IPageLayout.ID_OUTLINE)
    layout.addShowViewShortcut("org.eclipse.pde.runtime.LogView")
    	            
  }
  private def createFolders(layout : IPageLayout) = {
    val editorArea = layout.getEditorArea();
    val explorerFolder = layout.createFolder("explorer", IPageLayout.LEFT, 0.25f, editorArea);
    explorerFolder.addView(JavaUI.ID_PACKAGES)
    //explorerFolder.addView(IPageLayout.ID_OUTLINE);
    val problemsFolder = layout.createFolder("problems", IPageLayout.BOTTOM, 0.75f, editorArea);
    problemsFolder.addView(IPageLayout.ID_PROBLEM_VIEW);
    problemsFolder.addView(IPageLayout.ID_TASK_LIST);
    problemsFolder.addView(IConsoleConstants.ID_CONSOLE_VIEW);
    problemsFolder.addView("org.eclipse.pde.runtime.LogView");
  }
}

object PerspectiveFactory {
  val id = "ch.epfl.lamp.sdt.core.perspective"
}
