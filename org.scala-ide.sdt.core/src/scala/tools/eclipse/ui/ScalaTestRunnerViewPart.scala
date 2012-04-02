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

class ScalaTestRunnerViewPart extends ViewPart with Observer {
    
  //orientations
  val VIEW_ORIENTATION_VERTICAL: Int = 0
  val VIEW_ORIENTATION_HORIZONTAL: Int = 1
  val VIEW_ORIENTATION_AUTOMATIC: Int = 2
    
  private var fOrientation = VIEW_ORIENTATION_AUTOMATIC
  private var fCurrentOrientation: Int = 0

  protected var fParent: Composite = null
  
  private var fSashForm: SashForm = null
  protected var fCounterComposite: Composite = null
  protected var fProgressBar: JUnitProgressBar = null
  
  
  def createPartControl(parent: Composite) {
    fParent = parent
  }
  
  override def dispose() {
    
  }
  
  override def saveState(memento: IMemento) {
    
  }
  
  def setFocus() {

  }
  
  def update(o: Observable, arg: AnyRef) {
    arg match {
      case testStarting: TestStarting => println("***TestStarting")
      case testSucceeded: TestSucceeded => println("***TestSucceeded")
      case testFailed: TestFailed => println("***TestFailed")
      case testIgnored: TestIgnored => println("***TestIgnored")
      case testPending: TestPending => println("***TestPending")
      case testCanceled: TestCanceled => println("***TestCanceled")
      case suiteStarting: SuiteStarting => println("***SuiteStarting")
      case suiteCompleted: SuiteCompleted => println("***SuiteCompleted")
      case suiteAborted: SuiteAborted => println("***SuiteAborted")
      case runStarting: RunStarting => println("***RunStarting")
      case runCompleted: RunCompleted => println("***RunCompleted")
      case runStopped: RunStopped => println("***RunStopped")
      case runAborted: RunAborted => println("***RunAborted")
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
  }
  
  private def setCounterColumns(layout: GridLayout) {
    if (fCurrentOrientation == VIEW_ORIENTATION_HORIZONTAL)
      layout.numColumns = 2
    else
      layout.numColumns = 1
  }*/
}