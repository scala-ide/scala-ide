package scala.tools.eclipse.ui

import org.eclipse.swt.widgets.Composite
import org.eclipse.swt.SWT
import org.eclipse.swt.widgets.Text
import scala.tools.eclipse.ScalaImages
import org.eclipse.swt.layout.GridLayout
import org.eclipse.swt.widgets.Label
import org.eclipse.swt.graphics.Image
import org.eclipse.swt.layout.GridData
import org.eclipse.swt.events.DisposeListener
import org.eclipse.swt.events.DisposeEvent
import java.text.MessageFormat

class ScalaTestCounterPanel(parent: Composite) extends Composite(parent, SWT.WRAP) {
  protected var fNumberOfRuns: Text = null
  protected var fNumberOfSucceed: Text = null
  protected var fNumberOfFailure: Text = null
  protected var fNumberOfIgnored: Text = null
  protected var fNumberOfPending: Text = null
  protected var fNumberOfCanceled: Text = null
  protected var fNumberOfSuites: Text = null
  protected var fNumberOfSuiteAborted: Text = null
  
  protected var fTotal: Int = 0
  
  private val fSuccessIcon = ScalaImages.SCALATEST_SUCCESS.createImage
  private val fErrorIcon = ScalaImages.SCALATEST_ERROR.createImage
  private val fFailureIcon = ScalaImages.SCALATEST_FAILED.createImage
  
  createComponents()
  
  private def createComponents() {
    val gridLayout = new GridLayout()
    gridLayout.numColumns = 9
    gridLayout.makeColumnsEqualWidth = false
    gridLayout.marginWidth = 0
    setLayout(gridLayout)
    
    fNumberOfRuns = createLabel("Runs: ", null, " 0/0  ") //$NON-NLS-1$
    fNumberOfSucceed = createLabel("Succeed: ", fSuccessIcon, " 0 ")
    fNumberOfFailure = createLabel("Failure: ", fFailureIcon, " 0 ") //$NON-NLS-1$
    fNumberOfIgnored = createLabel("Ignored: ", fErrorIcon, " 0 ") //$NON-NLS-1$
    fNumberOfPending = createLabel("Pending: ", fErrorIcon, " 0 ")
    fNumberOfCanceled = createLabel("Canceled: ", fErrorIcon, " 0 ")
    fNumberOfSuites = createLabel("Suites: ", null, " 0 ")
    fNumberOfSuiteAborted = createLabel("Aborted: ", fErrorIcon, " 0 ")
    
    addDisposeListener(new DisposeListener() {
	  def widgetDisposed(e: DisposeEvent) {
        disposeIcons()
      }
    })
  }
  
  private def disposeIcons() {
    fSuccessIcon.dispose()
    fErrorIcon.dispose()
    fFailureIcon.dispose()
  }
  
  private def createLabel(name: String, image: Image, init: String): Text = {
    var label= new Label(this, SWT.NONE);
    if (image != null) {
      image.setBackground(label.getBackground())
      label.setImage(image)
    }
    label.setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_BEGINNING))

    label = new Label(this, SWT.NONE)
    label.setText(name)
    label.setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_BEGINNING))
    //label.setFont(JFaceResources.getBannerFont());

    val value= new Text(this, SWT.READ_ONLY)
    value.setText(init)
    // bug: 39661 Junit test counters do not repaint correctly [JUnit]
    value.setBackground(getDisplay().getSystemColor(SWT.COLOR_WIDGET_BACKGROUND))
    value.setLayoutData(new GridData(GridData.FILL_HORIZONTAL | GridData.HORIZONTAL_ALIGN_BEGINNING))
    value
  }
  
  def reset() {
    setSucceedValue(0)
    setFailureValue(0)
    setIgnoredValue(0)
    setPendingValue(0)
    setCanceledValue(0)
    setRunValue(0)
    setSuites(0)
    setSuiteAborted(0)
    fTotal = 0
  }

  def setTotal(value: Int) {
    fTotal= value
  }

  def getTotal() = {
    fTotal;
  }
  
  def setRunValue(value: Int) {
    val runString = " " + value + "/" + fTotal
    
    fNumberOfRuns.setText(runString)
    fNumberOfRuns.redraw()

    /*if (fIgnoredCount == 0 && ignoredCount > 0 || fIgnoredCount != 0 && ignoredCount == 0) {
      layout()
    } else {
      fNumberOfRuns.redraw()
      redraw()
    }
    fIgnoredCount= ignoredCount;*/
  }

  def setSucceedValue(value: Int) {
    fNumberOfSucceed.setText(value.toString)
    redraw()
  }
  
  def setIgnoredValue(value: Int) {
    fNumberOfIgnored.setText(value.toString)
    redraw()
  }
  
  def setPendingValue(value: Int) {
    fNumberOfPending.setText(value.toString)
  }
  
  def setCanceledValue(value: Int) {
    fNumberOfCanceled.setText(value.toString)
  }

  def setFailureValue(value: Int) {
    fNumberOfFailure.setText(value.toString)
    redraw()
  }
  
  def setSuites(value: Int) {
    fNumberOfSuites.setText(value.toString)
    redraw()
  }
  
  def setSuiteAborted(value: Int) {
    fNumberOfSuiteAborted.setText(value.toString)
    redraw()
  }
}