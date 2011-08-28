/*
 * Copyright 2010 LAMP/EPFL
 * 
 * @author Tim Clendenen
 * 
 */
package scala.tools.eclipse.wizards

class NewClassWizardPage extends {
  val declarationType = "Class"
} with AbstractNewElementWizardPage
  with ClassOptions

class NewTraitWizardPage extends {
  val declarationType = "Trait"
} with AbstractNewElementWizardPage
  with TraitOptions

class NewObjectWizardPage extends {
  val declarationType = "Object"
} with AbstractNewElementWizardPage
  with MuteLowerCaseTypeNameWarning
  with ObjectOptions

class NewPackageObjectWizardPage extends {
  val declarationType = "Package Object"
  override val imageName = "newpackage_object_wiz.png"
} with AbstractNewElementWizardPage
  with MuteLowerCaseTypeNameWarning
  with PackageObjectOptions {

  import org.eclipse.swt.widgets.Composite

  override protected def createTypeNameControls(composite: Composite, nColumns: Int) = {
    // first let `NewTypeWizardPage` create the type name component
    super.createTypeNameControls(composite, nColumns)
    // then make sure the field is not editable (the type name is automatically derived from the 
    // package's name. Look at `packageChanged()` method.
    setTypeName(buildTypeName, false)
    packageChanged()
  }

  // temp file name that is produced for a package object. The file will be
  // renamed to `package.scala` during `NewPackageObjectWizard.performFinish()`.
  // `package.scala` cannot be used immediately because Eclipse validates 
  // the name against some JavaConventions (class with static methods) and 
  // we cannot replace it with a ScalaConventions class.
  // TODO: We should really consider implementing our own `NewTypeWizardPage`
  //       to overcome this and other impediments in the wizard.
  override def getCompilationUnitName(typeName: String) = "_package.scala"

  override protected def packageChanged() = {
    val status = super.packageChanged()

    setTypeName(buildTypeName, false)

    status
  }

  private def buildTypeName = {
    // the type's name is derived from the package's name
    //e.g., if the package name is `foo.bar`, the type name is `bar 
    getPackageText.split('.').last
  }

  override protected def getPackageNameToInject = {
    // remove the last sub-package (the removed sub-package is used
    // as type's name for the produced `package object`.
    val pack = getPackageText.split('.').init.mkString(".")

    if (pack.isEmpty) None
    else Some(pack)
  }
}

/** Mixin this trait to mute lowercase type's name warning. */
trait MuteLowerCaseTypeNameWarning extends AbstractNewElementWizardPage {
  import org.eclipse.jdt.internal.ui.dialogs.StatusInfo
  import org.eclipse.jdt.internal.corext.util.Messages
  import org.eclipse.jdt.internal.core.util.{ Messages => InternalMessages }
  import org.eclipse.jdt.internal.ui.wizards.NewWizardMessages

  override protected def typeNameChanged() = {
    val status = super.typeNameChanged()

    // object's name are allowed to be lowercase
    val lowerCaseTypeNameWarningMessage = Messages.format(NewWizardMessages.NewTypeWizardPage_warning_TypeNameDiscouraged, InternalMessages.convention_type_lowercaseName)

    if (status != null && status.getMessage != null && status.getMessage.equals(lowerCaseTypeNameWarningMessage))
      StatusInfo.OK_STATUS // swallow warning
    else
      status
  }
}

class NewClassWizard
  extends AbstractNewElementWizard(new NewClassWizardPage())

class NewTraitWizard
  extends AbstractNewElementWizard(new NewTraitWizardPage())

class NewObjectWizard
  extends AbstractNewElementWizard(new NewObjectWizardPage())

class NewPackageObjectWizard
  extends AbstractNewElementWizard(new NewPackageObjectWizardPage()) {
  override def performFinish(): Boolean = {
    val isOk = super.performFinish()

    if (isOk) renameResource()

    isOk
  }

  /** Rename the `package object` resource's file from `_package.scala` to `package.scala`.*/
  private def renameResource() {
    import scala.tools.eclipse.util.SWTUtils
    import org.eclipse.ltk.core.refactoring.resource.RenameResourceChange

    SWTUtils.asyncExec({
      val res = wizardPage.getModifiedResource()
      val rename = new RenameResourceChange(res.getFullPath, "package.scala")

      val monitor = new org.eclipse.core.runtime.NullProgressMonitor
      rename.perform(monitor)
    })
  }
}

