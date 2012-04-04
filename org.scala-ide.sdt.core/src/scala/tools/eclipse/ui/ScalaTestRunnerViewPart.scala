package scala.tools.eclipse.ui

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

class ScalaTestRunnerViewPart extends ViewPart with Observer {
    
  //orientations
  val VIEW_ORIENTATION_VERTICAL: Int = 0
  val VIEW_ORIENTATION_HORIZONTAL: Int = 1
  val VIEW_ORIENTATION_AUTOMATIC: Int = 2
  
  val REFRESH_INTERVAL: Int = 200
    
  private var fOrientation = VIEW_ORIENTATION_AUTOMATIC
  private var fCurrentOrientation: Int = 0

  protected var fParent: Composite = null
  
  private var fSashForm: SashForm = null
  protected var fProgressBar: JUnitProgressBar = null
  
  protected var fCounterComposite: Composite = null
  protected var fCounterPanel: ScalaTestCounterPanel = null
  
  private var fIsDisposed: Boolean = false
  
  private var fTestRunSession: ScalaTestRunSession = null
  
  private var fUpdateJob: UpdateUIJob = null
  
  def setSession(session: ScalaTestRunSession) {
    fTestRunSession = session
  }
  
  def createPartControl(parent: Composite) {
    fParent = parent
    
    fCounterComposite = createProgressCountPanel(parent);
	fCounterComposite.setLayoutData(new GridData(GridData.GRAB_HORIZONTAL | GridData.HORIZONTAL_ALIGN_FILL));
  }
  
  override def dispose() {
    
  }
  
  override def saveState(memento: IMemento) {
    
  }
  
  def setFocus() {

  }
  
  protected def createProgressCountPanel(parent: Composite): Composite = {
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
  
  def update(o: Observable, arg: AnyRef) {
    arg match {
      case testStarting: TestStarting => 
        println("***TestStarting")
        fTestRunSession.startedCount += 1
      case testSucceeded: TestSucceeded => 
        println("***TestSucceeded")
        fTestRunSession.succeedCount += 1
      case testFailed: TestFailed => 
        println("***TestFailed")
        fTestRunSession.failureCount += 1
      case testIgnored: TestIgnored => 
        println("***TestIgnored")
        fTestRunSession.ignoredCount += 1
      case testPending: TestPending => 
        println("***TestPending")
        fTestRunSession.pendingCount += 1
      case testCanceled: TestCanceled => 
        println("***TestCanceled")
        fTestRunSession.canceledCount += 1
      case suiteStarting: SuiteStarting => 
        println("***SuiteStarting")
        fTestRunSession.suiteCount += 1
      case suiteCompleted: SuiteCompleted => 
        println("***SuiteCompleted")
      case suiteAborted: SuiteAborted => 
        println("***SuiteAborted")
        fTestRunSession.suiteAbortedCount += 1
      case runStarting: RunStarting => 
        println("***RunStarting")
        startUpdateJobs()
        fTestRunSession.run()
        fTestRunSession.totalCount = runStarting.testCount
      case runCompleted: RunCompleted => 
        println("***RunCompleted")
        fTestRunSession.stop()
        stopUpdateJobs()
      case runStopped: RunStopped => 
        println("***RunStopped")
        fTestRunSession.stop()
        stopUpdateJobs()
      case runAborted: RunAborted => 
        println("***RunAborted")
        fTestRunSession.stop()
        stopUpdateJobs()
      case infoProvided: InfoProvided => println("***InfoProvided")
      case markupProvided: MarkupProvided => println("***MarkupProvided")
      case scopeOpened: ScopeOpened => println("***ScopeOpened")
      case scopeClosed: ScopeClosed => println("***ScopeClosed")
    }
  }
  
  /*def computeOrientation() {
    
  }
  
  private def setOrientation(orientation: Int) {
    if ((fSashForm == null) || fSashForm.isDisposed())
      return;
    val horizontal = orientation == VIEW_ORIENTATION_HORIZONTAL;
    fSashForm.setOrientation(if (horizontal) SWT.HORIZONTAL else SWT.VERTICAL);
    //for (int i = 0; i < fToggleOrientationActions.length; ++i)
      //fToggleOrientationActions[i].setChecked(fOrientation == fToggleOrientationActions[i].getOrientation());
    for (fToggleOrientationAction <- fToggleOrientationActions)
    fCurrentOrientation = orientation;
    GridLayout layout= (GridLayout) fCounterComposite.getLayout();
    setCounterColumns(layout);
    fParent.layout();
  }*/
  
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
  
  private def startUpdateJobs() {
    //postSyncProcessChanges();

    if (fUpdateJob != null) {
      return
    }
    /*fJUnitIsRunningJob= new JUnitIsRunningJob(JUnitMessages.TestRunnerViewPart_wrapperJobName);
    fJUnitIsRunningLock= Job.getJobManager().newLock();
    // acquire lock while a test run is running
    // the lock is released when the test run terminates
    // the wrapper job will wait on this lock.
    fJUnitIsRunningLock.acquire();
    getProgressService().schedule(fJUnitIsRunningJob);*/

    fUpdateJob = new UpdateUIJob("Update ScalaTest")
    fUpdateJob.schedule(REFRESH_INTERVAL);
  }

  private def stopUpdateJobs() {
    Thread.sleep(REFRESH_INTERVAL)
    if (fUpdateJob != null) {
      fUpdateJob.stop()
      fUpdateJob= null
    }
    /*if (fJUnitIsRunningJob != null && fJUnitIsRunningLock != null) {
      fJUnitIsRunningLock.release();
      fJUnitIsRunningJob= null;
    }
    postSyncProcessChanges();*/
  }
  
  private def processChangesInUI() {
    //if (!fSashForm.isDisposed())
    //{
      //doShowInfoMessage()
      refreshCounters();

      /*if (!fPartIsVisible)
        updateViewTitleProgress();
      else {
        updateViewIcon();
      }
      boolean hasErrorsOrFailures= hasErrorsOrFailures();
      fNextAction.setEnabled(hasErrorsOrFailures);
      fPreviousAction.setEnabled(hasErrorsOrFailures);

      fTestViewer.processChangesInUI();*/
    //}
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
}