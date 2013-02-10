package scala.tools.eclipse
package refactoring.source.ui

import refactoring.source.ClassParameterDrivenIdeRefactoring
import scala.tools.eclipse.util.SWTUtils.noArgFnToSelectionAdapter
import scala.tools.eclipse.util.SWTUtils.noArgFnToMouseUpListener

import org.eclipse.ltk.ui.refactoring.UserInputWizardPage
import org.eclipse.swt.SWT
import org.eclipse.swt.events.MouseAdapter
import org.eclipse.swt.events.MouseEvent
import org.eclipse.swt.events.SelectionAdapter
import org.eclipse.swt.events.SelectionEvent
import org.eclipse.swt.layout.FillLayout
import org.eclipse.swt.layout.GridData
import org.eclipse.swt.layout.GridLayout
import org.eclipse.swt.widgets.Button
import org.eclipse.swt.widgets.Composite
import org.eclipse.swt.widgets.Event
import org.eclipse.swt.widgets.Group
import org.eclipse.swt.widgets.Label
import org.eclipse.swt.widgets.Listener
import org.eclipse.swt.widgets.Table
import org.eclipse.swt.widgets.TableItem

trait GenerateHashcodeAndEqualsConfigurationPageGenerator {

  this: ClassParameterDrivenIdeRefactoring =>

  import refactoring._
  /**
   * Wizard page for the GenerateHashcodeAndEquals refactoring.
   */
  class GenerateHashcodeAndEqualsConfigurationPage(
      prepResult: PreparationResult,
      selectedParamsObs: List[String] => Unit,
      callSuperObs: Boolean => Unit,
      keepExistingEqualityMethodsObs: Boolean => Unit) extends UserInputWizardPage("Generate hashCode and equals") {

    val headerLabelText: String = "Select the class parameters to include in the hashCode() and equals() methods"

    def createControl(parent: Composite) {
      initializeDialogUnits(parent)

      val composite = new Composite(parent, SWT.NONE)
      composite.setLayout(new GridLayout(2, false))

      prepResult.existingEqualityMethods match {
        case Nil => // nothing to do
        case ms => {
          val existingMethodNames = ms.map(_.nameString) match {
            case n1::n2::Nil => n1 + " and " + n2 + "."
            case names @ n1::n2::ns => names.init.mkString(", ") + " and " + names.last + "."
            case names => names.mkString(", ") + "."
          }
          val existingLabel = new Label(composite, SWT.WRAP)
          val implStr = if(ms.size == 1) "implementation" else "implementations"
          existingLabel.setText("Found existing " + implStr + " for " + existingMethodNames)
          val existingLabelGridData = new GridData(SWT.LEFT, SWT.CENTER, false, false, 2, 1)
          existingLabel.setLayoutData(existingLabelGridData)

          val keepOrReplaceLabel = new Label(composite, SWT.WRAP)
          keepOrReplaceLabel.setText("Do you want to keep or replace the existing " + implStr + "?")
          val keepOrReplaceLabelGridData = new GridData(SWT.LEFT, SWT.CENTER, false, false, 2, 1)
          keepOrReplaceLabel.setLayoutData(keepOrReplaceLabelGridData)

          val keepSelectionGroup = new Group(composite, SWT.NONE)
          keepSelectionGroup.setLayout(new FillLayout)
          val keepSelectionGroupGridData = new GridData(SWT.CENTER, SWT.CENTER, true, false, 1, 1)
          keepSelectionGroup.setLayoutData(keepSelectionGroupGridData)

          val keepBtn = new Button(keepSelectionGroup, SWT.RADIO)
          keepBtn setText "Keep"

          val replaceBtn = new Button(keepSelectionGroup, SWT.RADIO)
          replaceBtn setText "Replace"

          keepBtn setSelection true
          keepExistingEqualityMethodsObs(true)

          keepBtn addSelectionListener { () =>
            keepBtn setSelection true
            replaceBtn setSelection false
            keepExistingEqualityMethodsObs(true)
          }

          replaceBtn setSelection false
          replaceBtn addSelectionListener { () =>
              replaceBtn setSelection true
              keepBtn setSelection false
              keepExistingEqualityMethodsObs(false)
          }

        }
      }

      val paramSelectionLabel = new Label(composite, SWT.WRAP)
      paramSelectionLabel.setText(headerLabelText)

      val paramSelectLabelGridData = new GridData(SWT.LEFT, SWT.CENTER, false, false, 2, 1)
      paramSelectionLabel.setLayoutData(paramSelectLabelGridData)

      val paramTable = new Table(composite, SWT.CHECK | SWT.BORDER)
      val paramTableGridData = new GridData(SWT.FILL, SWT.FILL, true, true, 1, 2)
      paramTable.setLayoutData(paramTableGridData)

      val tableItems = prepResult.classParams.map { case (param, _) =>
        val tableItem = new TableItem(paramTable, SWT.NONE)
        tableItem.setText(param.nameString)
        tableItem
      }

      def updateSelectedParams() {
        val checkedParams = tableItems.filter(_.getChecked).map(_.getText)
        selectedParamsObs(checkedParams)
      }

      paramTable addSelectionListener { () => updateSelectedParams() }

      val selectAllButton = new Button(composite, SWT.NONE)
      selectAllButton.setText("Select all")
      val selectAllButtonGridData = new GridData(SWT.CENTER, SWT.CENTER, false, false, 1, 1)
      selectAllButton.setLayoutData(selectAllButtonGridData)
      selectAllButton addMouseListener { () =>
        tableItems.foreach(_.setChecked(true))
        updateSelectedParams()
      }

      val deselectAllButton = new Button(composite, SWT.NONE)
      deselectAllButton.setText("Deselect all")
      val deselectAllButtonGridData = new GridData(SWT.CENTER, SWT.CENTER, false, false, 1, 1)
      deselectAllButton setLayoutData deselectAllButtonGridData
      deselectAllButton addMouseListener { () =>
        tableItems.foreach(_.setChecked(false))
        updateSelectedParams()
      }

      val superCallButton = new Button(composite, SWT.CHECK)
      superCallButton.setText("Insert calls to super")
      superCallButton addMouseListener { () => callSuperObs(superCallButton.getSelection) }

      val superCallButtonGridData = new GridData(SWT.BEGINNING, SWT.CENTER, false, false, 1, 1)
      superCallButton.setLayoutData(superCallButtonGridData)

      setControl(composite)
    }

  }
}