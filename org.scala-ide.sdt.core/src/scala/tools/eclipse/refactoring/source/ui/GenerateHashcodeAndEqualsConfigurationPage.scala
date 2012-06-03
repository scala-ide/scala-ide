package scala.tools.eclipse
package refactoring.source.ui

import org.eclipse.ltk.ui.refactoring.UserInputWizardPage
import org.eclipse.swt.events.MouseAdapter
import org.eclipse.swt.events.MouseEvent
import org.eclipse.swt.layout.GridData
import org.eclipse.swt.layout.GridLayout
import org.eclipse.swt.widgets.Button
import org.eclipse.swt.widgets.Composite
import org.eclipse.swt.widgets.Event
import org.eclipse.swt.widgets.Label
import org.eclipse.swt.widgets.Listener
import org.eclipse.swt.widgets.Table
import org.eclipse.swt.widgets.TableItem
import org.eclipse.swt.SWT

/**
 * Wizard page for the GenerateHashcodeAndEquals refactoring.
 */
class GenerateHashcodeAndEqualsConfigurationPage(
    classParamNames: List[String],
    selectedParamsObs: List[String] => Unit,
    callSuperObs: Boolean => Unit) extends UserInputWizardPage("Generate hashCode and equals") {

  val headerLabelText: String = "Select the class parameters to include in the hashCode() and equals() methods"
  
  def createControl(parent: Composite) {
    initializeDialogUnits(parent)
    
    val composite = new Composite(parent, SWT.NONE)
    composite.setLayout(new GridLayout(2, false))
    
    val paramSelectionLabel = new Label(composite, SWT.WRAP)
    paramSelectionLabel.setText(headerLabelText)
    
    val paramSelectLabelGridData = new GridData(SWT.LEFT, SWT.CENTER, false, false, 2, 1)
    paramSelectionLabel.setLayoutData(paramSelectLabelGridData)

    
    val paramTable = new Table(composite, SWT.CHECK | SWT.BORDER)
    val paramTableGridData = new GridData(SWT.FILL, SWT.FILL, true, true, 1, 2)
    paramTable.setLayoutData(paramTableGridData)
    
    val tableItems = classParamNames.map { param =>
      val tableItem = new TableItem(paramTable, SWT.NONE)
      tableItem.setText(param)
      tableItem
    }
    
    def updateSelectedParams() {
      val checkedParams = tableItems.filter(_.getChecked).map(_.getText)
      selectedParamsObs(checkedParams)
    }
    
    paramTable.addListener(SWT.Selection, new Listener {
      def handleEvent(event: Event) {
        updateSelectedParams()
      }
    })
    
    val selectAllButton = new Button(composite, SWT.NONE)
    selectAllButton.setText("Select all")
    val selectAllButtonGridData = new GridData(SWT.CENTER, SWT.CENTER, false, false, 1, 1)
    selectAllButton.setLayoutData(selectAllButtonGridData)
    selectAllButton.addMouseListener(new MouseAdapter {
      override def mouseUp(me: MouseEvent) = {
        tableItems.foreach(_.setChecked(true))
        updateSelectedParams()
      }
    })
    
    val deselectAllButton = new Button(composite, SWT.NONE)
    deselectAllButton.setText("Deselect all")
    val deselectAllButtonGridData = new GridData(SWT.CENTER, SWT.CENTER, false, false, 1, 1)
    deselectAllButton setLayoutData deselectAllButtonGridData
    deselectAllButton.addMouseListener(new MouseAdapter {
      override def mouseUp(me: MouseEvent) = {
        tableItems.foreach(_.setChecked(false))
        updateSelectedParams()
      }
    })
    
    val superCallButton = new Button(composite, SWT.CHECK)
    superCallButton.setText("Insert calls to super")
    superCallButton.addMouseListener(new MouseAdapter() {
      override def mouseUp(event: MouseEvent) {
        callSuperObs(superCallButton.getSelection)
      }
    })
    
    val superCallButtonGridData = new GridData(SWT.BEGINNING, SWT.CENTER, false, false, 1, 1)
    superCallButton.setLayoutData(superCallButtonGridData)
    
    setControl(composite)
  }
  
}