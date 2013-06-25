package scala.tools.eclipse.debug.views

import org.eclipse.debug.ui.AbstractDebugView
import org.eclipse.jface.viewers.Viewer
import org.eclipse.swt.widgets.Composite
import org.eclipse.debug.internal.ui.viewers.model.provisional.TreeModelViewer
import org.eclipse.debug.internal.ui.DelegatingModelPresentation
import org.eclipse.debug.internal.ui.views.DebugModelPresentationContext
import org.eclipse.swt.SWT
import org.eclipse.debug.ui.IDebugUIConstants
import org.eclipse.debug.ui.contexts.IDebugContextListener
import org.eclipse.debug.ui.contexts.DebugContextEvent
import scala.tools.eclipse.logging.HasLogger
import org.eclipse.ui.IViewSite
import org.eclipse.debug.ui.DebugUITools
import org.eclipse.jface.viewers.TreeViewer
import org.eclipse.jface.viewers.ITreeContentProvider
import scala.tools.eclipse.debug.model.ScalaThread
import scala.tools.eclipse.debug.model.ScalaStackFrame
import scala.tools.eclipse.debug.model.ScalaVariable
import org.eclipse.debug.core.model.IVariable
import org.eclipse.jface.viewers.IStructuredSelection

class AsyncDebugView extends AbstractDebugView with IDebugContextListener with HasLogger {

  /** As seen from class AsyncDebugView, the missing signatures are as follows.  *  For convenience, these are usable as stub implementations.  */
  protected def configureToolBar(x$1: org.eclipse.jface.action.IToolBarManager): Unit = {}
  protected def createActions(): Unit = {}

  private var viewer: TreeViewer = _

  /** Creates and returns this view's underlying viewer.
   *  The viewer's control will automatically be hooked
   *  to display a pop-up menu that other plug-ins may
   *  contribute to. Subclasses must override this method.
   *
   *  @param parent the parent control
   */
  protected def createViewer(parent: Composite): Viewer = {
    viewer = new TreeViewer(parent, SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL | SWT.VIRTUAL)
    viewer.setContentProvider(new StackFrameProvider)
    viewer
  }

  override def init(site: IViewSite) {
    super.init(site)
    val service = DebugUITools.getDebugContextManager().getContextService(site.getWorkbenchWindow())

    service.addDebugContextListener(this)
  }

  protected def fillContextMenu(x$1: org.eclipse.jface.action.IMenuManager): Unit = {}
  protected def getHelpContextId(): String = ""

  /** Notification the debug context has changed as specified by the given event.
   *
   *  @param event debug context event
   */
  def debugContextChanged(event: DebugContextEvent): Unit = {
    logger.info(s"Debug Event context change ${event.getContext()}")
    event.getContext match {
      case ssel: IStructuredSelection =>
        viewer.setInput(ssel.getFirstElement())
      case _ =>
    }
  }

  private class StackFrameProvider extends ITreeContentProvider {
    private val emptyArray = Array[Object]()
    /** {@inheritDoc}
     *  <p>
     *  <b>NOTE:</b> The returned array must not contain the given
     *  <code>inputElement</code>, since this leads to recursion issues in
     *  {@link AbstractTreeViewer} (see
     *  <a href="https://bugs.eclipse.org/9262">bug 9262</a>).
     *  </p>
     */
    def getElements(inputElement: AnyRef): Array[Object] = inputElement match {
      case thread: ScalaThread =>
        thread.getStackFrames.asInstanceOf[Array[Object]]
      case _ => emptyArray
    }

    /** Returns the child elements of the given parent element.
     *  <p>
     *  The difference between this method and <code>IStructuredContentProvider.getElements</code>
     *  is that <code>getElements</code> is called to obtain the
     *  tree viewer's root elements, whereas <code>getChildren</code> is used
     *  to obtain the children of a given parent element in the tree (including a root).
     *  </p>
     *  The result is not modified by the viewer.
     *
     *  @param parentElement the parent element
     *  @return an array of child elements
     */
    def getChildren(parentElement: AnyRef): Array[Object] = parentElement match {
      case frame: ScalaStackFrame =>
        frame.getVariables.asInstanceOf[Array[Object]]
      case _ => emptyArray
    }

    /** Returns the parent for the given element, or <code>null</code>
     *  indicating that the parent can't be computed.
     *  In this case the tree-structured viewer can't expand
     *  a given node correctly if requested.
     *
     *  @param element the element
     *  @return the parent element, or <code>null</code> if it
     *   has none or if the parent cannot be computed
     */
    def getParent(element: AnyRef): AnyRef = element match {
      case frame: ScalaStackFrame =>
        frame.getThread
      case variable: IVariable =>
        null // TODO proper parent?
      case _ => null

    }

    /** Returns whether the given element has children.
     *  <p>
     *  Intended as an optimization for when the viewer does not
     *  need the actual children.  Clients may be able to implement
     *  this more efficiently than <code>getChildren</code>.
     *  </p>
     *
     *  @param element the element
     *  @return <code>true</code> if the given element has children,
     *  and <code>false</code> if it has no children
     */
    def hasChildren(element: AnyRef): Boolean = element match {
      case _: ScalaStackFrame | _: ScalaThread => true
      case _                                   => false
    }

    /** Disposes of this content provider.
     *  This is called by the viewer when it is disposed.
     *  <p>
     *  The viewer should not be updated during this call, as it is in the process
     *  of being disposed.
     *  </p>
     */
    def dispose(): Unit = {}

    /** Notifies this content provider that the given viewer's input
     *  has been switched to a different element.
     *  <p>
     *  A typical use for this method is registering the content provider as a listener
     *  to changes on the new input (using model-specific means), and deregistering the viewer
     *  from the old input. In response to these change notifications, the content provider
     *  should update the viewer (see the add, remove, update and refresh methods on the viewers).
     *  </p>
     *  <p>
     *  The viewer should not be updated during this call, as it might be in the process
     *  of being disposed.
     *  </p>
     *
     *  @param viewer the viewer
     *  @param oldInput the old input element, or <code>null</code> if the viewer
     *   did not previously have an input
     *  @param newInput the new input element, or <code>null</code> if the viewer
     *   does not have an input
     */
    def inputChanged(viewer: Viewer, oldInput: AnyRef, newInput: AnyRef): Unit = {
      logger.debug(s"old: $oldInput / new: $newInput")
    }
  }
}