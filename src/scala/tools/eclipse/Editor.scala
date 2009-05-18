/*
 * Copyright 2005-2009 LAMP/EPFL
 * @author Sean McDirmid
 */
// $Id$

package scala.tools.eclipse

import org.eclipse.jdt.core.{ ICompilationUnit, IJavaElement, JavaModelException }
import org.eclipse.jdt.internal.corext.util.JavaModelUtil
import org.eclipse.jdt.internal.ui.JavaPlugin
import org.eclipse.jdt.internal.ui.javaeditor.ClassFileDocumentProvider
import org.eclipse.ui.IEditorInput

import scala.util.Sorting

import java.{ util => ju }

import org.eclipse.core.resources.{IWorkspaceRunnable,IMarker,IResource};
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.internal.ui.javaeditor.{ CompilationUnitEditor, JavaSourceViewer }
import org.eclipse.jdt.ui.text.JavaSourceViewerConfiguration
import org.eclipse.jdt.ui.text.java.ContentAssistInvocationContext
import org.eclipse.jface.util.PropertyChangeEvent
import org.eclipse.jface.preference.IPreferenceStore
import org.eclipse.jface.text.{TextPresentation,ITypedRegion,DocumentEvent,DefaultInformationControl,IInformationControlCreator,IDocumentListener,IDocument,DocumentCommand,IAutoEditStrategy,ITextViewer,ITextHover,ITextHoverExtension,ITextOperationTarget,IRegion,Region};
import org.eclipse.jface.text.contentassist.{ContentAssistant,ICompletionProposal,IContentAssistant,IContentAssistProcessor,IContextInformation,IContextInformationPresenter,IContextInformationValidator};
import org.eclipse.jface.text.hyperlink.{IHyperlink,IHyperlinkDetector};
import org.eclipse.jface.text.presentation.{IPresentationDamager,IPresentationRepairer,PresentationReconciler};
import org.eclipse.jface.text.source.{AnnotationModelEvent,IAnnotationModel,ICharacterPairMatcher,IOverviewRuler,ISourceViewer,IVerticalRuler,SourceViewerConfiguration,IAnnotationModelListener};
import org.eclipse.jface.text.source.projection.{ProjectionAnnotationModel,ProjectionSupport,ProjectionViewer,IProjectionListener};
import org.eclipse.ui.{IEditorPart,IFileEditorInput};
import org.eclipse.ui.editors.text.{ EditorsUI, TextEditor };
import org.eclipse.ui.texteditor.{ContentAssistAction,SourceViewerDecorationSupport,IAbstractTextEditorHelpContextIds,ITextEditorActionConstants,ITextEditorActionDefinitionIds,IWorkbenchActionDefinitionIds,TextOperationAction};
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.{ExtendedModifyEvent,ExtendedModifyListener};
import org.eclipse.swt.events.{KeyListener,KeyEvent,FocusListener,FocusEvent};
import org.eclipse.swt.widgets.{Composite,Shell};

import scala.tools.eclipse.contribution.weaving.jdt.ui.javaeditor.{ ScalaCompilationUnitDocumentProvider, ScalaEditor } 
import scala.tools.eclipse.contribution.weaving.jdt.ui.text.java.ScalaCompletionProcessor
import scala.tools.eclipse.util.Style

class Editor extends ScalaEditor {
  val plugin : ScalaPlugin = ScalaPlugin.plugin

  showChangeInformation(true) 
  setPartName("Scala Editor");

  var file : Option[plugin.Project#File] = None
  
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
  
  def getSourceViewer0 = super.getSourceViewer.asInstanceOf[ScalaSourceViewer with ProjectionViewer];
  
  object documentListener extends IDocumentListener {
    def documentAboutToBeChanged(e : DocumentEvent) = {
      // TODO reinstate
    }
    
    def documentChanged(e : DocumentEvent) = {
      // TODO reinstate
    }
  }
  
  override def createJavaSourceViewer(parent : Composite, ruler : IVerticalRuler, overviewRuler : IOverviewRuler, isOverviewRulerVisible : Boolean, styles :  Int, store : IPreferenceStore) : JavaSourceViewer = {
    new ScalaSourceViewer(plugin, this, parent, ruler, overviewRuler, isOverviewRulerVisible, styles, store)
  }

  override def createJavaSourceViewerConfiguration : JavaSourceViewerConfiguration =
    new ScalaSourceViewerConfiguration(Editor.this.getPreferenceStore, Editor.this, contentAssistProcessor)
  
  object contentAssistProcessor
    extends ScalaCompletionProcessor(Editor.this, new ContentAssistant, IDocument.DEFAULT_CONTENT_TYPE) {
    override def collectProposals0(tv : ITextViewer, offset : Int, monitor : IProgressMonitor,  context : ContentAssistInvocationContext) : ju.List[_] = {
      val completions = file.get.doComplete(offset)
      ju.Arrays.asList(completions.toArray : _*)
    }
  }

  override def dispose = {
    val viewer = getSourceViewer
    if (viewer != null)
      viewer.setDocument(null)
    
    super.dispose
  }
  
  override protected def initializeEditor = {
    super.initializeEditor
  }
  
  override protected def handlePreferenceStoreChanged(event : PropertyChangeEvent) = {
    super.handlePreferenceStoreChanged(event)
    val plugin = this.plugin
    import plugin._
    def ck(id :String) = event.getProperty.endsWith(id)
    if (ck(Style.backgroundId) ||
      ck(Style.foregroundId) ||
        ck(Style.boldId) ||
          ck(Style.underlineId) ||
            ck(Style.italicsId) ||
              ck(Style.strikeoutId)) {
      if (file != null && file.isDefined) {
        val viewer = getSourceViewer0
        viewer.invalidateTextPresentation(0, file.get.content.length)
      }
    }
  }
  
  override def setSourceViewerConfiguration(configuration : SourceViewerConfiguration) {
    super.setSourceViewerConfiguration(
      configuration match {
        case svc : ScalaSourceViewerConfiguration => svc
        case _ => new ScalaSourceViewerConfiguration(Editor.this.getPreferenceStore, Editor.this, contentAssistProcessor)
      })
  }
  
  override def doSetInput(input : IEditorInput) = {
    input match {
      case null =>
      case input : plugin.ClassFileInput =>
        setDocumentProvider(new ClassFileDocumentProvider)
      case _ =>
        setDocumentProvider(new ScalaCompilationUnitDocumentProvider)
    }
    super.doSetInput(input)
  }

  override def getCorrespondingElement(element : IJavaElement ) : IJavaElement = element

  override def getElementAt(offset : Int) : IJavaElement = getElementAt(offset, true)

  override def getElementAt(offset : Int, reconcile : Boolean) : IJavaElement = {
    getInputJavaElement match {
      case unit : ICompilationUnit => 
        try {
          if (reconcile) {
            JavaModelUtil.reconcile(unit)
            unit.getElementAt(offset)
          } else if (unit.isConsistent())
            unit.getElementAt(offset)
          else
            null
        } catch {
          case ex : JavaModelException => {
            if (!ex.isDoesNotExist)
              JavaPlugin.log(ex.getStatus)
            null
          }
        }
      case _ => null
    }
  }
}

object EditorMessages {
  private val EDITOR_BUNDLE_FOR_CONSTRUCTED_KEYS = "org.eclipse.ui.texteditor.ConstructedEditorMessages"
  val bundleForConstructedKeys = ju.ResourceBundle.getBundle(EDITOR_BUNDLE_FOR_CONSTRUCTED_KEYS)
}
