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
import org.eclipse.debug.internal.ui.model.elements.DebugElementLabelProvider
import org.eclipse.jface.viewers.ITableLabelProvider
import scala.tools.eclipse.debug.model.ScalaDebugTarget
import org.eclipse.debug.core.model.IDebugElement
import org.eclipse.debug.core.DebugPlugin
import org.eclipse.jface.viewers.IBaseLabelProvider
import org.eclipse.jface.viewers.LabelProvider
import org.eclipse.debug.core.model.IStackFrame
import scala.tools.eclipse.debug.model.ScalaDebugModelPresentation
import org.eclipse.ui.ISelectionListener
import org.eclipse.ui.IWorkbenchPart
import org.eclipse.jface.viewers.ISelection
import com.sun.jdi.ObjectReference
import scala.tools.eclipse.debug.model.ScalaObjectReference
import com.sun.jdi.StackFrame
import scala.tools.eclipse.debug.async.AsyncStackTrace
import scala.tools.eclipse.debug.async.AsyncStackFrame
import scala.tools.eclipse.debug.async.AsyncLocalVariable
import scala.tools.eclipse.debug.async.AsyncStackFrame
import org.eclipse.ui.PlatformUI
import org.eclipse.jface.viewers.ISelectionChangedListener
import org.eclipse.jface.viewers.SelectionChangedEvent
import org.eclipse.debug.ui.contexts.AbstractDebugContextProvider
import org.eclipse.debug.internal.ui.views.variables.VariablesView
import org.eclipse.jface.viewers.ListViewer
import org.eclipse.swt.graphics.Image
import org.eclipse.jface.viewers.TableViewer
import org.eclipse.jface.viewers.IColorProvider
import org.eclipse.swt.graphics.Color
import org.eclipse.swt.widgets.Display
import scala.tools.eclipse.debug.async.AsyncStackFrame

class AsyncDebugView extends AbstractDebugView with IDebugContextListener with HasLogger {

  /** As seen from class AsyncDebugView, the missing signatures are as follows.  *  For convenience, these are usable as stub implementations.  */
  protected def configureToolBar(x$1: org.eclipse.jface.action.IToolBarManager): Unit = {}
  protected def createActions(): Unit = {}

  private var viewer: TableViewer = _

  private val greyablePackages = Set("akka.", "scala.", "play.")
  def greyableContext(typeName: String): Boolean =
    greyablePackages.exists(typeName.startsWith)
    
  
  /** Creates and returns this view's underlying viewer.
   *  The viewer's control will automatically be hooked
   *  to display a pop-up menu that other plug-ins may
   *  contribute to. Subclasses must override this method.
   *
   *  @param parent the parent control
   */
  protected def createViewer(parent: Composite): Viewer = {
    import Utils._
    //    val fPresentation = new DelegatingModelPresentation()
    //    val fPresentationContext = new DebugModelPresentationContext(IDebugUIConstants.ID_DEBUG_VIEW, this, fPresentation)
    //    viewer = new TreeModelViewer(parent,
    //      SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL | SWT.VIRTUAL,
    //      fPresentationContext)
    //viewer.setInput(DebugPlugin.getDefault().getLaunchManager())
    viewer = new TableViewer(parent, SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL | SWT.VIRTUAL)
    viewer.setContentProvider(new StackFrameProvider)
    viewer.setLabelProvider(new LabelProvider with IColorProvider {
      override def getText(elem: Object): String = elem match {
        case AsyncLocalVariable(name, value) => s"$name: ${ScalaDebugModelPresentation.computeDetail(value)}"
        case AsyncStackFrame(_, location)    => s"$location"
        case s: IStackFrame                  => s"${s.getName()}:${s.getLineNumber()}"
        case _                               => elem.toString
      }

      override def getImage(elem: Object): Image =
        DebugUITools.getImage(IDebugUIConstants.IMG_OBJS_STACKFRAME)

      def getForeground(element: AnyRef): Color = element match {
        case AsyncStackFrame(_, location) if greyableContext(location.declaringTypeName) =>
          Display.getCurrent().getSystemColor(SWT.COLOR_GRAY)
        case _ =>
          null
      }
      def getBackground(element: AnyRef): Color = null
    })

    viewer.addSelectionChangedListener(stackFrameSelectionChanged _)
    viewer
  }

  override def init(site: IViewSite) {
    import Utils._
    super.init(site)
    val service = DebugUITools.getDebugContextManager().getContextService(site.getWorkbenchWindow())
    service.addDebugContextProvider(asyncDebugContextProvider)
    //    service.addDebugContextListener(this)

    val selectionService = site.getWorkbenchWindow().getSelectionService()
    selectionService.addSelectionListener(IDebugUIConstants.ID_VARIABLE_VIEW, variableViewSelectionChanged _)
  }

  override def dispose() {
    val service = DebugUITools.getDebugContextManager().getContextService(getSite().getWorkbenchWindow())
    service.removeDebugContextProvider(asyncDebugContextProvider)
  }

  def stackFrameSelectionChanged(sce: SelectionChangedEvent) {
    currentFrame = Option(sce.getSelection())
    sce.getSelection() match {
      case se: IStructuredSelection =>
        logger.debug("Firing debug context changed!")
        //        val varView = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().findView(IDebugUIConstants.ID_VARIABLE_VIEW)
        //        varView.getViewSite().getSelectionProvider().setSelection(new ISelection { def isEmpty = true })
        //        varView.asInstanceOf[VariablesView].getViewer().setSelection(new ISelection { def isEmpty = true })
        if (!se.isEmpty) asyncDebugContextProvider.fireSelection(se)
      case _ =>
        logger.debug("Unknown selection, really?")
    }
  }

  def variableViewSelectionChanged(part: IWorkbenchPart, selection: ISelection): Unit = selection match {
    case structSel: IStructuredSelection =>
      val elem = structSel.getFirstElement()
      logger.debug(s"Changing selection to $elem")
      if (elem ne null)
        updateAsyncFrame(elem.asInstanceOf[IVariable])

    case _ => logger.info(s"Selection not understood: $selection")
  }

  def updateAsyncFrame(elem: IVariable): Unit = {
    val dbgTarget = elem.getDebugTarget().asInstanceOf[ScalaDebugTarget]
    elem.getValue() match {
      case ref: ScalaObjectReference =>
        viewer.setSelection(null, true)
        val newInput = dbgTarget.retainedStack.getStackFrameForFuture(ref.underlying).getOrElse(AsyncStackTrace(Nil))
        logger.debug(s"Setting viewer.input to $newInput")
        viewer.setInput(newInput)
      //        val varView = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().findView(IDebugUIConstants.ID_VARIABLE_VIEW)
      //        varView.asInstanceOf[AbstractDebugView].getViewer().setInput(null)

      case _ =>
        viewer.setInput(null)
        logger.debug("Unknown value")
    }
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
      //      case Seq(elems @ _*) => elems.toArray.asInstanceOf[Array[Object]]
      case AsyncStackTrace(frames) => frames.toArray
      case _                       => Array(inputElement) // TODO: They say big NO NO
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
      case AsyncStackTrace(frames)    => frames.toArray
      case AsyncStackFrame(locals, _) => locals.toArray
      case _                          => emptyArray
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
      case _: AsyncStackFrame | _: AsyncStackTrace => true
      case _                                       => false
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
      //      logger.debug(s"old: $oldInput / new: $newInput")
    }
  }

  private object asyncDebugContextProvider extends AbstractDebugContextProvider(this) {
    def fireSelection(se: ISelection) {
      fire(new DebugContextEvent(this, se, DebugContextEvent.ACTIVATED))
    }

    override def getActiveContext(): ISelection = {
      currentFrame.getOrElse(null)
    }
  }

  private var currentFrame: Option[ISelection] = None
}

object Utils {
  implicit def fnToSelectionListener(f: (IWorkbenchPart, ISelection) => Unit): ISelectionListener = new ISelectionListener {
    override def selectionChanged(part: IWorkbenchPart, selection: ISelection): Unit = {
      f(part, selection)
    }
  }

  implicit def fnToSelectionChangedListener(f: SelectionChangedEvent => Unit): ISelectionChangedListener = new ISelectionChangedListener {
    override def selectionChanged(selection: SelectionChangedEvent): Unit = {
      f(selection)
    }
  }
}