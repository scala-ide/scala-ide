/*
 * SCALA LICENSE
 *
 * Copyright (C) 2011-2012 Artima, Inc. All rights reserved.
 *
 * This software was developed by Artima, Inc.
 *
 * Permission to use, copy, modify, and distribute this software in source
 * or binary form for any purpose with or without fee is hereby granted,
 * provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the EPFL nor the names of its contributors
 *    may be used to endorse or promote products derived from this
 *    software without specific prior written permission.
 *
 *
 * THIS SOFTWARE IS PROVIDED BY THE REGENTS AND CONTRIBUTORS ``AS IS'' AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE REGENTS OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
 * OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 */

package scala.tools.eclipse.scalatest.ui

import org.eclipse.ui.part.PageBook
import org.eclipse.swt.widgets.Composite
import org.eclipse.jface.viewers.TreeViewer
import org.eclipse.jface.viewers.ITreeContentProvider
import org.eclipse.jface.viewers.Viewer
import org.eclipse.jface.viewers.LabelProvider
import org.eclipse.jface.viewers.StyledCellLabelProvider
import java.text.NumberFormat
import org.eclipse.jface.viewers.StyledString
import java.text.MessageFormat
import org.eclipse.swt.SWT
import org.eclipse.jdt.internal.ui.viewsupport.ColoringLabelProvider
import org.eclipse.jface.viewers.DelegatingStyledCellLabelProvider.IStyledLabelProvider
import org.eclipse.swt.graphics.Image
import org.eclipse.jface.viewers.LabelProviderChangedEvent
import org.eclipse.swt.widgets.Control
import org.eclipse.jface.viewers.StructuredViewer
import org.eclipse.jface.action.Action
import scala.tools.eclipse.ScalaProject
import org.eclipse.jdt.core.JavaCore
import org.eclipse.core.resources.ResourcesPlugin
import org.eclipse.jdt.ui.JavaUI
import org.eclipse.swt.events.SelectionAdapter
import org.eclipse.swt.events.SelectionEvent
import org.eclipse.jdt.internal.ui.viewsupport.SelectionProviderMediator
import org.eclipse.jface.viewers.IStructuredSelection
import org.eclipse.jface.dialogs.MessageDialog
import org.eclipse.ui.PlatformUI
import org.eclipse.ui.part.FileEditorInput
import org.eclipse.ui.texteditor.ITextEditor
import org.eclipse.jface.viewers.ISelectionChangedListener
import org.eclipse.jface.viewers.SelectionChangedEvent
import org.eclipse.jface.action.IMenuManager
import org.eclipse.jface.action.MenuManager
import org.eclipse.jface.action.IMenuListener
import org.eclipse.debug.ui.DebugUITools
import scala.tools.eclipse.scalatest.launching.ScalaTestLaunchDelegate
import RerunHelper._
import org.eclipse.debug.internal.ui.DebugUIPlugin
import org.eclipse.debug.ui.IDebugUIConstants
import org.eclipse.jface.viewers.StructuredSelection
import org.eclipse.jface.viewers.ViewerFilter

class ScalaTestViewer(parent: Composite, fTestRunnerPart: ScalaTestRunnerViewPart) {
  
  private class TestSelectionListener extends ISelectionChangedListener {
    def selectionChanged(event: SelectionChangedEvent) {
      handleSelected()
    }
  }

  private var fViewerbook: PageBook = null
  private var fTreeViewer: TreeViewer = null
  private var fTreeContentProvider: TestSessionTreeContentProvider = null
  private var fTreeLabelProvider: TestSessionLabelProvider = null
  private var fSelectionProvider: SelectionProviderMediator = null
  
  private var fTreeNeedsRefresh = false
  private var fNeedUpdate: Set[Node] = null
  private var fAutoScrollTarget: Node = null
  
  private var fAutoClose: List[Node] = Nil
  private var fAutoExpand: List[Node] = Nil
  
  private var fLayoutMode: Int = ScalaTestRunnerViewPart.LAYOUT_HIERARCHICAL
  
  private val fFailedTestsOnlyFilter = new FailedTestsOnlyFilter()
  
  createTestViewers(parent)

  registerViewersRefresh()

  initContextMenu()
  
  private def initContextMenu() {
    val menuMgr = new MenuManager("#PopupMenu"); //$NON-NLS-1$
    menuMgr.setRemoveAllWhenShown(true)
    menuMgr.addMenuListener(new IMenuListener() {
      def menuAboutToShow(manager: IMenuManager) {
        handleMenuAboutToShow(manager)
      }
    })
    fTestRunnerPart.getSite.registerContextMenu(menuMgr, fSelectionProvider)
    val menu = menuMgr.createContextMenu(fViewerbook);
    fTreeViewer.getTree.setMenu(menu);
    //fTableViewer.getTable().setMenu(menu);
  }
  
  private def createTestViewers(parent: Composite) {
    fViewerbook = new PageBook(parent, SWT.NULL);

    fTreeViewer = new TreeViewer(fViewerbook, SWT.V_SCROLL | SWT.SINGLE);
    fTreeViewer.setUseHashlookup(true);
    fTreeContentProvider = new TestSessionTreeContentProvider();
    fTreeViewer.setContentProvider(fTreeContentProvider);
	fTreeLabelProvider = new TestSessionLabelProvider(fTestRunnerPart, ScalaTestRunnerViewPart.LAYOUT_HIERARCHICAL);
	fTreeViewer.setLabelProvider(new ColoringLabelProvider(fTreeLabelProvider));

    /*fTableViewer = new TableViewer(fViewerbook, SWT.V_SCROLL | SWT.H_SCROLL | SWT.SINGLE);
    fTableViewer.setUseHashlookup(true);
    fTableContentProvider= new TestSessionTableContentProvider();
    fTableViewer.setContentProvider(fTableContentProvider);
    fTableLabelProvider= new TestSessionLabelProvider(fTestRunnerPart, TestRunnerViewPart.LAYOUT_FLAT);
    fTableViewer.setLabelProvider(new ColoringLabelProvider(fTableLabelProvider));*/

    //fSelectionProvider= new SelectionProviderMediator(new StructuredViewer[] { fTreeViewer, fTableViewer }, fTreeViewer);
	fSelectionProvider= new SelectionProviderMediator(Array[StructuredViewer](fTreeViewer), fTreeViewer)
    fSelectionProvider.addSelectionChangedListener(new TestSelectionListener())
    val openSourceCodeListener= new OpenSourceCodeListener(fTestRunnerPart, fSelectionProvider)
    fTreeViewer.getTree().addSelectionListener(openSourceCodeListener)
    //fTableViewer.getTable().addSelectionListener(testOpenListener);

    //fTestRunnerPart.getSite().setSelectionProvider(fSelectionProvider);

    fViewerbook.showPage(fTreeViewer.getTree());
  }
  
  def handleMenuAboutToShow(manager: IMenuManager) {
    val selection = fSelectionProvider.getSelection.asInstanceOf[IStructuredSelection]
    if (!selection.isEmpty) {
      val node = selection.getFirstElement.asInstanceOf[Node]
      node match {
        case test: TestModel => 
          test.rerunner match {
            case Some(rerunner) => 
              manager.add(new RerunTestAction("Rerun Test", fTestRunnerPart, rerunner, test.suiteId, test.testName))
            case None => 
          }
        case suite: SuiteModel => 
          suite.rerunner match {
            case Some(rerunner) => 
              manager.add(new RerunSuiteAction("Rerun Suite", fTestRunnerPart, rerunner, suite.suiteId))
            case None =>
          }
        case _ =>
      }
    }
  }
  
  def registerViewersRefresh() {
	synchronized {
      fTreeNeedsRefresh= true
      //fTableNeedsRefresh= true
      //clearUpdateAndExpansion()
    }
  }
  
  private def clearUpdateAndExpansion() {
    fNeedUpdate = Set[Node]()
    fAutoClose = List[Node]()
  }
  
  def registerNodeAdded(node: Node) {
    synchronized {
      //TODO: performance: would only need to refresh parent of added element
      fTreeNeedsRefresh= true
      //fTableNeedsRefresh= true;
    }
  }

  def registerViewerUpdate(node: Node) {
    synchronized {
      fNeedUpdate += node
    }
  }

  def clearAutoExpand() {
    synchronized {
      fAutoExpand = List.empty[Node]
    }
  }

  def registerAutoScrollTarget(node: Node) {
    fAutoScrollTarget = node;
  }

  def registerFailedForAutoScroll(node: Node) {
    synchronized {
      /*val parent = fTreeContentProvider.getParent(node)
      if (parent != null)
        fAutoExpand += parent.asInstanceOf[Node]*/
      fAutoExpand = node :: fAutoExpand
    }
  }

  def expandFirstLevel() {
    fTreeViewer.expandToLevel(2)
  }
  
  def getTestViewerControl: Control = fViewerbook
  
  private def getActiveViewer(): StructuredViewer = {
    //if (fLayoutMode == ScalaTestRunnerViewPart.LAYOUT_HIERARCHICAL)
      return fTreeViewer;
    //else
      //return fTableViewer;
  }
  
  private def getActiveViewerNeedsRefresh: Boolean = {
    //if (fLayoutMode == ScalaTestRunnerViewPart.LAYOUT_HIERARCHICAL)
      return fTreeNeedsRefresh;
    //else
      //return fTableNeedsRefresh;
  }

  private def setActiveViewerNeedsRefresh(needsRefresh: Boolean) {
    //if (fLayoutMode == TestRunnerViewPart.LAYOUT_HIERARCHICAL)
      fTreeNeedsRefresh= needsRefresh;
    //else
      //fTableNeedsRefresh= needsRefresh;
  }
  
  def processChangesInUI() {
    if (fTestRunnerPart.getSession == null) {
      registerViewersRefresh()
      fTreeNeedsRefresh= false
      //fTableNeedsRefresh= false
      fTreeViewer.setInput(null)
      //fTableViewer.setInput(null)
      return
    }

    val testRoot = fTestRunnerPart.getSession.rootNode

    val viewer = getActiveViewer()
    if (getActiveViewerNeedsRefresh) {
      clearUpdateAndExpansion()
      setActiveViewerNeedsRefresh(false)
      viewer.setInput(testRoot)
    } 
    else {
      var toUpdate: Array[Node] = null
      synchronized {
        toUpdate= fNeedUpdate.toArray
        fNeedUpdate = Set.empty[Node]
      }
      if (!fTreeNeedsRefresh && toUpdate.length > 0) {
        /*if (fTreeHasFilter)
          for (Object element : toUpdate)
            updateElementInTree((TestElement) element);
        else {*/
          var toUpdateWithParents = Set[Node]()
          toUpdateWithParents ++= toUpdate
          for (node <- toUpdate) {
            var parent= node.parent
            while (parent != null) {
              toUpdateWithParents += parent
              parent = parent.parent
            }
          }
          val nullStringArray: Array[String] = null
          val toUpdateWithParentsArray: Array[Node] = toUpdateWithParents.toArray
          fTreeViewer.update(toUpdateWithParentsArray, nullStringArray)
        //}
      }
      /*if (! fTableNeedsRefresh && toUpdate.length > 0) {
          if (fTableHasFilter)
            for (Object element : toUpdate)
              updateElementInTable((TestElement) element);
          else
            fTableViewer.update(toUpdate, null);
      }*/
    }
    //autoScrollInUI()
  }
  
  def autoExpandFailedTests() {

    synchronized {
      for (node <- fAutoExpand) {
        //fTreeViewer.setExpandedState(node, true)
        fTreeViewer.reveal(node)
      }
    }

    val current = fAutoScrollTarget
    fAutoScrollTarget = null
    
    if (fAutoExpand.length > 0) {
      val last = fAutoExpand.last
      fTreeViewer.reveal(last)
      getActiveViewer.setSelection(new StructuredSelection(last), true)
    }
  }
  
  def selectedNode = {
    val selection = getActiveViewer.getSelection.asInstanceOf[IStructuredSelection]
    if (!selection.isEmpty)
	  Some(selection.getFirstElement().asInstanceOf[Node])
    else
      None
  }
  
  def selectNode(node: Node) {
    fTreeViewer.reveal(node)
    getActiveViewer.setSelection(new StructuredSelection(node), true)
  }
  
  private def handleSelected() {
    val selection = fSelectionProvider.getSelection.asInstanceOf[IStructuredSelection]
    val node = 
      if (selection.size == 1)
        Some(selection.getFirstElement.asInstanceOf[Node])
      else
        None
    fTestRunnerPart.handleTestSelected(node)
  }
  
  def setShowFailedTestsOnly(failedTestsOnly: Boolean) {
    synchronized {
      val viewer = getActiveViewer()
      setActiveViewerNeedsRefresh(true)
      viewer.setInput(null)
      if (failedTestsOnly)
        viewer.addFilter(fFailedTestsOnlyFilter)
      else
        viewer.removeFilter(fFailedTestsOnlyFilter)
      processChangesInUI()
      autoExpandFailedTests()
    }
  }
}

private class TestSessionTreeContentProvider extends ITreeContentProvider {
  
  private val NO_CHILDREN = Array.empty[AnyRef]
  
  def dispose() {
  }
  
  def getChildren(parentElement: AnyRef): Array[AnyRef] = {
    Array.empty[AnyRef] ++ parentElement.asInstanceOf[Node].children
  }
  
  def getElements(inputElement: AnyRef): Array[AnyRef] = {
    Array.empty[AnyRef] ++ inputElement.asInstanceOf[RunModel].children
  }

  def getParent(element: AnyRef): AnyRef = {
    element.asInstanceOf[Node].parent
  }

  def hasChildren(element: AnyRef): Boolean = {
    element.asInstanceOf[Node].hasChildren
  }

  def inputChanged(viewer: Viewer, oldInput: AnyRef, newInput: AnyRef) {
  }
}

private class TestSessionLabelProvider(fTestRunnerPart: ScalaTestRunnerViewPart, fLayoutMode: Int) extends LabelProvider with IStyledLabelProvider {

  private var fShowTime = true
  private val timeFormat: NumberFormat = NumberFormat.getNumberInstance()
  
  timeFormat.setGroupingUsed(true)
  timeFormat.setMinimumFractionDigits(3)
  timeFormat.setMaximumFractionDigits(3)
  timeFormat.setMinimumIntegerDigits(1)
  
  def getStyledText(element: AnyRef): StyledString = {
    val label= getSimpleLabel(element)
    if (label == null) {
      return new StyledString(element.toString())
    }
    val text = new StyledString(label)

    val duration = 
      element match {
        case test: TestModel => test.duration
        case scope: ScopeModel => None
        case suite: SuiteModel => suite.duration
        case run: RunModel => run.duration
        case info: InfoModel => None
      }
    return addElapsedTime(text, duration)
  }
  
  private def addElapsedTime(styledString: StyledString, time: Option[Long]): StyledString = {
    val string = styledString.getString()
    val decorated= addElapsedTime(string, time)
	StyledCellLabelProvider.styleDecoratedString(decorated, StyledString.COUNTER_STYLER, styledString);
  }

  private def addElapsedTime(string: String, time: Option[Long]): String = {
    time match {
      case Some(time) => 
        val seconds = time / 1000.0
        if (!fShowTime || seconds == Double.NaN)
          string
        else {
          val formattedTime = timeFormat.format(seconds);
          string + " (" + formattedTime + " s)"
        }
      case None => 
        string
    }
  }
  
  private def getSimpleLabel(element: AnyRef): String = {
    element match {
      case test: TestModel => test.testText
      case scope: ScopeModel => scope.message
      case suite: SuiteModel => suite.suiteName
      case run: RunModel => "Run"
      case info: InfoModel => info.message
      case _ => element.toString
    }
  }
  
  override def getText(element: AnyRef): String = {
    val label = getSimpleLabel(element);
    if (label == null) {
      return element.toString();
    }
    val duration = 
      element match {
        case test: TestModel => test.duration
        case scope: ScopeModel => None
        case suite: SuiteModel => suite.duration
        case run: RunModel => run.duration
        case info: InfoModel => None
      }
    return addElapsedTime(label, duration)
  }
  
  override def getImage(element: AnyRef): Image = {
    element match {
      case test: TestModel => 
        test.status match {
          case TestStatus.STARTED =>
            fTestRunnerPart.testRunIcon
          case TestStatus.SUCCEEDED =>
            fTestRunnerPart.testSucceedIcon
          case TestStatus.FAILED => 
            fTestRunnerPart.testFailedIcon
          case TestStatus.IGNORED => 
            fTestRunnerPart.testIgnoredIcon
          case TestStatus.PENDING => 
            fTestRunnerPart.testIgnoredIcon
          case TestStatus.CANCELED =>
            fTestRunnerPart.testIgnoredIcon
        }
      case scope: ScopeModel => 
        fTestRunnerPart.scopeIcon
      case suite: SuiteModel => 
        suite.status match {
          case SuiteStatus.STARTED => 
            fTestRunnerPart.suiteRunIcon
          case SuiteStatus.SUCCEED =>
            fTestRunnerPart.suiteSucceedIcon
          case SuiteStatus.FAILED => 
            fTestRunnerPart.suiteFailIcon
          case SuiteStatus.ABORTED =>
            fTestRunnerPart.suiteAbortedIcon
        }
      case info: InfoModel => 
        fTestRunnerPart.infoIcon
      case _ => 
        throw new IllegalArgumentException(String.valueOf(element))
    }
  }

  def setShowTime(showTime: Boolean) {
    fShowTime = showTime
    fireLabelProviderChanged(new LabelProviderChangedEvent(this));
  }
}

private class OpenSourceCodeListener(fTestRunnerPart: ScalaTestRunnerViewPart, fSelectionProvider: SelectionProviderMediator) extends SelectionAdapter {
  override def widgetDefaultSelected(e: SelectionEvent) {
    val selection= fSelectionProvider.getSelection().asInstanceOf[IStructuredSelection]
    if (selection.size() != 1)
      return
      
    val node = selection.getFirstElement.asInstanceOf[Node]
    val action = new GoToSourceAction(node, fTestRunnerPart)
    if (action.isEnabled)
      action.run()
  }
}

private class GoToSourceAction(node: Node, fTestRunnerPart: ScalaTestRunnerViewPart) extends Action {
  
  override def run() {
    node match {
      case test: TestModel => 
        goToLocation(test.location, test.errorDepth, test.errorStackTrace)
      case scope: ScopeModel =>
        goToLocation(scope.location, None, None)
      case info: InfoModel =>
        goToLocation(info.location, info.errorDepth, info.errorStackTrace)
      case suite: SuiteModel =>
        goToLocation(suite.location, suite.errorDepth, suite.errorStackTrace)
      case _ =>
    }
  }
  
  private def getShell = fTestRunnerPart.getSite.getShell
  
  private def notifyLocationNotFound() {
    MessageDialog.openError(getShell, "Cannot Open Editor", 
                            "Cannot open source location of the selected element")
  }
  
  def openSourceFileLineNumber(scProj: ScalaProject, fileName: String, lineNumber: Int) {
    val sourceFile = scProj.allSourceFiles.find(file => file.getName == fileName)
    sourceFile match {
      case Some(sourceFile) => 
        val page = PlatformUI.getWorkbench.getActiveWorkbenchWindow.getActivePage
        val desc = PlatformUI.getWorkbench.getEditorRegistry.getDefaultEditor(sourceFile.getName)
        val editorPart = page.openEditor(new FileEditorInput(sourceFile), desc.getId)
        editorPart match {
          case textEditor: ITextEditor => 
            val document = textEditor.getDocumentProvider.getDocument(textEditor.getEditorInput)
            val lineOffset = document.getLineOffset(lineNumber - 1)
            val lineLength = document.getLineLength(lineNumber - 1)
            textEditor.selectAndReveal(lineOffset, lineLength)
          case _ =>
            notifyLocationNotFound()
        }
      case None => 
        notifyLocationNotFound()
    }
  }
  
  private def goToLocation(location: Option[Location], errorDepth: Option[Int], errorStackTraces: Option[Array[StackTraceElement]]) {
    location match {
      case Some(location) =>
        location match {
          case topOfClass: TopOfClass => 
            val scProj = getScalaProject(fTestRunnerPart.getSession.projectName)
            scProj match {
              case Some(scProj) => 
                val iType = scProj.javaProject.findType(topOfClass.className)
                if (iType != null)
                  JavaUI.openInEditor(iType, true, true)
                else
                  notifyLocationNotFound()
              case None =>
                notifyLocationNotFound()
            }
          case topOfMethod: TopOfMethod => 
            val scProj = getScalaProject(fTestRunnerPart.getSession.projectName)
            scProj match {
              case Some(scProj) => 
                val iType = scProj.javaProject.findType(topOfMethod.className)
                val methodId = topOfMethod.methodId
                val methodName = methodId.substring(methodId.lastIndexOf('.') + 1, methodId.lastIndexOf('('))
                val methodRawParamTypes = methodId.substring(methodId.lastIndexOf('(') + 1, methodId.length - 1)
                val methodParamTypes = 
                  if (methodRawParamTypes.length > 0)
                    methodRawParamTypes.split(",").map(paramType => paramType.trim)
                  else
                    Array.empty[String]
                val method = iType.getMethod(methodName, methodParamTypes)
                if (method != null)
                  JavaUI.openInEditor(method, true, true)
                else
                  notifyLocationNotFound()
              case None =>
                notifyLocationNotFound()
            }
          case lineInFile: LineInFile =>
            val scProj = getScalaProject(fTestRunnerPart.getSession.projectName)
            scProj match {
              case Some(scProj) =>
                val fileName = lineInFile.fileName
                val lineNumber = lineInFile.lineNumber
                openSourceFileLineNumber(scProj, fileName, lineNumber)
              case None => 
                notifyLocationNotFound()
            }
          case SeeStackDepthException =>
            val scProj = getScalaProject(fTestRunnerPart.getSession.projectName)
            scProj match {
              case Some(scProj) =>
                if (errorDepth.isDefined && errorStackTraces.isDefined) {
                  val errorDepthValue = errorDepth.get
                  if (errorDepthValue >= 0) {
                    val stackTrace = errorStackTraces.get(errorDepthValue)
                    val fileName = stackTrace.fileName
                    val lineNumber = stackTrace.lineNumber
                    openSourceFileLineNumber(scProj, fileName, lineNumber)
                  }
                  else
                    notifyLocationNotFound()
                }
                else
                  notifyLocationNotFound()
              case None => 
                notifyLocationNotFound()
            }
        }  
      case None =>
        notifyLocationNotFound()
    }
  }
  
  private def getScalaProject(projectName: String): Option[ScalaProject] = {
    val model = JavaCore.create(ResourcesPlugin.getWorkspace.getRoot)
    val javaProject = model.getJavaProject(projectName)
    if (javaProject != null)
      Some(ScalaProject(javaProject.getProject))
    else
      None
  }
}

object RerunHelper {
  
  def rerun(fTestRunnerPart: ScalaTestRunnerViewPart, delegate: ScalaTestLaunchDelegate, stArgs: String) {
    val launch = fTestRunnerPart.getSession.fLaunch
    if (launch != null) {
      val launchConfig = launch.getLaunchConfiguration
      if (launchConfig != null) {
        val buildBeforeLaunch = DebugUIPlugin.getDefault().getPreferenceStore().getBoolean(IDebugUIConstants.PREF_BUILD_BEFORE_LAUNCH)
        if (buildBeforeLaunch)
          ScalaTestPlugin.doBuild()
        delegate.launchScalaTest(launchConfig, launch.getLaunchMode, launch, null, stArgs)
      }
      else
        MessageDialog.openError(fTestRunnerPart.getSite.getShell, "Error", 
                            "Cannot find launch configuration.")
    }
    else
      MessageDialog.openError(fTestRunnerPart.getSite.getShell, "Error", 
                            "Cannot find launch object.")
  }
  
}

private class RerunSuiteAction(actionName: String, fTestRunnerPart: ScalaTestRunnerViewPart, suiteClassName: String, 
                               suiteId: String) extends Action(actionName) {
  override def run() {
    val delegate = new ScalaTestLaunchDelegate()
    val stArgs = delegate.getScalaTestArgsForSuite(suiteClassName, suiteId)
    rerun(fTestRunnerPart, delegate, stArgs)
  }
}

private class RerunTestAction(actionName: String, fTestRunnerPart: ScalaTestRunnerViewPart, suiteClassName: String, 
                               suiteId: String, testName: String) extends Action(actionName) {
  override def run() {
    val delegate = new ScalaTestLaunchDelegate()
    val stArgs = delegate.getScalaTestArgsForTest(suiteClassName, suiteId, testName)
    rerun(fTestRunnerPart, delegate, stArgs)
  }
}

private class FailedTestsOnlyFilter extends ViewerFilter {
  
  override def select(viewer: Viewer, parentElement: AnyRef, element: AnyRef): Boolean = {
    select(element.asInstanceOf[Node])
  }
  
  private def select(node: Node): Boolean = {
    node match {
      case test: TestModel => 
        test.status == TestStatus.FAILED
      case suite: SuiteModel => 
        suite.status == SuiteStatus.FAILED
      case _ =>
        true
    }
  }
  
}