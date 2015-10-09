package org.scalaide.ui.internal.editor
import org.eclipse.ui.views.contentoutline.ContentOutlinePage
import org.eclipse.swt.widgets.Composite
import org.eclipse.jface.viewers.ISelectionChangedListener
import org.eclipse.jface.viewers.SelectionChangedEvent
import org.eclipse.jface.viewers.ISelection
import org.eclipse.ui.IActionBars
import org.eclipse.jdt.internal.ui.actions.CollapseAllAction
import org.eclipse.ui.handlers.CollapseAllHandler
import org.eclipse.jface.action.Action
import org.eclipse.jdt.internal.ui.JavaPluginImages
import org.eclipse.ui.PlatformUI
import org.eclipse.jdt.internal.ui.IJavaHelpContextIds
import org.eclipse.jdt.internal.ui.JavaPlugin
import org.eclipse.jdt.internal.ui.javaeditor.JavaEditorMessages
import org.scalaide.logging.HasLogger

class ScalaOutlinePage(val fEditor: ScalaSourceFileEditor) extends ContentOutlinePage with HasLogger {
  import org.eclipse.jface.viewers.ViewerComparator
  import org.eclipse.jface.viewers.Viewer
  class LexicalComparator extends ViewerComparator {
    override def compare(viewer: Viewer, e1: AnyRef, e2: AnyRef): Int = {
      def cn(e: AnyRef): (Int, String) = e match {
        case n: PackageNode => (0, n.name)
        case n: ContainerNode => (1, n.name)
        case n: Node => (2, n.name)
        case _ => (3, "")
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
  private var input: Object = null
  var fOpenAndLinkWithEditorHelper: org.eclipse.ui.OpenAndLinkWithEditorHelper = _
  class LexicalSortingAction extends Action {
    PlatformUI.getWorkbench().getHelpSystem().setHelp(this, IJavaHelpContextIds.LEXICAL_SORTING_OUTLINE_ACTION);
    setText("Sort");
    JavaPluginImages.setLocalImageDescriptors(this, "alphab_sort_co.gif"); //$NON-NLS-1$
    setToolTipText("Sort");
    setDescription("Enable Sort");

    val checked = JavaPlugin.getDefault().getPreferenceStore().getBoolean("LexicalSortingAction.isChecked"); //$NON-NLS-1$
    valueChanged(checked, false);
    override def run() = {
      valueChanged(isChecked, true)
    }
    private def valueChanged(checked: Boolean, save: Boolean) = {
      setChecked(checked)
      if (checked)
        getTreeViewer.setComparator(new LexicalComparator)
      else
        getTreeViewer.setComparator(new ScalaComparator)
    }
  }
  override def createControl(parent: Composite) = {

    super.createControl(parent);

    val viewer = getTreeViewer()
    viewer.setContentProvider(new ScalaContentProvider());
    viewer.setLabelProvider(new ScalaLabelProvider());
    viewer.addSelectionChangedListener(this);
    if (input != null)
      viewer.setInput(input);
    fOpenAndLinkWithEditorHelper = new org.eclipse.ui.OpenAndLinkWithEditorHelper(viewer) {

      override def activate(selection: ISelection) = {
        fEditor.doSelectionChanged(selection)
        getSite().getPage().activate(fEditor)
      }

      override def linkToEditor(selection: ISelection) = {
        fEditor.doSelectionChanged(selection);

      }

      override def open(selection: ISelection, activate: Boolean) = {
        fEditor.doSelectionChanged(selection);
        if (activate)
          getSite().getPage().activate(fEditor);
      }

    };
    fOpenAndLinkWithEditorHelper.setLinkWithEditor(true)
    val site = getSite();
    val actionBars = site.getActionBars();
    registerToolbarActions(actionBars);
  }

  def registerToolbarActions(actionBars: IActionBars) = {
    val toolBarManager = actionBars.getToolBarManager()

    val fCollapseAllAction = new CollapseAllAction(getTreeViewer)
    fCollapseAllAction.setActionDefinitionId(CollapseAllHandler.COMMAND_ID)
    toolBarManager.add(fCollapseAllAction)
    toolBarManager.add(new LexicalSortingAction)
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
    val viewer = getTreeViewer();

    if (viewer != null) {
      val control = viewer.getControl();
      if (control != null && !control.isDisposed()) {
        control.setRedraw(false);
        viewer.setInput(input);
        viewer.expandAll();
        control.setRedraw(true);
      }
    }
  }
  def update(delta: (Iterable[Node], Iterable[Node])) = {
    val viewer = getTreeViewer()
    delta._1.foreach(n => viewer.refresh(n, false))
    delta._2.foreach(n => viewer.update(n, null))
  }
}