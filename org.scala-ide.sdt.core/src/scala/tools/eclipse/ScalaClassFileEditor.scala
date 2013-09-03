/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse

import scala.tools.eclipse.javaelements.ScalaClassFile
import scala.tools.eclipse.javaelements.ScalaCompilationUnit
import scala.tools.eclipse.semantichighlighting.TextPresentationHighlighter
import scala.tools.eclipse.semantichighlighting.ui.TextPresentationEditorHighlighter

import org.eclipse.jdt.core.IJavaElement
import org.eclipse.jdt.internal.ui.javaeditor.ClassFileEditor
import org.eclipse.jdt.ui.actions.IJavaEditorActionDefinitionIds
import org.eclipse.jface.action.Action
import org.eclipse.jface.text.ITextSelection
import org.eclipse.jface.text.source.SourceViewerConfiguration

class ScalaClassFileEditor extends ClassFileEditor with ScalaCompilationUnitEditor {

  override def setSourceViewerConfiguration(configuration : SourceViewerConfiguration) {
    super.setSourceViewerConfiguration(
      configuration match {
        case svc : ScalaSourceViewerConfiguration => svc
        case _ => new ScalaSourceViewerConfiguration(getPreferenceStore, ScalaPlugin.prefStore, this)
      })
  }

  override def getElementAt(offset : Int) : IJavaElement = {
    getInputJavaElement match {
      case scf : ScalaClassFile => scf.getElementAt(offset)
      case _ => null
    }
  }

  override def getCorrespondingElement(element : IJavaElement) : IJavaElement = {
      getInputJavaElement match {
        case scf : ScalaClassFile => scf.getCorrespondingElement(element).getOrElse(super.getCorrespondingElement(element))
        case _ => super.getCorrespondingElement(element)
    }
  }

  override protected def createActions() {
    super.createActions()
    val openAction = new Action {
      override def run {
        Option(getInputJavaElement) map (_.asInstanceOf[ScalaCompilationUnit]) foreach { scu =>
         scu.followDeclaration(ScalaClassFileEditor.this, getSelectionProvider.getSelection.asInstanceOf[ITextSelection])
        }
      }
    }
    openAction.setActionDefinitionId(IJavaEditorActionDefinitionIds.OPEN_EDITOR)
    setAction("OpenEditor", openAction)
  }

  override def createSemantichHighlighter: TextPresentationHighlighter = {
    TextPresentationEditorHighlighter(this, semanticHighlightingPreferences, _ => (), _ => ())
  }

  override def forceSemanticHighlightingOnInstallment: Boolean = true
}
