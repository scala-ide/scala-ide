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
  protected var fNumberOfErrors: Text = null
  protected var fNumberOfFailures: Text = null
  protected var fNumberOfRuns: Text = null
  protected var fTotal: Int = 0
  protected var fIgnoredCount: Int = 0
  
  private val fErrorIcon = ScalaImages.SCALATEST_ERROR.createImage
  private val fFailureIcon = ScalaImages.SCALATEST_FAILED.createImage
  
  createComponents()
  
  private def createComponents() {
    val gridLayout = new GridLayout()
    gridLayout.numColumns = 9
    gridLayout.makeColumnsEqualWidth = false
    gridLayout.marginWidth = 0
    setLayout(gridLayout)
    
    fNumberOfRuns = createLabel("Runs: ", null, " 0/0  "); //$NON-NLS-1$
    fNumberOfErrors = createLabel("Errors: ", fErrorIcon, " 0 "); //$NON-NLS-1$
    fNumberOfFailures = createLabel("Failures: ", fFailureIcon, " 0 "); //$NON-NLS-1$
    
    addDisposeListener(new DisposeListener() {
	  def widgetDisposed(e: DisposeEvent) {
        disposeIcons();
      }
    })
  }
  
  private def disposeIcons() {
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
    setErrorValue(0)
    setFailureValue(0)
    setRunValue(0, 0)
    fTotal = 0
  }

  def setTotal(value: Int) {
    fTotal= value
  }

  def getTotal() = {
    fTotal;
  }
  
  def setRunValue(value: Int, ignoredCount: Int) {
    val runString =
    if (ignoredCount == 0)
      " " + value + "/" + fTotal
      //MessageFormat.format(" {0}/{1}", Array[String](value.toString, fTotal.toString))
    else
      " " + value + "/" + fTotal + " (" + ignoredCount + " ignored)"
      //MessageFormat.format(" {0}/{1} ({2} ignored)", Array[String](value.toString, fTotal.toString, ignoredCount.toString))
    
    fNumberOfRuns.setText(runString)

    if (fIgnoredCount == 0 && ignoredCount > 0 || fIgnoredCount != 0 && ignoredCount == 0) {
      layout()
    } else {
      fNumberOfRuns.redraw()
      redraw()
    }
    fIgnoredCount= ignoredCount;
  }

  def setErrorValue(value: Int) {
    fNumberOfErrors.setText(value.toString)
    redraw()
  }

  def setFailureValue(value: Int) {
    fNumberOfFailures.setText(value.toString)
    redraw()
  }
}