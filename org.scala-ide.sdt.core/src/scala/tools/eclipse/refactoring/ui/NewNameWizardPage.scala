package scala.tools.eclipse
package refactoring
package ui

import org.eclipse.jface.dialogs.IMessageProvider
import org.eclipse.ltk.ui.refactoring.UserInputWizardPage
import org.eclipse.swt.layout.{GridData, GridLayout}
import org.eclipse.swt.widgets.Composite
import org.eclipse.swt.SWT
import org.eclipse.ui.PlatformUI

/**
 * This wizard page prompts the user to enter a new name. If an invalid name is entered, 
 * an error message is displayed.
 * 
 * @param isValidName A validation function that returns true when the name is valid.
 * @param nameChanged A callback that is called with the changed name, but only when
 * the name is valid.
 * @param defaultName The initial name that is displayed when the page is opened.
 */
class NewNameWizardPage(
    nameChanged: String => Unit,
    isValidName: String => Boolean,
    defaultName: String,
    helpId: String) extends UserInputWizardPage("New Name") {

  setMessage("Note that this is a preview release, make sure to check the generated changes.", IMessageProvider.INFORMATION)
  
  def createControl(parent: Composite) {
        
    val main = new Composite(parent, SWT.None)
    
    main.setLayout(new GridLayout)

    val textField = new LabeledTextField(main, newNameEntered, "New Name:", defaultName)

    textField.setLayoutData(new GridData(GridData.FILL_HORIZONTAL))

    textField.setFocus()

    setControl(main)
  }
  
  override def setVisible(visible: Boolean) = {
    super.setVisible(visible)
    if(visible) {
      PlatformUI.getWorkbench.getHelpSystem.setHelp(getControl, "org.scala-ide.sdt.core." + helpId)            
    }
  }
  
  def newNameEntered(name: String) {
    if(name == defaultName) {
      setPageComplete(false)
      setErrorMessage(null)
    } else if(isValidName(name)) {
      setPageComplete(true)
      setErrorMessage(null)
      nameChanged(name)
    } else {
      setPageComplete(false)
      setErrorMessage("The name \""+name +"\" is not a valid identifier.")
    }
  }
}
