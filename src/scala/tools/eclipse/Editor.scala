/*
 * Copyright 2005-2008 LAMP/EPFL
 * @author Sean McDirmid
 */
// $Id$

package scala.tools.eclipse

import java.util.ResourceBundle

import org.eclipse.core.internal.runtime.AdapterManager
import org.eclipse.jdt.core.{ ICompilationUnit, IJavaElement, JavaModelException }
import org.eclipse.jdt.internal.corext.util.JavaModelUtil
import org.eclipse.jdt.internal.ui.JavaPlugin
import org.eclipse.jdt.internal.ui.javaeditor.{ ClassFileDocumentProvider, JavaEditor, SemanticHighlightings }
import org.eclipse.jdt.internal.ui.javaeditor.breadcrumb.IBreadcrumb
import org.eclipse.jdt.ui.{ JavaUI, PreferenceConstants } 
import org.eclipse.jface.preference.{ IPreferenceStore, PreferenceStore }
import org.eclipse.jface.text.ITextOperationTarget
import org.eclipse.jface.util.PropertyChangeEvent
import org.eclipse.swt.widgets.Composite
import org.eclipse.ui.IEditorInput
import org.eclipse.ui.texteditor.{ 
  ChainedPreferenceStore, IAbstractTextEditorHelpContextIds, ITextEditorActionConstants, IWorkbenchActionDefinitionIds,
  TextOperationAction}

import lampion.eclipse.SourceViewer
import lampion.util.ReflectionUtils

class Editor extends { val plugin = Driver.driver } with lampion.eclipse.Editor {
  override def doSetInput(input : IEditorInput) = {
    input match {
      case null =>
      case input : plugin.ClassFileInput =>
        setDocumentProvider(new ClassFileDocumentProvider)
      case _ =>
        setDocumentProvider(new plugin.DocumentProvider)
    }
    super.doSetInput(input)
  }

  override def getCorrespondingElement(element : IJavaElement ) : IJavaElement = element

  override def getElementAt(offset : Int) : IJavaElement = getElementAt(offset, true)

  override def getElementAt(offset : Int, reconcile : Boolean) : IJavaElement = {
    EditorUtils.getInputJavaElement(this) match {
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

  override def createPartControl(parent : Composite) {
    val store = getPreferenceStore.asInstanceOf[ChainedPreferenceStore]
    EditorUtils.prependStore(store, EditorUtils.suppressingStore)
    super.createPartControl(parent)
  }
  
  // Breadcumb not yet supported for Scala
  override def createBreadcrumb : IBreadcrumb = null

  // Folding not yet supported for Scala
	override def isFoldingEnabled = false
 
  override def createActions {
		super.createActions
  
    // Reset cut/copy/paste to default until the 'add imports on paste' behaviour can
    // be extended to support Scala
    val cutAction = new TextOperationAction(EditorUtils.bundleForConstructedKeys, "Editor.Cut.", this, ITextOperationTarget.CUT)
    cutAction.setHelpContextId(IAbstractTextEditorHelpContextIds.CUT_ACTION)
    cutAction.setActionDefinitionId(IWorkbenchActionDefinitionIds.CUT)
    setAction(ITextEditorActionConstants.CUT, cutAction)

    val copyAction = new TextOperationAction(EditorUtils.bundleForConstructedKeys, "Editor.Copy.", this, ITextOperationTarget.COPY, true)
    copyAction.setHelpContextId(IAbstractTextEditorHelpContextIds.COPY_ACTION)
    copyAction.setActionDefinitionId(IWorkbenchActionDefinitionIds.COPY)
    setAction(ITextEditorActionConstants.COPY, copyAction)

    val pasteAction = new TextOperationAction(EditorUtils.bundleForConstructedKeys, "Editor.Paste.", this, ITextOperationTarget.PASTE)
    pasteAction.setHelpContextId(IAbstractTextEditorHelpContextIds.PASTE_ACTION)
    pasteAction.setActionDefinitionId(IWorkbenchActionDefinitionIds.PASTE)
    setAction(ITextEditorActionConstants.PASTE, pasteAction)
  }
}

object EditorUtils extends ReflectionUtils {
  val cpsClass = Class.forName("org.eclipse.ui.texteditor.ChainedPreferenceStore")
  val fPreferenceStoresField = getField(cpsClass, "fPreferenceStores")
  val jeClass = Class.forName("org.eclipse.jdt.internal.ui.javaeditor.JavaEditor")
  val getInputJavaElementMethod = getMethod(jeClass, "getInputJavaElement")
  val breadcrumbKey = "breadcrumb"
  
  val suppressingStore = {
    val store = new PreferenceStore
    val highlightings = SemanticHighlightings.getSemanticHighlightings
    for (highlighting <- highlightings) { 
      val key = SemanticHighlightings.getEnabledPreferenceKey(highlighting)
      store.setDefault(key, true)
      store.setValue(key, false)
    }
    
    // Breadcumb not yet supported for Scala
    val breadcrumbKeys =
      breadcrumbKey + "." + PerspectiveFactory.id ::
      breadcrumbKey + "." + JavaUI.ID_PERSPECTIVE ::
      Nil
    for (key <- breadcrumbKeys) { 
      store.setDefault(key, true)
      store.setValue(key, false)
    }
    
    // Folding not yet supported for Scala
    store.setDefault(PreferenceConstants.EDITOR_FOLDING_ENABLED, true)
    store.setValue(PreferenceConstants.EDITOR_FOLDING_ENABLED, false)
    
    store
  }
  
  def prependStore(store : ChainedPreferenceStore, newChild : IPreferenceStore) {
    val stores = fPreferenceStoresField.get(store).asInstanceOf[Array[IPreferenceStore]]
    val numStores = stores.length
    val newStores = new Array[IPreferenceStore](numStores+1)
    newStores(0) = newChild
    Array.copy(stores, 0, newStores, 1, numStores)
    fPreferenceStoresField.set(store, newStores)
  }
  
  // Workaround for continued support for Eclipse 3.3
  def getInputJavaElement(editor : JavaEditor) = getInputJavaElementMethod.invoke(editor)

  private val BUNDLE_FOR_CONSTRUCTED_KEYS = "org.eclipse.ui.texteditor.ConstructedEditorMessages"
  val bundleForConstructedKeys = ResourceBundle.getBundle(BUNDLE_FOR_CONSTRUCTED_KEYS)
}
