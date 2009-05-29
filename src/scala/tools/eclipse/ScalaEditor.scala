/*
 * Copyright 2005-2009 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse

import java.{ util => ju }

import org.eclipse.jdt.ui.text.JavaSourceViewerConfiguration
import org.eclipse.jface.preference.IPreferenceStore
import org.eclipse.jface.text.ITextOperationTarget
import org.eclipse.jface.text.source.SourceViewerConfiguration
import org.eclipse.ui.texteditor.{ IAbstractTextEditorHelpContextIds, ITextEditorActionConstants, IWorkbenchActionDefinitionIds, TextOperationAction }

import scala.tools.eclipse.contribution.weaving.jdt.ui.javaeditor.ScalaEditorStub 

class ScalaEditor extends ScalaEditorStub {

  setPartName("Scala Editor")

  override protected def createActions : Unit = {
    super.createActions
    
    val cutAction = new TextOperationAction(EditorMessages.bundleForConstructedKeys, "Editor.Cut.", this, ITextOperationTarget.CUT); //$NON-NLS-1$
    cutAction.setHelpContextId(IAbstractTextEditorHelpContextIds.CUT_ACTION);
    cutAction.setActionDefinitionId(IWorkbenchActionDefinitionIds.CUT);
    setAction(ITextEditorActionConstants.CUT, cutAction);

    val copyAction = new TextOperationAction(EditorMessages.bundleForConstructedKeys, "Editor.Copy.", this, ITextOperationTarget.COPY, true); //$NON-NLS-1$
    copyAction.setHelpContextId(IAbstractTextEditorHelpContextIds.COPY_ACTION);
    copyAction.setActionDefinitionId(IWorkbenchActionDefinitionIds.COPY);
    setAction(ITextEditorActionConstants.COPY, copyAction);

    val pasteAction = new TextOperationAction(EditorMessages.bundleForConstructedKeys, "Editor.Paste.", this, ITextOperationTarget.PASTE); //$NON-NLS-1$
    pasteAction.setHelpContextId(IAbstractTextEditorHelpContextIds.PASTE_ACTION);
    pasteAction.setActionDefinitionId(IWorkbenchActionDefinitionIds.PASTE);
    setAction(ITextEditorActionConstants.PASTE, pasteAction);
  }
  
  override def createJavaSourceViewerConfiguration : JavaSourceViewerConfiguration =
    new ScalaSourceViewerConfiguration(getPreferenceStore, this)
  
  override def setSourceViewerConfiguration(configuration : SourceViewerConfiguration) {
    super.setSourceViewerConfiguration(
      configuration match {
        case svc : ScalaSourceViewerConfiguration => svc
        case _ => new ScalaSourceViewerConfiguration(getPreferenceStore, this)
      })
  }
}

object EditorMessages {
  private val EDITOR_BUNDLE_FOR_CONSTRUCTED_KEYS = "org.eclipse.ui.texteditor.ConstructedEditorMessages"
  val bundleForConstructedKeys = ju.ResourceBundle.getBundle(EDITOR_BUNDLE_FOR_CONSTRUCTED_KEYS)
}
