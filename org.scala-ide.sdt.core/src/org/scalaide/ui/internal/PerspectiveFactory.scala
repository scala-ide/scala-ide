package org.scalaide.ui.internal

import org.eclipse.debug.ui.IDebugUIConstants
import org.eclipse.jdt.ui.JavaUI
import org.eclipse.ui.IPageLayout
import org.eclipse.ui.IPerspectiveFactory
import org.eclipse.ui.console.IConsoleConstants
import org.eclipse.ui.navigator.resources.ProjectExplorer
import org.scalaide.core.SdtConstants

/**
 * This class is called the first time the Scala perspective is created. Once
 * the Scala perspective is created, it is cached in the platform, which means
 * that any changes to this class won't be seen by users, which already have a
 * Scala perspective.
 *
 * Therefore, new wizard entries that are added to this factory also need to be
 * registered for automatic activation, see the following method for more details:
 *
 * [[org.scalaide.ui.internal.migration.MigrationPreferenceInitializer.activateNewWizardShortcut]]
 */
class PerspectiveFactory extends IPerspectiveFactory {

  def createInitialLayout(layout: IPageLayout) = {
    createFolders(layout)
    addShortcuts(layout)
    layout.addActionSet(IDebugUIConstants.LAUNCH_ACTION_SET)
    layout.addActionSet(JavaUI.ID_ACTION_SET)
  }

  private def addShortcuts(layout: IPageLayout) = {
    layout.addNewWizardShortcut(SdtConstants.ProjectWizId)
    layout.addNewWizardShortcut("org.eclipse.jdt.ui.wizards.NewPackageCreationWizard")
    layout.addNewWizardShortcut(SdtConstants.ClassCreatorWizId)
    layout.addNewWizardShortcut(SdtConstants.TraitCreatorWizId)
    layout.addNewWizardShortcut(SdtConstants.ObjectCreatorWizId)
    layout.addNewWizardShortcut(SdtConstants.PackageObjectCreatorWizId)
    layout.addNewWizardShortcut(SdtConstants.AppCreatorWizId)
    layout.addNewWizardShortcut("org.eclipse.jdt.ui.wizards.NewSourceFolderCreationWizard")
    layout.addNewWizardShortcut("org.eclipse.ui.wizards.new.folder")
    layout.addNewWizardShortcut("org.eclipse.ui.wizards.new.file")

    layout.addShowViewShortcut(ProjectExplorer.VIEW_ID)
    layout.addShowViewShortcut(IPageLayout.ID_OUTLINE)
  }

  private def createFolders(layout: IPageLayout) = {
    val editorArea = layout.getEditorArea()

    val explorerFolder = layout.createFolder("explorer", IPageLayout.LEFT, 0.25f, editorArea)
    explorerFolder.addView(JavaUI.ID_PACKAGES)

    val problemsFolder = layout.createFolder("problems", IPageLayout.BOTTOM, 0.75f, editorArea)
    problemsFolder.addView(IPageLayout.ID_PROBLEM_VIEW)
    problemsFolder.addView(IPageLayout.ID_TASK_LIST)
    problemsFolder.addView(IConsoleConstants.ID_CONSOLE_VIEW)

    val outlineFolder = layout.createFolder("right", IPageLayout.RIGHT, 0.75f, editorArea)
    outlineFolder.addView(IPageLayout.ID_OUTLINE)
  }
}
