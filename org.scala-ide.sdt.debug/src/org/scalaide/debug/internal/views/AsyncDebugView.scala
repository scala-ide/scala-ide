package org.scalaide.debug.internal.views

import org.eclipse.debug.core.model.IStackFrame
import org.eclipse.debug.core.model.IVariable
import org.eclipse.debug.ui.AbstractDebugView
import org.eclipse.debug.ui.DebugUITools
import org.eclipse.debug.ui.IDebugUIConstants
import org.eclipse.debug.ui.contexts.AbstractDebugContextProvider
import org.eclipse.debug.ui.contexts.DebugContextEvent
import org.eclipse.debug.ui.contexts.IDebugContextListener
import org.eclipse.debug.ui.contexts.IDebugContextService
import org.eclipse.jface.action.IToolBarManager
import org.eclipse.jface.viewers.IColorProvider
import org.eclipse.jface.viewers.ISelection
import org.eclipse.jface.viewers.ISelectionChangedListener
import org.eclipse.jface.viewers.IStructuredSelection
import org.eclipse.jface.viewers.ITreeContentProvider
import org.eclipse.jface.viewers.LabelProvider
import org.eclipse.jface.viewers.SelectionChangedEvent
import org.eclipse.jface.viewers.TableViewer
import org.eclipse.jface.viewers.Viewer
import org.eclipse.swt.SWT
import org.eclipse.swt.graphics.Color
import org.eclipse.swt.graphics.Image
import org.eclipse.swt.widgets.Composite
import org.eclipse.swt.widgets.Display
import org.eclipse.ui.ISelectionListener
import org.eclipse.ui.IViewSite
import org.eclipse.ui.IWorkbenchPart
import org.scalaide.debug.internal.async._
import org.scalaide.debug.internal.model.ScalaDebugModelPresentation
import org.scalaide.debug.internal.model.ScalaDebugTarget
import org.scalaide.debug.internal.model.ScalaObjectReference
import org.scalaide.debug.internal.model.ScalaStackFrame
import org.scalaide.debug.internal.model.ScalaThread
import org.scalaide.logging.HasLogger

class AsyncDebugView extends AbstractDebugView with IDebugContextListener with HasLogger {

  private var viewer: TableViewer = _

  private var currentFrame: Option[ISelection] = None

  override protected def configureToolBar(tbm: IToolBarManager): Unit = {}
  override protected def createActions(): Unit = {}

  override protected def createViewer(parent: Composite): Viewer = {
    import Utils._
    viewer = new TableViewer(parent, SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL | SWT.VIRTUAL)
    viewer.setContentProvider(StackFrameContentProvider)
    viewer.setLabelProvider(StackFrameLabelProvider)

    viewer.addSelectionChangedListener(stackFrameSelectionChanged _)
    viewer
  }

  override def init(site: IViewSite): Unit = {
    import Utils._
    super.init(site)

    val service = debugService
    service.addDebugContextProvider(asyncDebugContextProvider)
    // TODO remove this?
//    service.addDebugContextListener(this)

    val selectionService = site.getWorkbenchWindow().getSelectionService()
    selectionService.addSelectionListener(IDebugUIConstants.ID_VARIABLE_VIEW, variableViewSelectionChanged _)
  }

  override def dispose(): Unit = {
    debugService.removeDebugContextProvider(asyncDebugContextProvider)
    super.dispose()
  }

  def stackFrameSelectionChanged(sce: SelectionChangedEvent): Unit = {
    currentFrame = Option(sce.getSelection())
    sce.getSelection() match {
      case se: IStructuredSelection =>
        if (!se.isEmpty)
          asyncDebugContextProvider.fireSelection(se)
      case _ =>
    }
  }

  def variableViewSelectionChanged(part: IWorkbenchPart, selection: ISelection): Unit = selection match {
    case structSel: IStructuredSelection =>
      structSel.getFirstElement match {
        case elem: IVariable =>
          updateAsyncFrame(elem)
        case _ =>
      }
    case _ =>
  }

  def updateAsyncFrame(elem: IVariable): Unit = {
    val dbgTarget = elem.getDebugTarget().asInstanceOf[ScalaDebugTarget]
    elem.getValue() match {
      case ref: ScalaObjectReference =>
        viewer.setSelection(null, true)
        val newInput = dbgTarget.retainedStack.getStackFrameForFuture(ref.underlying).getOrElse(AsyncStackTrace(Nil))
        setInputSafely(newInput)

      case _ =>
        setInputSafely(null)
    }
  }

  override protected def fillContextMenu(x$1: org.eclipse.jface.action.IMenuManager): Unit = {}
  override protected def getHelpContextId(): String = ""

  override def debugContextChanged(event: DebugContextEvent): Unit = {
    logger.info(s"Debug Event context change ${event.getContext()}")
    event.getContext match {
      case ssel: IStructuredSelection =>
        setInputSafely(ssel.getFirstElement)
      case _ =>
    }
  }

  /**
   * In can happen that this method is called when the UI widget is already disposed
   * or not yet fully initialized. In such cases we are not allowed to set an input.
   */
  private def setInputSafely(input: Any): Unit = {
    val c = viewer.getControl
    if (c != null && !c.isDisposed())
      viewer.setInput(input)
  }

  private def debugService: IDebugContextService =
    DebugUITools.getDebugContextManager().getContextService(getSite().getWorkbenchWindow())

  private object StackFrameContentProvider extends ITreeContentProvider {
    private val emptyArray = Array[Object]()

    override def getElements(inputElement: AnyRef): Array[Object] = inputElement match {
      case thread: ScalaThread     => thread.getStackFrames.asInstanceOf[Array[Object]]
      case AsyncStackTrace(frames) => frames.toArray
      case _                       => Array(inputElement) // TODO: They say big NO NO
    }

    override def getChildren(parentElement: AnyRef): Array[Object] = parentElement match {
      case frame: ScalaStackFrame     => frame.getVariables.asInstanceOf[Array[Object]]
      case AsyncStackTrace(frames)    => frames.toArray
      case AsyncStackFrame(locals, _) => locals.toArray
      case _                          => emptyArray
    }

    override def getParent(element: AnyRef): AnyRef = element match {
      case frame: ScalaStackFrame => frame.getThread
      case variable: IVariable    => null // TODO proper parent?
      case _                      => null
    }

    override def hasChildren(element: AnyRef): Boolean = element match {
      case _: AsyncStackFrame | _: AsyncStackTrace => true
      case _                                       => false
    }

    override def dispose(): Unit = {}

    override def inputChanged(viewer: Viewer, oldInput: AnyRef, newInput: AnyRef): Unit = {}
  }

  private object StackFrameLabelProvider extends LabelProvider with IColorProvider {

    val greyablePackages = Set("akka.", "scala.", "play.")

    def greyableContext(typeName: String): Boolean =
      greyablePackages.exists(typeName.startsWith)

    override def getText(elem: Object): String = elem match {
      case AsyncLocalVariable(name, value) => s"$name: ${ScalaDebugModelPresentation.computeDetail(value)}"
      case AsyncStackFrame(_, location)    => s"$location"
      case s: IStackFrame                  => s"${s.getName()}:${s.getLineNumber()}"
      case _                               => elem.toString
    }

    override def getImage(elem: Object): Image =
      DebugUITools.getImage(IDebugUIConstants.IMG_OBJS_STACKFRAME)

    override def getForeground(element: AnyRef): Color = element match {
      case AsyncStackFrame(_, location) if greyableContext(location.declaringTypeName) =>
        Display.getCurrent().getSystemColor(SWT.COLOR_GRAY)
      case _ =>
        null
    }

    override def getBackground(element: AnyRef): Color = null
  }

  private object asyncDebugContextProvider extends AbstractDebugContextProvider(this) {
    def fireSelection(se: ISelection): Unit = {
      fire(new DebugContextEvent(this, se, DebugContextEvent.ACTIVATED))
    }

    override def getActiveContext(): ISelection = {
      currentFrame.orNull
    }
  }

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
