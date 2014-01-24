package scala.tools.eclipse.wizards

import org.eclipse.jdt.core.IPackageFragment
import org.eclipse.jface.wizard.IWizardPage
import org.eclipse.jface.wizard.WizardPage
import org.eclipse.swt.SWT
import org.eclipse.swt.layout.GridData
import org.eclipse.swt.layout.GridLayout
import org.eclipse.swt.widgets.Combo
import org.eclipse.swt.widgets.Composite
import org.eclipse.swt.widgets.Group
import org.eclipse.swt.widgets.Text

abstract class NewApplicationPage extends WizardPage("New Scala Application") {
  setTitle("New Scala Application")
  setDescription("Create a new top-level Scala application")

  final override def createControl(parent: Composite): Unit = {
    val outerComposite = new Composite(parent, SWT.NONE)
    outerComposite.setLayout(new GridLayout)
    populateWizard(outerComposite)
    setControl(outerComposite)
  }

  protected def populateWizard(container: Composite): Unit

  def getApplicationName: String
  def getSelectedPackage: Option[IPackageFragment]
}

object NewApplicationPage {

  def apply(packageFragments: List[IPackageFragment]): NewApplicationPage = {
    if (packageFragments.isEmpty) new NewApplicationPageError
    else new NewApplicationPageOk(packageFragments)
  }

  private class NewApplicationPageOk(packageFragments: List[IPackageFragment]) extends NewApplicationPage {
    require(packageFragments.nonEmpty)

    private var nameField: Text = _
    private var packageComboBox: Combo = _

    override protected def populateWizard(container: Composite): Unit = {
      val packageGroup = makeGroup(container, "&Package:")
      packageGroup.setLayoutData(new GridData(GridData.FILL_HORIZONTAL))
      packageComboBox = makePackageComboBox(packageGroup)
      packageComboBox.setLayoutData(new GridData(GridData.FILL_HORIZONTAL))

      val nameGroup = makeGroup(container, "&Object name:")
      nameGroup.setLayoutData(new GridData(GridData.FILL_HORIZONTAL))
      nameField = new Text(nameGroup, SWT.LEFT + SWT.BORDER)
      nameField.setLayoutData(new GridData(GridData.FILL_HORIZONTAL))
    }

    override def setVisible(visible: Boolean) {
      super.setVisible(visible)
      if (visible)
        nameField.setFocus()
    }

    override def getApplicationName: String = {
      val name = nameField.getText.trim
      if (name endsWith ".scala")
        name.substring(0, name.length - ".scala".length)
      else
        name
    }

    override def getSelectedPackage: Option[IPackageFragment] = Some(packageFragments(packageComboBox.getSelectionIndex))

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

  private class NewApplicationPageError extends NewApplicationPage {
    override protected def populateWizard(container: Composite): Unit = {
      setErrorMessage("Cannot create Scala Application unless a project or a package is selected.")
    }
    override def getApplicationName: String = "Scala Application Wizard"
    override def getSelectedPackage: Option[IPackageFragment] = None
  }
}