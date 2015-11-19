package org.scalaide.ui.internal.editor.outline
import org.eclipse.ui.views.contentoutline.ContentOutlinePage
import org.eclipse.swt.widgets.Composite
import org.eclipse.ui.IActionBars
import org.eclipse.jdt.internal.ui.actions.CollapseAllAction
import org.eclipse.ui.handlers.CollapseAllHandler
import org.eclipse.jface.action.Action
import org.eclipse.jface.viewers.ISelection
import org.eclipse.jface.viewers.TreeViewer
import org.eclipse.jdt.internal.ui.JavaPluginImages
import org.eclipse.ui.PlatformUI
import org.eclipse.jdt.internal.ui.IJavaHelpContextIds
import org.eclipse.jdt.internal.ui.JavaPlugin
import org.scalaide.logging.HasLogger
import org.scalaide.core.internal.ScalaPlugin
import org.eclipse.jface.viewers.ViewerComparator
import org.eclipse.jface.viewers.Viewer
import org.scalaide.ui.internal.preferences.EditorPreferencePage

class PublicOnlyAction(contentProvider: ScalaOutlineContentProvider, viewer: Viewer) extends Action {
  setText("Hide non-public members")
  setToolTipText("Hide non-public members")
  setDescription("Hide non-public members")
  JavaPluginImages.setLocalImageDescriptors(this, "public_co.gif")
  val checked = ScalaPlugin().getPreferenceStore().getBoolean("PublicOnlyAction.isChecked")
  valueChanged(checked, false)
  override def run() = {
    valueChanged(isChecked, true)
  }
  private def valueChanged(checked: Boolean, save: Boolean) = {
    setChecked(checked)
    contentProvider.publicOnly = checked
    viewer.refresh()
    if (save)
      ScalaPlugin().getPreferenceStore().setValue("PublicOnlyAction.isChecked", checked)
  }
}

class LexicalComparator extends ViewerComparator {
  override def compare(viewer: Viewer, e1: AnyRef, e2: AnyRef): Int = {
    def cn(e: AnyRef): (Int, String) = e match {
      case n: PackageNode => (0, n.name)
      case n: ImportsNode => (1, n.name)
      case n: ContainerNode => (2, n.name)
      case n: TypeNode => (2, n.name)
      case n: Node => (3, n.name)
      case _ => (4, "")
    }
    val cn1 = cn(e1)
    val cn2 = cn(e2)
    if (cn1._1 == cn2._1)
      cn1._2.compareToIgnoreCase(cn2._2)
    else
      cn1._1 - cn2._1
  }
}

class ScalaComparator extends ViewerComparator {
  override def compare(viewer: Viewer, e1: AnyRef, e2: AnyRef): Int = {
    e1.asInstanceOf[Node].start - e2.asInstanceOf[Node].start
  }
}

class LexicalSortingAction(treeViewer: TreeViewer) extends Action {
  PlatformUI.getWorkbench().getHelpSystem().setHelp(this, IJavaHelpContextIds.LEXICAL_SORTING_OUTLINE_ACTION)
  setText("Sort")
  JavaPluginImages.setLocalImageDescriptors(this, "alphab_sort_co.gif")
  setToolTipText("Sort")
  setDescription("Enable Sort")

  val checked = ScalaPlugin().getPreferenceStore().getBoolean("LexicalSortingAction.isChecked")
  valueChanged(checked, false)
  override def run() = {
    valueChanged(isChecked, true)
  }
  private def valueChanged(checked: Boolean, save: Boolean) = {
    setChecked(checked)
    if (checked)
      treeViewer.setComparator(new LexicalComparator)
    else
      treeViewer.setComparator(new ScalaComparator)
    if (save)
      ScalaPlugin().getPreferenceStore().setValue("LexicalSortingAction.isChecked", if (checked) true else false)
  }
}

/**
 * The content outline page of the scala editor. Based on  org.eclipse.jdt.internal.ui.javaeditor.JavaOutlinePage.
 */
class ScalaOutlinePage(val fEditor: OutlinePageEditorExtension) extends ContentOutlinePage with HasLogger {
  private var input: Object = null
  private val contentProvider = new ScalaOutlineContentProvider()
  private var fOpenAndLinkWithEditorHelper: org.eclipse.ui.OpenAndLinkWithEditorHelper = _

  override def createControl(parent: Composite) = {

    super.createControl(parent)

    val viewer = getTreeViewer()
    viewer.setContentProvider(contentProvider)
    viewer.setLabelProvider(new ScalaOutlineLabelProvider())
    viewer.addSelectionChangedListener(this)
    if (input != null)
      viewer.setInput(input)
    fOpenAndLinkWithEditorHelper = new org.eclipse.ui.OpenAndLinkWithEditorHelper(viewer) {

      override def activate(selection: ISelection) = {
        fEditor.doSelectionChanged(selection)
        getSite().getPage().activate(fEditor)
      }

      override def linkToEditor(selection: ISelection) = {
        fEditor.doSelectionChanged(selection)

      }

      override def open(selection: ISelection, activate: Boolean) = {
        fEditor.doSelectionChanged(selection)
        if (activate)
          getSite().getPage().activate(fEditor)
      }

    }
    fOpenAndLinkWithEditorHelper.setLinkWithEditor(true)
    val site = getSite()
    val actionBars = site.getActionBars()
    registerToolbarActions(actionBars)
  }

  def registerToolbarActions(actionBars: IActionBars) = {
    val toolBarManager = actionBars.getToolBarManager()

    val fCollapseAllAction = new CollapseAllAction(getTreeViewer)
    fCollapseAllAction.setActionDefinitionId(CollapseAllHandler.COMMAND_ID)
    toolBarManager.add(fCollapseAllAction)
    toolBarManager.add(new LexicalSortingAction(getTreeViewer))
    toolBarManager.add(new PublicOnlyAction(contentProvider, getTreeViewer))
  }

  def setInput(input: Object): Unit = {
    this.input = input
    update()
  }

  def getInput: RootNode = input.asInstanceOf[RootNode]
  /**
   * Updates the outline page.
   */

  def update(): Unit = {
    val viewer = getTreeViewer()
    if (viewer != null) {
      val control = viewer.getControl()
      if (control != null && !control.isDisposed()) {
        control.setRedraw(false)
        viewer.setInput(input)
        viewer.expandAll()
        if (ScalaPlugin().getPreferenceStore().getBoolean(EditorPreferencePage.P_INITIAL_IMPORT_FOLD))
          OutlineHelper.foldImportNodes(viewer, input)
        control.setRedraw(true)
      }
    }
  }

  def update(delta: (Iterable[Node], Iterable[Node])) = {
    val viewer = getTreeViewer()
    delta._1.foreach(n => viewer.refresh(n, false))
    delta._2.foreach(n => viewer.update(n, null))
  }
}