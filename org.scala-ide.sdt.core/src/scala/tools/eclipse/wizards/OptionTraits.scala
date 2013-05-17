/*
 * Copyright 2010 LAMP/EPFL
 *
 * @author Tim Clendenen
 *
 */
package scala.tools.eclipse.wizards

import org.eclipse.jdt.core.ICompilationUnit
import org.eclipse.jdt.internal.ui.wizards.dialogfields.{ DialogField,
	SelectionButtonDialogFieldGroup }

import org.eclipse.jface.dialogs.IDialogSettings

import org.eclipse.swt.layout.GridData
import org.eclipse.swt.widgets.Composite

trait ClassOptions extends AbstractNewElementWizardPage {

  def initializeOptions(dialogSettings: IDialogSettings) {
	var createConstructors = false
	var createUnimplemented = true

	if (dialogSettings != null) {
	  val section = dialogSettings.getSection(PAGE_NAME)
	  if (section != null) {
		createConstructors = section.getBoolean(SETTINGS_CREATECONSTR)
		createUnimplemented = section.getBoolean(SETTINGS_CREATEUNIMPLEMENTED)
	  }
	}

	methodStubButtons.enableSelectionButton(0, false)
	methodStubButtons.setSelection(0, false)
	
	methodStubButtons.enableSelectionButton(1, true)
	methodStubButtons.setSelection(1, createConstructors)
	
	methodStubButtons.enableSelectionButton(2, true)
	methodStubButtons.setSelection(2, createUnimplemented)
  }

  def specifyModifierControls(composite: Composite, columns: Int) {
    DialogField.createEmptySpace(composite)
    val control = otherModifierButtons.getSelectionButtonsGroup(composite)
    val gd = new GridData(GridData.HORIZONTAL_ALIGN_FILL)
    gd.horizontalSpan = columns - 2
    control.setLayoutData(gd)
    DialogField.createEmptySpace(composite)
  }
}

trait ObjectOptions extends AbstractNewElementWizardPage {

  def initializeOptions(dialogSettings: IDialogSettings) {
	var createMain = false
	var createConstructors = false
	var createUnimplemented = false

	if (dialogSettings != null) {
	  val section = dialogSettings.getSection(PAGE_NAME)
	  if (section != null) {
	    createMain = section.getBoolean(SETTINGS_CREATEMAIN)
		createConstructors = section.getBoolean(SETTINGS_CREATECONSTR)
		createUnimplemented = section.getBoolean(SETTINGS_CREATEUNIMPLEMENTED)
	  }
	}

	methodStubButtons.enableSelectionButton(0, true)
	methodStubButtons.setSelection(0, createMain)
	
	methodStubButtons.enableSelectionButton(1, false)
	methodStubButtons.setSelection(1, createConstructors)
	
	methodStubButtons.enableSelectionButton(2, true)
	methodStubButtons.setSelection(2, createUnimplemented)
  }

  def specifyModifierControls(composite: Composite, columns: Int) {}

  override protected def getGeneratedTypeName = super.getGeneratedTypeName+"$"
}

trait PackageObjectOptions extends AbstractNewElementWizardPage {

  accessModifierButtons.setEnabled(false)

  def initializeOptions(dialogSettings: IDialogSettings) {
	var createMain = false
	var createConstructors = false
	var createUnimplemented = false

	if (dialogSettings != null) {
	  val section = dialogSettings.getSection(PAGE_NAME)
	  if (section != null) {
	    createMain = section.getBoolean(SETTINGS_CREATEMAIN)
		createConstructors = section.getBoolean(SETTINGS_CREATECONSTR)
		createUnimplemented = section.getBoolean(SETTINGS_CREATEUNIMPLEMENTED)
	  }
	}

	methodStubButtons.enableSelectionButton(0, false)
	methodStubButtons.setSelection(0, createMain)
	
	methodStubButtons.enableSelectionButton(1, false)
	methodStubButtons.setSelection(1, createConstructors)
	
	methodStubButtons.enableSelectionButton(2, true)
	methodStubButtons.setSelection(2, createUnimplemented)
  }

  def specifyModifierControls(composite: Composite, columns: Int) {}

  override protected def getGeneratedTypeName = "package$"
}

trait TraitOptions { self: AbstractNewElementWizardPage =>

  def initializeOptions(dialogSettings: IDialogSettings) {
	var createUnimplemented = true

	if (dialogSettings != null) {
	  val section = dialogSettings.getSection(PAGE_NAME)
	  if (section != null)
		createUnimplemented = section.getBoolean(SETTINGS_CREATEUNIMPLEMENTED)
	}

	methodStubButtons.enableSelectionButton(0, false)
	methodStubButtons.setSelection(0, false)
	
	methodStubButtons.enableSelectionButton(1, false)
	methodStubButtons.setSelection(1, false)
	
	methodStubButtons.enableSelectionButton(2, true)
	methodStubButtons.setSelection(2, createUnimplemented)
  }
	
  def specifyModifierControls(composite: Composite, columns: Int) {}
}

