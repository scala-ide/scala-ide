/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse

import org.eclipse.core.resources.{ IFile, IProject }
import org.eclipse.core.runtime.{ Path, NullProgressMonitor }

import org.eclipse.ui.{ IFileEditorInput, PlatformUI }
import org.eclipse.ui.part.FileEditorInput

import org.eclipse.pde.core.plugin.{ IPluginModelBase, IPluginBase, IPluginImport }
import org.eclipse.pde.internal.ui.editor.plugin.ManifestEditor
import org.eclipse.pde.internal.ui.IPDEUIConstants
import org.eclipse.pde.internal.core.WorkspacePluginModelManager
import org.eclipse.pde.internal.ui.editor.plugin.DependenciesPage

/**
 * Adds or removes 'org.scala-ide.scala.library' as a required plug-in for an Eclipse plug-in project.
 */
object ScalaLibraryPluginDependencyUtils {

  def addScalaLibraryRequirement(project: IProject) = editPlugin(project, pluginModelBase => if (getExistingScalaLibraryImport(pluginModelBase).isEmpty) {
    val scalaLibraryImport = pluginModelBase.getPluginFactory.createImport
    scalaLibraryImport.setId(ScalaPlugin.plugin.libraryPluginId)
    pluginModelBase.getPluginBase.add(scalaLibraryImport)
  })

  def removeScalaLibraryRequirement(project: IProject) = editPlugin(project, pluginModelBase =>
    getExistingScalaLibraryImport(pluginModelBase) foreach pluginModelBase.getPluginBase.remove)

  private def getExistingScalaLibraryImport(pluginModelBase: IPluginModelBase): Array[IPluginImport] =
    pluginModelBase.getPluginBase.getImports filter { ScalaPlugin.plugin.libraryPluginId == _.getId }

  private def editPlugin(project: IProject, editStrategy: IPluginModelBase => Unit) {
    val (manifestEditor, alreadyOpen) = findOrOpenManifestEditor(project)
    manifestEditor.setActivePage(DependenciesPage.PAGE_ID) /* According to AJDT, needed to ensure the model will be updated consistently across the pages.
                                                              See org.eclipse.ajdt.internal.utils.AJDTUtils.getAndPrepareToChangePDEModel */
    val pluginModelBase = manifestEditor.getAggregateModel.asInstanceOf[IPluginModelBase]
    val pluginBase = pluginModelBase.getPluginBase

    editStrategy(pluginModelBase)

    manifestEditor.doSave(new NullProgressMonitor)
    if (!alreadyOpen)
      getWorkbenchPage.closeEditor(manifestEditor,/* save = */false)
  }

  private def getWorkbenchPage = PlatformUI.getWorkbench.getActiveWorkbenchWindow.getActivePage

  private def findOrOpenManifestEditor(project: IProject): (ManifestEditor, Boolean) = {
    val workbenchPage = getWorkbenchPage
    val manifestFile = project.findMember(new Path(MANIFEST_PATH)).asInstanceOf[IFile]
    val fileEditorInput = new FileEditorInput(manifestFile)
    val existingEditor = Option(workbenchPage.findEditor(fileEditorInput))
    val manifestEditor = (existingEditor getOrElse workbenchPage.openEditor(fileEditorInput, IPDEUIConstants.MANIFEST_EDITOR_ID))
      .asInstanceOf[ManifestEditor]
    val alreadyOpen = existingEditor.isDefined
    (manifestEditor, alreadyOpen)
  }

  private val MANIFEST_PATH = "META-INF/MANIFEST.MF"

}
