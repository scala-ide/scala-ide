/*
 * Copyright (c) 2014 Contributor. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Scala License which accompanies this distribution, and
 * is available at http://www.scala-lang.org/node/146
 */
package org.scalaide.ui.internal.editor.decorators.semantichighlighting

import java.util.concurrent.ConcurrentHashMap

import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.jdt.core.ICompilationUnit
import org.eclipse.jdt.core.WorkingCopyOwner
import org.eclipse.jdt.internal.ui.JavaPlugin
import org.eclipse.jdt.internal.ui.javaeditor.JavaSourceViewer
import org.eclipse.ui.IEditorInput
import org.eclipse.ui.IWorkbenchPart
import org.scalaide.core.internal.jdt.model.ScalaCompilationUnit
import org.scalaide.logging.HasLogger
import org.scalaide.ui.internal.actions.PartAdapter
import org.scalaide.ui.internal.editor.ScalaSourceFileEditor
import org.scalaide.ui.internal.editor.decorators.SemanticAction
import org.scalaide.util.internal.Utils.WithAsInstanceOfOpt
import org.scalaide.util.internal.eclipse.EclipseUtils


/**
 * Manages the SemanticHighlightingPresenter instances for the open editors.
 *
 * Each ScalaCompilationUnit has one associated SemanticHighlightingPresenter,
 * which is created the first time a reconciliation is performed for a
 * compilation unit. When the editor (respectively the IWorkbenchPart) is closed,
 * the SemanticHighlightingPresenter is removed.
 *
 * Deprecating this class since only the implicit highlighting component is using it, and I'm quite convinced that implicit highlighting
 * should be enabled via the editor, just like we do for semantic highlighting.
 *
 */
@deprecated("This is not needed and should be removed the moment implicit highlighting is hooked in the editor", "2.1.0")
class SemanticHighlightingReconciliation(actions: List[JavaSourceViewer => SemanticAction]) extends HasLogger {

  private case class SemanticDecorationManagers(actions: List[SemanticAction])

  private val semanticDecorationManagers: java.util.Map[ScalaCompilationUnit, SemanticDecorationManagers] = new ConcurrentHashMap

  /** A listener that removes a  SemanticHighlightingPresenter when the part is closed. */
  private class UnregisteringPartListener(scu: ScalaCompilationUnit) extends PartAdapter {
    override def partClosed(part: IWorkbenchPart) {
      for {
        scalaEditor <- part.asInstanceOfOpt[ScalaSourceFileEditor]
        editorInput <- Option(scalaEditor.getEditorInput)
        compilationUnit <- getCompilationUnitOf(editorInput)
        if scu == compilationUnit
      } semanticDecorationManagers.remove(scu)
    }
  }

  /* Following Iulian's suggestion (https://github.com/scala-ide/scala-ide/pull/154#discussion_r1179403).
   * Hopefully, we will be able to eliminate all this fuzzy code once we fix #1001156 */
  private def getCompilationUnitOf(editorInput: IEditorInput): Option[ICompilationUnit] = {
    val cu = JavaPlugin.getDefault.getWorkingCopyManager.getWorkingCopy(editorInput)
    if (cu == null) logger.warn("Compilation unit for EditorInput %s is `null`. This could indicate a regression.".format(editorInput.getName))
    Option(cu)
  }

  /**
   * Searches for the Editor that currently displays the compilation unit, then creates
   * an instance of SemanticHighlightingPresenter. A listener is registered at the editor
   * to remove the SemanticHighlightingPresenter when the editor is closed.
   */
  private def createSemanticDecorationManagers(scu: ScalaCompilationUnit): Option[SemanticDecorationManagers] = {
    val presenters =
      for {
        page <- EclipseUtils.getWorkbenchPages
        editorReference <- page.getEditorReferences
        editor <- Option(editorReference.getEditor(false))
        scalaEditor <- editor.asInstanceOfOpt[ScalaSourceFileEditor]
        editorInput <- Option(scalaEditor.getEditorInput)
        compilationUnit <- getCompilationUnitOf(editorInput)
        if scu == compilationUnit
      } yield {
        page.addPartListener(new UnregisteringPartListener(scu))
        SemanticDecorationManagers(actions.map(_(scalaEditor.sourceViewer)))
      }
    presenters.headOption
  }

  def beforeReconciliation(scu: ScalaCompilationUnit, monitor: IProgressMonitor, workingCopyOwner: WorkingCopyOwner) {
    val firstTimeReconciliation = !semanticDecorationManagers.containsKey(scu)

    if (firstTimeReconciliation) {
      for (semanticDecorationManager <- createSemanticDecorationManagers(scu))
        semanticDecorationManagers.put(scu, semanticDecorationManager)
    }
  }

  def afterReconciliation(scu: ScalaCompilationUnit, monitor: IProgressMonitor, workingCopyOwner: WorkingCopyOwner) {
    // sometimes we reconcile compilation units that are not open in an editor,
    // so we need to guard against the case where there's no semantic highlighter
    for {
      semanticDecorationManager <- Option(semanticDecorationManagers.get(scu))
      action <- semanticDecorationManager.actions
    } action(scu)
  }
}
