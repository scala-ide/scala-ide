package scala.tools.eclipse.refactoring.ui

import org.eclipse.ltk.ui.refactoring.UserInputWizardPage
import org.eclipse.jface.dialogs.IMessageProvider
import org.eclipse.swt.widgets.Composite
import org.eclipse.swt.layout.GridData
import org.eclipse.swt.layout.GridLayout
import org.eclipse.swt.SWT

class NewNameWizardPage(
    nameChanged: String => Unit,
    isValidName: String => Boolean,
    defaultName: String) extends UserInputWizardPage("??") {
              
  setMessage("Note that this is a preview release, make sure to check the generated changes.", IMessageProvider.WARNING)
  
  def createControl(parent: Composite) {
        
    val main = new Composite(parent, SWT.None)
    
    main.setLayout(new GridLayout)

    val textField = new LabeledTextField(main, newNameEntered, "New Name:", defaultName)

    textField.setLayoutData(new GridData(GridData.FILL_HORIZONTAL))
    
    setControl(main)
  }
  
  def newNameEntered(name: String) {
    if(isValidName(name)) {
      setPageComplete(true)
      setErrorMessage(null)
      nameChanged(name)
    } else {
      setPageComplete(false)
      setErrorMessage("The name \""+name +"\" is not a valid identifier.")
    }
  }
}
