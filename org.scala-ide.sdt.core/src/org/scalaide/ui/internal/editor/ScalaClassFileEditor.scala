package org.scalaide.ui.internal.editor

import org.eclipse.jdt.core.IJavaElement
import org.eclipse.jdt.internal.ui.javaeditor.ClassFileEditor
import org.eclipse.jdt.ui.actions.IJavaEditorActionDefinitionIds
import org.eclipse.jface.action.Action
import org.eclipse.jface.text.ITextSelection
import org.scalaide.core.internal.jdt.model.ScalaClassFile
import org.scalaide.core.internal.jdt.model.ScalaCompilationUnit
import org.scalaide.ui.internal.editor.decorators.implicits.ImplicitHighlightingPresenter
import org.scalaide.ui.internal.editor.decorators.semantichighlighting.TextPresentationEditorHighlighter
import org.scalaide.ui.internal.editor.decorators.semantichighlighting.TextPresentationHighlighter

class ScalaClassFileEditor extends ClassFileEditor with ScalaCompilationUnitEditor {

  private lazy val implicitHighlighter = new ImplicitHighlightingPresenter(sourceViewer)

  override def createPartControl(parent: org.eclipse.swt.widgets.Composite) {
    super.createPartControl(parent)

    getInteractiveCompilationUnit() match {
      case scu: ScalaCompilationUnit => implicitHighlighter(scu)
      case _ =>
    }
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

  override def createSemanticHighlighter: TextPresentationHighlighter =
    TextPresentationEditorHighlighter(this, semanticHighlightingPreferences, _ => (), _ => ())

  override def forceSemanticHighlightingOnInstallment: Boolean = true
}
