package org.scalaide.ui.internal.editor
import org.eclipse.ui.views.contentoutline.ContentOutlinePage
import org.eclipse.swt.widgets.Composite
import org.eclipse.jface.viewers.ISelectionChangedListener
import org.eclipse.jface.viewers.SelectionChangedEvent
import org.eclipse.jface.viewers.ISelection

class ScalaOutlinePage(val fEditor:ScalaSourceFileEditor) extends ContentOutlinePage {

  private var input: Object = null
  var fOpenAndLinkWithEditorHelper : org.eclipse.ui.OpenAndLinkWithEditorHelper = _
  override def createControl(parent: Composite)= {

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


      override def open(selection: ISelection, activate: Boolean)= {
        fEditor.doSelectionChanged(selection);
        if (activate)
          getSite().getPage().activate(fEditor);
      }

    };
    fOpenAndLinkWithEditorHelper.setLinkWithEditor(true)
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
  def update(delta:(Iterable[Node], Iterable[Node]))={
    val viewer = getTreeViewer()
    delta._1.foreach(n=> viewer.refresh(n, false))
    delta._2.foreach(n=> viewer.update(n,null))
  }
}