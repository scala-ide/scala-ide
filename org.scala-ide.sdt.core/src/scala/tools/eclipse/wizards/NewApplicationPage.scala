package scala.tools.eclipse.wizards

import org.eclipse.jdt.core.IPackageFragment
import org.eclipse.jface.wizard.WizardPage
import org.eclipse.swt.layout._
import org.eclipse.swt.widgets.{ List => _, _}
import org.eclipse.swt.SWT

class NewApplicationPage(packageFragments: List[IPackageFragment]) extends WizardPage("New Scala Application") {

  setTitle("New Scala Application")
  setDescription("Create a new top-level Scala application")

  private var nameField: Text = _
  private var packageComboBox: Combo = _

  def createControl(parent: Composite) {
    val outerComposite = new Composite(parent, SWT.NONE)
    outerComposite.setLayout(new GridLayout)
    setControl(outerComposite)

    if (packageFragments.isEmpty)
      setErrorMessage("Cannot create a top-level Scala application unless a project or package is selected.")
    else {
      val packageGroup = makeGroup(outerComposite, "&Package:")
      packageGroup.setLayoutData(new GridData(GridData.FILL_HORIZONTAL))
      packageComboBox = makePackageComboBox(packageGroup)
      packageComboBox.setLayoutData(new GridData(GridData.FILL_HORIZONTAL))

      val nameGroup = makeGroup(outerComposite, "&Object name:")
      nameGroup.setLayoutData(new GridData(GridData.FILL_HORIZONTAL))
      nameField = new Text(nameGroup, SWT.LEFT + SWT.BORDER)
      nameField.setLayoutData(new GridData(GridData.FILL_HORIZONTAL))
    }
  }

  override def setVisible(visible: Boolean) {
    super.setVisible(visible)
    if (visible)
      nameField.setFocus()
  }

  def getApplicationName: String = {
    val name = nameField.getText.trim
    if (name endsWith ".scala")
      name.substring(0, name.length - ".scala".length)
    else
      name
  }

  def getSelectedPackage: IPackageFragment = packageFragments(packageComboBox.getSelectionIndex)

  private def makePackageComboBox(parent: Composite): Combo = {
    val packageComboBox = new Combo(parent, SWT.SINGLE | SWT.READ_ONLY)
    for (pkg <- packageFragments)
      packageComboBox.add(if (pkg.isDefaultPackage) "(default package)" else pkg.getElementName)
    packageComboBox.select(0)
    packageComboBox
  }

  private def makeGroup(parent: Composite, label: String): Group = {
    val group = new Group(parent, SWT.NONE)
    group.setText(label)
    group.setLayout(new GridLayout)
    group
  }

}