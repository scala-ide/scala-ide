package scala.tools.eclipse.scalatest.ui

import org.eclipse.ui.part.ViewPart
import org.eclipse.jdt.internal.junit.ui.JUnitProgressBar
import org.eclipse.swt.widgets.Composite
import org.eclipse.swt.layout.GridLayout
import org.eclipse.swt.custom.SashForm
import org.eclipse.swt.SWT
import org.eclipse.ui.IMemento
import java.util.Observer
import java.util.Observable
import scala.xml.Elem
import org.eclipse.swt.layout.GridData
import org.eclipse.ui.progress.UIJob
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.core.runtime.IStatus
import org.eclipse.core.runtime.Status
import ScalaTestRunnerViewPart._
import scala.tools.eclipse.scalatest.ScalaTestImages
import org.eclipse.swt.custom.ViewForm
import org.eclipse.swt.widgets.Layout
import org.eclipse.swt.graphics.Point
import org.eclipse.jdt.internal.junit.ui.TestViewer
import org.eclipse.swt.custom.CLabel
import org.eclipse.swt.widgets.ToolBar
import org.eclipse.jface.action.Action
import org.eclipse.ui.handlers.IHandlerService
import org.eclipse.core.commands.AbstractHandler
import org.eclipse.core.commands.ExecutionEvent
import org.eclipse.debug.ui.DebugUITools
import org.eclipse.debug.core.ILaunch
import scala.tools.eclipse.scalatest.launching.ScalaTestLaunchDelegate
import org.eclipse.debug.internal.ui.DebugUIPlugin
import org.eclipse.debug.ui.IDebugUIConstants
import org.eclipse.ui.actions.ActionFactory
import scala.collection.mutable.ListBuffer
import scala.annotation.tailrec
import org.eclipse.jface.dialogs.MessageDialog
import org.eclipse.swt.events.ControlListener
import org.eclipse.swt.events.ControlEvent
import org.eclipse.core.runtime.jobs.Job
import org.eclipse.core.runtime.jobs.ILock
import org.eclipse.jface.action.Separator
import org.eclipse.jface.action.IAction

object ScalaTestRunnerViewPart {
  //orientations
  val VIEW_ORIENTATION_VERTICAL: Int = 0
  val VIEW_ORIENTATION_HORIZONTAL: Int = 1
  val VIEW_ORIENTATION_AUTOMATIC: Int = 2
  
  val REFRESH_INTERVAL: Int = 200
  
  val LAYOUT_FLAT: Int = 0
  val LAYOUT_HIERARCHICAL: Int = 1
}

class ScalaTestRunnerViewPart extends ViewPart with Observer {
  
  private var fCurrentOrientation: Int = 0

  private var fParent: Composite = null
  
  private var fSashForm: SashForm = null
  private var fProgressBar: JUnitProgressBar = null
  
  private var fCounterComposite: Composite = null
  private var fCounterPanel: ScalaTestCounterPanel = null
  private var fTestViewer: ScalaTestViewer = null
  private var fStackTrace: ScalaTestStackTrace = null
  
  private var fNextAction: ShowNextFailureAction = null
  private var fPreviousAction: ShowPreviousFailureAction = null
  private var fFailedTestsOnlyFilterAction: FailedTestsOnlyFilterAction = null
  private var fStopAction: StopAction = null
  private var fRerunAllTestsAction: RerunAllTestsAction = null
  private var fRerunFailedTestsAction: RerunFailedTestsAction = null
  
  private var fIsDisposed: Boolean = false
  
  private var fTestRunSession: ScalaTestRunSession = null
  
  private var fUpdateJob: UpdateUIJob = null
  private var fRunningLock: ILock = null
  
  private val suiteList = new ListBuffer[Node]()
  private var nodeList: List[Node] = Nil
  private var suiteMap: Map[String, SuiteModel] = null
  
  val suiteIcon = ScalaTestImages.SCALATEST_SUITE.createImage
  val suiteSucceedIcon = ScalaTestImages.SCALATEST_SUITE_OK.createImage
  val suiteFailIcon = ScalaTestImages.SCALATEST_SUITE_FAIL.createImage
  val suiteAbortedIcon = ScalaTestImages.SCALATEST_SUITE_ABORTED.createImage
  val suiteRunIcon = ScalaTestImages.SCALATEST_SUITE_RUN.createImage
  val scopeIcon = ScalaTestImages.SCALATEST_SCOPE.createImage
  val testRunIcon = ScalaTestImages.SCALATEST_RUN.createImage
  val testSucceedIcon = ScalaTestImages.SCALATEST_SUCCEED.createImage
  val testFailedIcon = ScalaTestImages.SCALATEST_FAILED.createImage
  val testIgnoredIcon = ScalaTestImages.SCALATEST_IGNORED.createImage
  val infoIcon = ScalaTestImages.SCALATEST_INFO.createImage
  val stackTraceIcon = ScalaTestImages.SCALATEST_STACKTRACE.createImage
  
  def getSession = fTestRunSession
  
  def setSession(session: ScalaTestRunSession) {
    fTestRunSession = session
    fRerunAllTestsAction.session = session
    fRerunFailedTestsAction.session = session
  }
  
  def createPartControl(parent: Composite) {
    fParent = parent
    addResizeListener(parent)
    
    val gridLayout = new GridLayout()
    gridLayout.marginWidth = 0
    gridLayout.marginHeight = 0
    parent.setLayout(gridLayout)
    
    configureToolBar()
    
    fCounterComposite = createProgressCountPanel(parent)
	fCounterComposite.setLayoutData(new GridData(GridData.GRAB_HORIZONTAL | GridData.HORIZONTAL_ALIGN_FILL))
    val sashForm = createSashForm(parent)
    sashForm.setLayoutData(new GridData(GridData.FILL_BOTH))
  }
  
  override def dispose() {
    suiteIcon.dispose()
    suiteSucceedIcon.dispose()
    suiteFailIcon.dispose()
    suiteAbortedIcon.dispose()
    suiteRunIcon.dispose()
    scopeIcon.dispose()
    testRunIcon.dispose()
    testSucceedIcon.dispose()
    testFailedIcon.dispose()
    testIgnoredIcon.dispose()
    infoIcon.dispose()
    stackTraceIcon.dispose()
  }
  
  private def createProgressCountPanel(parent: Composite): Composite = {
    val composite = new Composite(parent, SWT.NONE)
    val layout= new GridLayout()
    composite.setLayout(layout)
    setCounterColumns(layout)

    fCounterPanel = new ScalaTestCounterPanel(composite)
    fCounterPanel.setLayoutData(new GridData(GridData.GRAB_HORIZONTAL | GridData.HORIZONTAL_ALIGN_FILL))
    fProgressBar = new JUnitProgressBar(composite)
    fProgressBar.setLayoutData(new GridData(GridData.GRAB_HORIZONTAL | GridData.HORIZONTAL_ALIGN_FILL))
    composite
  }
  
  private def createSashForm(parent: Composite): SashForm = {
    fSashForm = new SashForm(parent, SWT.VERTICAL);

	val top= new ViewForm(fSashForm, SWT.NONE)

    val empty= new Composite(top, SWT.NONE)
    empty.setLayout(new Layout() {
      override protected def computeSize(composite: Composite, wHint: Int, hHint: Int, flushCache: Boolean): Point = {
        return new Point(1, 1); // (0, 0) does not work with super-intelligent ViewForm
      }
      override protected def layout(composite: Composite, flushCache: Boolean) {
      }
    })
    top.setTopLeft(empty) // makes ViewForm draw the horizontal separator line ...
    fTestViewer = new ScalaTestViewer(top, this)
    top.setContent(fTestViewer.getTestViewerControl)

    val bottom = new ViewForm(fSashForm, SWT.NONE)

    val label = new CLabel(bottom, SWT.NONE)
    label.setText("Stack Trace")
    label.setImage(stackTraceIcon)
    bottom.setTopLeft(label)
    val stackTraceToolBar= new ToolBar(bottom, SWT.FLAT | SWT.WRAP)
    bottom.setTopCenter(stackTraceToolBar)
    fStackTrace= new ScalaTestStackTrace(bottom, this, stackTraceToolBar)
    bottom.setContent(fStackTrace.getComposite)

    fSashForm.setWeights(Array[Int](50, 50))
    fSashForm
  }
  
  def setFocus() {
    if (fTestViewer != null)
      fTestViewer.getTestViewerControl.setFocus()
  }
  
  private def enableToolbarControls(enable: Boolean) {
    fRerunAllTestsAction.setEnabled(enable)
    fRerunFailedTestsAction.setEnabled(enable)
    fStopAction.setEnabled(!enable)
    fNextAction.setEnabled(enable)
    fPreviousAction.setEnabled(enable)
  }
  
  def update(o: Observable, arg: AnyRef) {
    arg match {
      case testStarting: TestStarting => 
        fTestRunSession.startedCount += 1
        val test = 
          TestModel(
            testStarting.suiteId, 
            testStarting.testName,
            testStarting.testText,
            testStarting.decodedTestName,
            None,
            None, 
            None, 
            None, 
            testStarting.location,
            testStarting.rerunner,
            testStarting.threadName,
            testStarting.timeStamp, 
            TestStatus.STARTED
          )
        suiteMap.get(testStarting.suiteId) match {
          case Some(suite) => 
            suite.addChild(test)
            fTestViewer.registerAutoScrollTarget(test)
            fTestViewer.registerNodeAdded(test)
          case None => 
            // Should not happen
            throw new IllegalStateException("Unable to find suite model for TestStarting, suiteId: " + testStarting.suiteId + ", test name: " + testStarting.testName)
        }
      case testSucceeded: TestSucceeded => 
        fTestRunSession.succeedCount += 1
        suiteMap.get(testSucceeded.suiteId) match {
          case Some(suite) => 
            val test = suite.updateTest(testSucceeded.testName, TestStatus.SUCCEEDED, testSucceeded.duration, testSucceeded.location, None, None, None)
            suite.closeScope()
            fTestViewer.registerAutoScrollTarget(test)
            fTestViewer.registerViewerUpdate(test)
          case None => 
            // Should not happen
            throw new IllegalStateException("Unable to find suite model for TestSucceeded, suiteId: " + testSucceeded.suiteId + ", test name: " + testSucceeded.testName)
        }
      case testFailed: TestFailed => 
        fTestRunSession.failureCount += 1
        suiteMap.get(testFailed.suiteId) match {
          case Some(suite) => 
            val test = suite.updateTest(testFailed.testName, TestStatus.FAILED, testFailed.duration, testFailed.location, testFailed.errorMessage, testFailed.errorDepth, testFailed.errorStackTraces)
            suite.closeScope()
            fTestViewer.registerFailedForAutoScroll(test)
            fTestViewer.registerViewerUpdate(test)
          case None => 
            // Should not happen
            throw new IllegalStateException("Unable to find suite model for TestFailed, suiteId: " + testFailed.suiteId + ", test name: " + testFailed.testName)
        }
      case testIgnored: TestIgnored => 
        fTestRunSession.ignoredCount += 1
        val test = 
          TestModel(
            testIgnored.suiteId, 
            testIgnored.testName,
            testIgnored.testText,
            testIgnored.decodedTestName,
            None,
            None, 
            None, 
            None, 
            testIgnored.location,
            None,
            testIgnored.threadName,
            testIgnored.timeStamp, 
            TestStatus.IGNORED
          )
        suiteMap.get(testIgnored.suiteId) match {
          case Some(suite) => 
            suite.addChild(test)
            fTestViewer.registerAutoScrollTarget(test)
            fTestViewer.registerNodeAdded(test)
          case None => 
            // Should not happen
            throw new IllegalStateException("Unable to find suite model for TestIgnored, suiteId: " + testIgnored.suiteId + ", test name: " + testIgnored.testName)
        }
      case testPending: TestPending => 
        fTestRunSession.pendingCount += 1
        suiteMap.get(testPending.suiteId) match {
          case Some(suite) => 
            val test = suite.updateTest(testPending.testName, TestStatus.PENDING, testPending.duration, testPending.location, None, None, None)
            suite.closeScope()
            fTestViewer.registerAutoScrollTarget(test)
            fTestViewer.registerViewerUpdate(test)
          case None => 
            // Should not happen
            throw new IllegalStateException("Unable to find suite model for TestPending, suiteId: " + testPending.suiteId + ", test name: " + testPending.testName)
        }
      case testCanceled: TestCanceled => 
        fTestRunSession.canceledCount += 1
        suiteMap.get(testCanceled.suiteId) match {
          case Some(suite) => 
            val test = suite.updateTest(testCanceled.testName, TestStatus.CANCELED, testCanceled.duration, testCanceled.location, testCanceled.errorMessage, testCanceled.errorDepth, testCanceled.errorStackTraces)
            suite.closeScope()
            fTestViewer.registerAutoScrollTarget(test)
            fTestViewer.registerViewerUpdate(test)
          case None => 
            // Should not happen
            throw new IllegalStateException("Unable to find suite model for TestCanceled, suiteId: " + testCanceled.suiteId + ", test name: " + testCanceled.testName)
        }
      case suiteStarting: SuiteStarting => 
        if (suiteStarting.suiteId != "org.scalatest.tools.DiscoverySuite") {
          fTestRunSession.suiteCount += 1
          val suite = SuiteModel(
                        suiteStarting.suiteName,
                        suiteStarting.suiteId,
                        suiteStarting.suiteClassName,
                        suiteStarting.decodedSuiteName,
                        suiteStarting.location,
                        suiteStarting.rerunner,
                        None,
                        None,
                        None, 
                        None, 
                        suiteStarting.threadName,
                        suiteStarting.timeStamp, 
                        SuiteStatus.STARTED
                      )
          suiteList += suite
          suiteMap += (suite.suiteId -> suite)
          fTestRunSession.rootNode.addChild(suite)
          fTestViewer.registerAutoScrollTarget(suite)
          fTestViewer.registerNodeAdded(suite)
        }
      case suiteCompleted: SuiteCompleted => 
        if (suiteCompleted.suiteId != "org.scalatest.tools.DiscoverySuite") {
          suiteMap.get(suiteCompleted.suiteId) match {
            case Some(suite) => 
              suite.duration = suiteCompleted.duration
              suite.location = suiteCompleted.location
              suite.status = 
                if (suite.suiteSucceeded)
                  SuiteStatus.SUCCEED
                else
                  SuiteStatus.FAILED
              fTestViewer.registerAutoScrollTarget(suite)
              fTestViewer.registerViewerUpdate(suite)
            case None => 
              // Should not happen
              throw new IllegalStateException("Unable to find suite model for SuiteCompleted, suiteId: " + suiteCompleted.suiteId)
          }
        }
      case suiteAborted: SuiteAborted => 
        if (suiteAborted.suiteId != "org.scalatest.tools.DiscoverySuite") {
          fTestRunSession.suiteAbortedCount += 1
          suiteMap.get(suiteAborted.suiteId) match {
            case Some(suite) => 
              suite.duration = suiteAborted.duration
              suite.location = suiteAborted.location
              suite.errorMessage = suiteAborted.errorMessage
              suite.errorDepth = suiteAborted.errorDepth
              suite.errorStackTrace = suiteAborted.errorStackTraces
              suite.status = SuiteStatus.ABORTED
              fTestViewer.registerAutoScrollTarget(suite)
              fTestViewer.registerViewerUpdate(suite)
            case None => 
              // Should not happend
              throw new IllegalStateException("Unable to find suite model for SuiteAborted, suiteId: " + suiteAborted.suiteId)
          }
        }
      case runStarting: RunStarting => 
        enableToolbarControls(false)
        suiteList.clear()
        suiteMap = Map.empty[String, SuiteModel]
        fTestRunSession.rootNode = 
          RunModel(
            runStarting.testCount, 
            None, 
            None,
            None,
            None, 
            None, 
            runStarting.threadName,
            runStarting.timeStamp, 
            RunStatus.STARTED
          )
        fTestViewer.registerViewersRefresh()
        fTestViewer.clearAutoExpand()
        startUpdateJobs()
        fTestRunSession.run()
        fTestRunSession.totalCount = runStarting.testCount
      case runCompleted: RunCompleted =>
        fTestRunSession.done()
        fTestRunSession.rootNode.duration = runCompleted.duration
        fTestRunSession.rootNode.summary = runCompleted.summary
        fTestRunSession.rootNode.status = RunStatus.COMPLETED
        stopUpdateJobs()
        nodeList = getFlattenNode(suiteList.toList)
        fTestViewer.registerAutoScrollTarget(null)
        enableToolbarControls(true)
      case runStopped: RunStopped => 
        fTestRunSession.stop()
        fTestRunSession.rootNode.duration = runStopped.duration
        fTestRunSession.rootNode.summary = runStopped.summary
        fTestRunSession.rootNode.status = RunStatus.STOPPED
        stopUpdateJobs()
        nodeList = getFlattenNode(suiteList.toList)
        fTestViewer.registerAutoScrollTarget(null)
        enableToolbarControls(true)
      case runAborted: RunAborted => 
        fTestRunSession.stop()
        if (fTestRunSession.rootNode != null) {
          fTestRunSession.rootNode.duration = runAborted.duration
          fTestRunSession.rootNode.summary = runAborted.summary
          fTestRunSession.rootNode.errorMessage = runAborted.errorMessage
          fTestRunSession.rootNode.errorDepth = runAborted.errorDepth
          fTestRunSession.rootNode.errorStackTrace = runAborted.errorStackTraces
          fTestRunSession.rootNode.status = RunStatus.ABORTED
        }
        stopUpdateJobs()
        nodeList = getFlattenNode(suiteList.toList)
        fTestViewer.registerAutoScrollTarget(null)
        enableToolbarControls(true)
      case infoProvided: InfoProvided => 
        val info = 
          InfoModel(
            infoProvided.message,
            infoProvided.nameInfo,
            infoProvided.aboutAPendingTest,
            infoProvided.aboutACanceledTest,
            infoProvided.errorMessage, 
            infoProvided.errorDepth, 
            infoProvided.errorStackTraces, 
            infoProvided.location, 
            infoProvided.threadName,
            infoProvided.timeStamp
          )
        infoProvided.nameInfo match {
          case Some(nameInfo) => 
            suiteMap.get(nameInfo.suiteId) match {
              case Some(suite) => 
                suite.addChild(info)
                fTestViewer.registerAutoScrollTarget(info)
                fTestViewer.registerNodeAdded(info)
              case None => 
                // Should not happen
               throw new IllegalStateException("Unable to find suite model for InfoProvided, suiteId: " + nameInfo.suiteId)
            }
          case None => 
            fTestRunSession.rootNode.addChild(info)
        }
      case markupProvided: MarkupProvided => 
        // Do nothing for MarkupProvided, markup info should be shown in HtmlReporter only.
      case scopeOpened: ScopeOpened => 
        suiteMap.get(scopeOpened.nameInfo.suiteId) match {
          case Some(suite) => 
            val scope = 
              ScopeModel(
                scopeOpened.message,
                scopeOpened.nameInfo,
                scopeOpened.location,
                scopeOpened.threadName,
                scopeOpened.timeStamp, 
                ScopeStatus.OPENED
              )
            suite.addChild(scope)
            fTestViewer.registerAutoScrollTarget(scope)
            fTestViewer.registerNodeAdded(scope)
          case None => 
            // Should not happend
            throw new IllegalStateException("Unable to find suite model for ScopeOpened, suiteId: " + scopeOpened.nameInfo.suiteId)
        }
      case scopeClosed: ScopeClosed => 
        suiteMap.get(scopeClosed.nameInfo.suiteId) match {
          case Some(suite) => 
            suite.closeScope()
          case None => 
            throw new IllegalStateException("Unable to find suite model for ScopeClosed, suiteId: " + scopeClosed.nameInfo.suiteId)
        }
    }
  }
  
  private def getFlattenNode(suiteList: List[Node]) = {
    @tailrec
    def getFlattenNodeAcc(acc: List[Node], suiteList: List[Node]): List[Node] = {
      suiteList match {
        case Nil => 
          acc
        case head :: rest =>
          getFlattenNodeAcc(head :: acc, head.children.toList ::: rest)
      }
    }
    getFlattenNodeAcc(List.empty, suiteList).reverse
  }
  
  private def addResizeListener(parent: Composite) {
    parent.addControlListener(new ControlListener() {
      def controlMoved(e: ControlEvent) {
      }
      def controlResized(e: ControlEvent) {
        computeOrientation()
      }
    })
  }
  
  private def computeOrientation() {
    // compute orientation automatically
    val size = fParent.getSize
    if (size.x != 0 && size.y != 0) {
      if (size.x > size.y)
        setOrientation(VIEW_ORIENTATION_HORIZONTAL)
      else
        setOrientation(VIEW_ORIENTATION_VERTICAL)
    }
  }
  
  private def setOrientation(orientation: Int) {
    if ((fSashForm == null) || fSashForm.isDisposed())
      return
    val horizontal = orientation == VIEW_ORIENTATION_HORIZONTAL
    fSashForm.setOrientation(if (horizontal) SWT.HORIZONTAL else SWT.VERTICAL);
    fCurrentOrientation = orientation;
    val layout = fCounterComposite.getLayout().asInstanceOf[GridLayout]
    setCounterColumns(layout)
    fParent.layout()
  }
  
  private def setCounterColumns(layout: GridLayout) {
    if (fCurrentOrientation == VIEW_ORIENTATION_HORIZONTAL)
      layout.numColumns = 2
    else
      layout.numColumns = 1
  }
  
  private def isDisposed(): Boolean = {
    return fIsDisposed || fCounterPanel.isDisposed();
  }
  
  private def refreshCounters() {
    // TODO: Inefficient. Either
    // - keep a boolean fHasTestRun and update only on changes, or
    // - improve components to only redraw on changes (once!).

    var startedCount: Int = 0
    var succeedCount: Int = 0
    var failureCount: Int = 0
    var ignoredCount: Int = 0
    var pendingCount: Int = 0
    var canceledCount: Int = 0
    var totalCount: Int = 0
    var suiteCount: Int = 0
    var suiteAbortedCount: Int = 0
    
    var hasFailures: Boolean = false
    var stopped: Boolean = false

    if (fTestRunSession != null) {
      startedCount = fTestRunSession.startedCount
      succeedCount = fTestRunSession.succeedCount
      failureCount = fTestRunSession.failureCount
      ignoredCount = fTestRunSession.ignoredCount
      pendingCount = fTestRunSession.pendingCount
      canceledCount = fTestRunSession.canceledCount
      totalCount= fTestRunSession.totalCount
      suiteCount = fTestRunSession.suiteCount
      suiteAbortedCount = fTestRunSession.suiteAbortedCount
      
      hasFailures = failureCount > 0
      stopped = fTestRunSession.isStopped
    } 
    else {
      startedCount = 0
      succeedCount = 0
      failureCount = 0
      ignoredCount = 0
      pendingCount = 0
      canceledCount = 0
      totalCount = 0
      suiteCount = 0
      suiteAbortedCount = 0
      
      hasFailures = false
      stopped = false
    }

    fCounterPanel.setTotal(totalCount)
    fCounterPanel.setRunValue(startedCount)
    fCounterPanel.setSucceedValue(succeedCount)
    fCounterPanel.setFailureValue(failureCount)
    fCounterPanel.setIgnoredValue(ignoredCount)
    fCounterPanel.setPendingValue(pendingCount)
    fCounterPanel.setCanceledValue(canceledCount)
    fCounterPanel.setSuites(suiteCount)
    fCounterPanel.setSuiteAborted(suiteAbortedCount)

    val ticksDone = 
    if (startedCount == 0)
      0
    else if (startedCount == totalCount && ! fTestRunSession.isRunning)
      totalCount
    else
      startedCount - 1

    fProgressBar.reset(hasFailures, stopped, ticksDone, totalCount);
  }
  
  private def postSyncProcessChanges() {
    postSyncRunnable(new Runnable() {
      def run() {
        processChangesInUI()
        expandFailedTests()
      }
    })
  }
  
  private def startUpdateJobs() {
    postSyncProcessChanges()
    
    if (fUpdateJob != null) {
      return
    }
    
    fRunningLock = Job.getJobManager.newLock
    fRunningLock.acquire()

    fUpdateJob = new UpdateUIJob("Update ScalaTest")
    fUpdateJob.schedule(REFRESH_INTERVAL)
  }

  private def stopUpdateJobs() {
    Thread.sleep(REFRESH_INTERVAL)
    
    if (fUpdateJob != null) {
      fUpdateJob.stop()
      fUpdateJob = null
    }
    
    if (fRunningLock != null) {
      fRunningLock.release()
      fRunningLock = null
    }
    
    postSyncProcessChanges();
  }
  
  private def processChangesInUI() {
    if (!fSashForm.isDisposed())
    {
      refreshCounters()
      enableNextPreviousButton()
      fTestViewer.processChangesInUI()
    }
  }
  
  private def enableNextPreviousButton() {
    fNextAction.setEnabled(findNextFailure.isDefined)
    fPreviousAction.setEnabled(findPreviousFailure.isDefined)
  }
  
  private def expandFailedTests() {
    fTestViewer.autoExpandFailedTests()
  }
  
  def handleTestSelected(node: Option[Node]) {
    showFailure(node)
  }
  
  private def showFailure(node: Option[Node]) {
    postSyncRunnable(new Runnable() {
      def run() {
        if (!isDisposed)
          fStackTrace.showFailure(node)
      }
    })
  }
  
  def findNextFailure = {
    val currentList = fTestViewer.selectedNode match {
      case Some(selected) => 
        val dropList = nodeList.dropWhile(node => node != selected)
        if (dropList.isEmpty)
          Nil
        else
          dropList.tail
      case None =>
        nodeList
    }
    currentList.find { node => 
      node match {
        case test: TestModel if test.status == TestStatus.FAILED => true
        case _ => false
      }
    }
  }
  
  def showNextFailure() {
    val next = findNextFailure
    next match {
      case Some(next) => 
        fTestViewer.selectNode(next)
      case None => 
        MessageDialog.openError(null, "Error", "No more next failed test.")
    }
  }
  
  def findPreviousFailure = {
    val currentList = fTestViewer.selectedNode match {
      case Some(selected) => 
        nodeList.takeWhile(node => node != selected).reverse
      case None =>
        nodeList.reverse
    }
    currentList.find { node => 
      node match {
        case test: TestModel if test.status == TestStatus.FAILED => true
        case _ => false
      }
    }
  }
  
  def showPreviousFailure() {
    val previous = findPreviousFailure
    previous match {
      case Some(previous) => 
        fTestViewer.selectNode(previous)
      case None => 
        MessageDialog.openError(null, "Error", "No more previous failed test.")
    }
  }
  
  def terminateRun() {
    ScalaTestPlugin.listener.stop()
    fTestRunSession.stop()
    stopUpdateJobs()
    nodeList = getFlattenNode(suiteList.toList)
    fTestViewer.registerAutoScrollTarget(null)
    enableToolbarControls(true)
  }
  
  private def postSyncRunnable(r: Runnable) {
    if (!isDisposed)
      getDisplay.syncExec(r)
  }
  
  private def getDisplay = getViewSite.getShell.getDisplay
  
  private def configureToolBar() {
    val actionBars = getViewSite.getActionBars
    val toolBar = actionBars.getToolBarManager
    val viewMenu = actionBars.getMenuManager
    
    fNextAction = new ShowNextFailureAction()
    fNextAction.setEnabled(false);
    actionBars.setGlobalActionHandler(ActionFactory.NEXT.getId(), fNextAction)
    
    fPreviousAction= new ShowPreviousFailureAction()
    fPreviousAction.setEnabled(false);
    actionBars.setGlobalActionHandler(ActionFactory.PREVIOUS.getId(), fPreviousAction)
    
    fFailedTestsOnlyFilterAction = new FailedTestsOnlyFilterAction
    
    fRerunAllTestsAction = new RerunAllTestsAction()
    val rerunAllTestsHandler = new AbstractHandler() {
      def execute(event: ExecutionEvent): AnyRef = {
        fRerunAllTestsAction.run()
        return null
      }
      override def isEnabled = fRerunAllTestsAction.isEnabled
    }
    
    fRerunFailedTestsAction = new RerunFailedTestsAction()
    val rerunFailedTestsHandler = new AbstractHandler() {
      def execute(event: ExecutionEvent): AnyRef = {
        fRerunFailedTestsAction.run()
        return null
      }
      override def isEnabled = fRerunFailedTestsAction.isEnabled
    }
    
    fStopAction = new StopAction()
    fStopAction.setEnabled(false)
    
    val handlerService = getSite.getWorkbenchWindow.getService(classOf[IHandlerService]).asInstanceOf[IHandlerService]
    handlerService.activateHandler("Rerun All Tests", rerunAllTestsHandler)
    handlerService.activateHandler("Rerun Failed Tests", rerunFailedTestsHandler)
    
    toolBar.add(fNextAction)
    toolBar.add(fPreviousAction)
    toolBar.add(fFailedTestsOnlyFilterAction)
    toolBar.add(new Separator())
    toolBar.add(fRerunAllTestsAction)
    toolBar.add(fRerunFailedTestsAction)
    toolBar.add(fStopAction)
    
    actionBars.updateActionBars()
  }
  
  def setShowFailedTestsOnly(failedTestsOnly: Boolean) {
    fTestViewer.setShowFailedTestsOnly(failedTestsOnly)
  }
  
  private class UpdateUIJob(name: String) extends UIJob(name) {
    private var fRunning = true
    
    setSystem(true)

    override def runInUIThread(monitor: IProgressMonitor): IStatus = {
      if (!isDisposed()) {
        processChangesInUI();
      }
      schedule(REFRESH_INTERVAL);
      Status.OK_STATUS;
    }

    def stop() {
      fRunning= false;
    }
	
    override def shouldSchedule(): Boolean = {
      return fRunning
    }
  }
  
  private class RerunAllTestsAction extends Action {
    setText("Rerun All Tests")
    setToolTipText("Rerun All Tests")
    setImageDescriptor(ScalaTestImages.SCALATEST_RERUN_ALL_TESTS_ENABLED)
    setDisabledImageDescriptor(ScalaTestImages.SCALATEST_RERUN_ALL_TESTS_DISABLED)
    setEnabled(false)
    
    var session: ScalaTestRunSession = null
    
    override def run() {
      val launch = session.fLaunch
      DebugUITools.launch(launch.getLaunchConfiguration, launch.getLaunchMode)
    }
  }
  
  private class RerunFailedTestsAction extends Action {
    setText("Rerun Failed Tests")
    setToolTipText("Rerun Failed Tests")
    setImageDescriptor(ScalaTestImages.SCALATEST_RERUN_FAILED_TESTS_ENABLED)
    setDisabledImageDescriptor(ScalaTestImages.SCALATEST_RERUN_FAILED_TESTS_DISABLED)
    setEnabled(false)
    
    var session: ScalaTestRunSession = null
    
    override def run() {
      val launch = session.fLaunch
      val delegate = new ScalaTestLaunchDelegate()
      val stArgs = delegate.getScalaTestArgsForFailedTests(session.rootNode)
      val buildBeforeLaunch = DebugUIPlugin.getDefault().getPreferenceStore().getBoolean(IDebugUIConstants.PREF_BUILD_BEFORE_LAUNCH)
      if (buildBeforeLaunch)
        ScalaTestPlugin.doBuild()
      delegate.launchScalaTest(launch.getLaunchConfiguration, launch.getLaunchMode, launch, null, stArgs)
    }
  }
  
  private class ShowNextFailureAction extends Action("Next Failure") {
    setDisabledImageDescriptor(ScalaTestImages.SCALATEST_NEXT_FAILED_DISABLED)
    setHoverImageDescriptor(ScalaTestImages.SCALATEST_NEXT_FAILED_ENABLED)
    setImageDescriptor(ScalaTestImages.SCALATEST_NEXT_FAILED_ENABLED)
    setToolTipText("Next Failed Test")
    
    override def run() {
      showNextFailure()
      enableNextPreviousButton()
    }
  }
  
  private class ShowPreviousFailureAction extends Action("Previous Failure") {
    setDisabledImageDescriptor(ScalaTestImages.SCALATEST_PREV_FAILED_DISABLED)
    setHoverImageDescriptor(ScalaTestImages.SCALATEST_PREV_FAILED_ENABLED)
    setImageDescriptor(ScalaTestImages.SCALATEST_PREV_FAILED_ENABLED)
    setToolTipText("Previous Failed Test")
    
    override def run() {
      showPreviousFailure()
      enableNextPreviousButton()
    }
  }
  
  private class StopAction extends Action {
    setText("Stop Running ScalaTest")
    setToolTipText("Stop ScalaTest Run")
    setImageDescriptor(ScalaTestImages.SCALATEST_STOP_ENABLED)
    setDisabledImageDescriptor(ScalaTestImages.SCALATEST_STOP_DISABLED)
    
    override def run() {
      terminateRun()
    }
  }
  
  private class FailedTestsOnlyFilterAction extends Action("Show Failed Tests Only", IAction.AS_CHECK_BOX) {
    setToolTipText("Show Failed Tests Only")
    setImageDescriptor(ScalaTestImages.SCALATEST_SHOW_FAILED_TESTS_ONLY)
    
    override def run() {
      setShowFailedTestsOnly(isChecked)
      enableNextPreviousButton()
    }
  }
}