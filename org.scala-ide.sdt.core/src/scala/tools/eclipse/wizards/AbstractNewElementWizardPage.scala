/*
 * Copyright 2010 LAMP/EPFL
 *
 *
 */
package scala.tools.eclipse.wizards

import org.eclipse.core.resources.IResource
import org.eclipse.core.runtime.CoreException
import org.eclipse.core.runtime.FileLocator
import org.eclipse.core.runtime.IPath
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.core.runtime.IStatus
import org.eclipse.core.runtime.NullProgressMonitor
import org.eclipse.core.runtime.Path
import org.eclipse.core.runtime.SubProgressMonitor
import org.eclipse.jdt.core.Flags
import org.eclipse.jdt.core.ICompilationUnit
import org.eclipse.jdt.core.IJavaElement
import org.eclipse.jdt.core.IPackageFragment
import org.eclipse.jdt.core.IPackageFragmentRoot
import org.eclipse.jdt.core.IType
import org.eclipse.jdt.core.ITypeHierarchy
import org.eclipse.jdt.core.JavaModelException
import org.eclipse.jdt.core.Signature
import org.eclipse.jdt.core.WorkingCopyOwner
import org.eclipse.jdt.core.dom.AST
import org.eclipse.jdt.core.dom.ASTParser
import org.eclipse.jdt.core.dom.CompilationUnit
import org.eclipse.jdt.internal.corext.codemanipulation.StubUtility
import org.eclipse.jdt.internal.corext.util.JavaModelUtil
import org.eclipse.jdt.internal.ui.dialogs.StatusInfo
import org.eclipse.jdt.internal.ui.dialogs.TextFieldNavigationHandler
import org.eclipse.jdt.internal.ui.refactoring.contentassist.ControlContentAssistHelper
import org.eclipse.jdt.internal.ui.refactoring.contentassist.JavaPackageCompletionProcessor
import org.eclipse.jdt.internal.ui.util.SWTUtil
import org.eclipse.jdt.internal.ui.wizards.NewWizardMessages
import org.eclipse.jdt.internal.ui.wizards.dialogfields.DialogField
import org.eclipse.jdt.internal.ui.wizards.dialogfields.IDialogFieldListener
import org.eclipse.jdt.internal.ui.wizards.dialogfields.IStringButtonAdapter
import org.eclipse.jdt.internal.ui.wizards.dialogfields.LayoutUtil
import org.eclipse.jdt.internal.ui.wizards.dialogfields.SelectionButtonDialogField
import org.eclipse.jdt.internal.ui.wizards.dialogfields.SelectionButtonDialogFieldGroup
import org.eclipse.jdt.internal.ui.wizards.dialogfields.StringButtonDialogField
import org.eclipse.jdt.ui.wizards.NewTypeWizardPage
import org.eclipse.jface.dialogs.Dialog
import org.eclipse.jface.dialogs.IDialogSettings
import org.eclipse.jface.resource.ImageDescriptor
import org.eclipse.jface.viewers.IStructuredSelection
import org.eclipse.swt.SWT
import org.eclipse.swt.layout.GridData
import org.eclipse.swt.layout.GridLayout
import org.eclipse.swt.widgets.Composite
import collection.Seq
import collection.mutable.Buffer
import scala.tools.eclipse.ScalaPlugin._
import scala.tools.eclipse.formatter.ScalaFormatterCleanUpProvider
import scala.tools.eclipse.javaelements.ScalaSourceFile
import scala.tools.eclipse.logging.HasLogger

abstract class AbstractNewElementWizardPage extends NewTypeWizardPage(1, "") with HasLogger {

  val declarationType: String

  val imageName = "new" + declarationType.replace(' ', '_').toLowerCase + "_wiz.gif"
  val iPath = new Path("icons/full/wizban").append(imageName)
  val url = FileLocator.find(plugin.getBundle, iPath, null)

  setImageDescriptor(ImageDescriptor.createFromURL(url))
  setTitle("Scala " + declarationType)
  setDescription("Create a new Scala " + declarationType)

  val PAGE_NAME = "New" + declarationType + "WizardPage"
  val DEFAULT_SUPER_TYPE = "scala.AnyRef"
  val SETTINGS_CREATEMAIN = "create_main"
  val SETTINGS_CREATECONSTR = "create_constructor"
  val SETTINGS_CREATEUNIMPLEMENTED = "create_unimplemented"

  protected object dialogFieldListener extends IDialogFieldListener {
    def dialogFieldChanged(field: DialogField) {
      doStatusUpdate()
    }
  }

  val accessModifierNames = Array(
    NewWizardMessages.NewTypeWizardPage_modifiers_default,
    NewWizardMessages.NewTypeWizardPage_modifiers_protected,
    NewWizardMessages.NewTypeWizardPage_modifiers_private)

  val accessModifierButtons =
    new SelectionButtonDialogFieldGroup(SWT.RADIO, accessModifierNames, 3)

  accessModifierButtons.setDialogFieldListener(dialogFieldListener)
  accessModifierButtons.setLabelText(getModifiersLabel)
  accessModifierButtons.setSelection(0, true)

  def defaultSelected = accessModifierButtons.isSelected(0)
  def protectedSelected = accessModifierButtons.isSelected(1)
  def privateSelected = accessModifierButtons.isSelected(2)

  val otherModifierNames = Array(
    NewWizardMessages.NewTypeWizardPage_modifiers_abstract,
    NewWizardMessages.NewTypeWizardPage_modifiers_final)

  val otherModifierButtons =
    new SelectionButtonDialogFieldGroup(SWT.CHECK, otherModifierNames, 4)

  otherModifierButtons.setDialogFieldListener(dialogFieldListener)

  def abstractSelected = otherModifierButtons.isSelected(0)
  def finalSelected = otherModifierButtons.isSelected(1)

  val methodStubNames = Array(
    NewWizardMessages.NewClassWizardPage_methods_main,
    NewWizardMessages.NewClassWizardPage_methods_constructors,
    NewWizardMessages.NewClassWizardPage_methods_inherited)

  val methodStubButtons =
    new SelectionButtonDialogFieldGroup(SWT.CHECK, methodStubNames, 1)
  methodStubButtons.setDialogFieldListener(dialogFieldListener)
  methodStubButtons.setLabelText(
    NewWizardMessages.NewClassWizardPage_methods_label)

  def createMainSelected = methodStubButtons.isSelected(0)
  def createConstructorsSelected = methodStubButtons.isSelected(1)
  def createInheritedSelected = methodStubButtons.isSelected(2)

  private object folderButtonListener extends IDialogFieldListener {
    def dialogFieldChanged(field: DialogField) {
      fFolderField.setEnabled(fFolderButton.isSelected())
      updateFolderPath()
    }
  }

  val fFolderButton= new SelectionButtonDialogField(SWT.CHECK);
  fFolderButton.setDialogFieldListener(folderButtonListener);
  fFolderButton.setLabelText("Folder:")
  fFolderButton.setSelection(false)

  private object specifyFolderBrowseListener extends IStringButtonAdapter {
    def changeControlPressed(field: DialogField) {
      val packageFragment = choosePackage();
      if (packageFragment != null) fFolderField.setText(packageFragment.getElementName())
    }
  }

  val fFolderField = new StringButtonDialogField(specifyFolderBrowseListener);
  fFolderField.setEnabled(false)
  fFolderField.setButtonLabel("Browse...");

  val fSelectedFolderCompletionProcessor = new JavaPackageCompletionProcessor()


  protected var createdType: IType = _

  def init(selection: IStructuredSelection): Unit = {
    val jelem = getInitialJavaElement(selection)
    initContainerPage(jelem)
    initTypePage(jelem)
    initFolder(jelem)
    val dialogSettings = getDialogSettings()
    initializeOptions(dialogSettings)
  }

  def initFolder(jelem: IJavaElement): Unit = {
    jelem match {
      case scala: ScalaSourceFile =>
        val packageDeclarations = scala.getCompilationUnit.getPackageDeclarations()
        packageDeclarations.toList match {
          case pkgDeclaration :: _ =>
            val fragment = getPackageFragmentRoot().getPackageFragment(pkgDeclaration.getElementName())
            setPackageFragment(fragment, true)

            //if the package doesn't match the folder, set the folder
            //eg com.test.test2.Class in src/com/tests should have the folder set to com.test
            val containingFolder = scala.getCompilationUnit.getParent() /*should be IPackageFragment*/ //realpkg.getParent().getParent().asInstanceOf[IPackageFragment]
            if (pkgDeclaration.getElementName() != containingFolder.getElementName()) {
              fFolderField.setText(containingFolder.getElementName())
              fFolderButton.setSelection(true)
            }
          case _ =>
        }
      case _ =>
    }
  }

  def initializeOptions(dialogSettings: IDialogSettings): Unit

  override def getModifiers = {
    var modifiers = 0
    if (privateSelected) modifiers += F_PRIVATE
    else if (protectedSelected) modifiers += F_PROTECTED
    if (abstractSelected) modifiers += F_ABSTRACT
    if (finalSelected) modifiers += F_FINAL
    modifiers
  }

  override def setModifiers(modifiers: Int, canBeModified: Boolean) = {

    if (Flags.isPrivate(modifiers))
      accessModifierButtons.setSelection(2, true)
    else if (Flags.isProtected(modifiers))
      accessModifierButtons.setSelection(1, true)
    else
      accessModifierButtons.setSelection(0, true)

    if (Flags.isAbstract(modifiers))
      otherModifierButtons.setSelection(0, true)

    if (Flags.isFinal(modifiers))
      otherModifierButtons.setSelection(1, true)

    accessModifierButtons.setEnabled(canBeModified)
    otherModifierButtons.setEnabled(canBeModified)
  }

  override def setVisible(visible: Boolean) {
    super.setVisible(visible)
    if (visible) {
      setFocus()
    } else {
      val dialogSettings = getDialogSettings()
      if (dialogSettings != null) {
        var section = dialogSettings.getSection(PAGE_NAME)
        if (section == null) {
          section = dialogSettings.addNewSection(PAGE_NAME)
        }
        section.put(SETTINGS_CREATEMAIN, createMainSelected)
        section.put(SETTINGS_CREATECONSTR, createConstructorsSelected)
        section.put(SETTINGS_CREATEUNIMPLEMENTED, createInheritedSelected)
      }
    }
  }

  private def doStatusUpdate() {
    val parentStatus = if (isEnclosingTypeSelected) fEnclosingTypeStatus
    else fPackageStatus

    val status = Array(fContainerStatus, parentStatus, fTypeNameStatus,
      fModifierStatus, fSuperClassStatus,
      fSuperInterfacesStatus)

    // the mode severe status will be displayed and the OK button
    // enabled/disabled.
    updateStatus(status);
  }

  /*
   * @see NewContainerWizardPage#handleFieldChanged
   */
  override protected def handleFieldChanged(fieldName: String) {
    super.handleFieldChanged(fieldName)
    doStatusUpdate()
  }

  override protected def getSuperInterfacesLabel(): String =
    "Traits and \nInterfaces:"

  override protected def createModifierControls(composite: Composite, columns: Int) = {
    LayoutUtil.setHorizontalSpan(accessModifierButtons.getLabelControl(composite), 1)
    val control1 = accessModifierButtons.getSelectionButtonsGroup(composite)
    val gd1 = new GridData(GridData.HORIZONTAL_ALIGN_FILL)
    gd1.horizontalSpan = columns - 2
    control1.setLayoutData(gd1)
    DialogField.createEmptySpace(composite)

    specifyModifierControls(composite, columns)
  }

  def specifyModifierControls(composite: Composite, columns: Int): Unit

  protected def createMethodStubSelectionControls(composite: Composite, columns: Int) {
    val labelControl = methodStubButtons.getLabelControl(composite)
    LayoutUtil.setHorizontalSpan(labelControl, columns)
    DialogField.createEmptySpace(composite)
    val buttonGroup = methodStubButtons.getSelectionButtonsGroup(composite)
    LayoutUtil.setHorizontalSpan(buttonGroup, columns - 1)
  }

  /*
   * Override to pick the UI components relevant for a Scala elements
   */
  override def createControl(parent: Composite) = {
    initializeDialogUnits(parent)

    val composite = new Composite(parent, SWT.NONE)
    composite.setFont(parent.getFont)
    val columns = 4

    val layout = new GridLayout()
    layout.numColumns = columns
    composite.setLayout(layout)

    createContainerControls(composite, columns)
    createPackageControls(composite, columns)
    //createEnclosingTypeControls(composite, nColumns)
    createFolderControls(composite, columns)

    createSeparator(composite, columns)

    createTypeNameControls(composite, columns)
    createModifierControls(composite, columns)
    createSuperClassControls(composite, columns)
    setSuperClass(DEFAULT_SUPER_TYPE, true)

    createSuperInterfacesControls(composite, columns)
    createMethodStubSelectionControls(composite, columns)
    createCommentControls(composite, columns)
    enableCommentControl(true)

    setControl(composite)

    Dialog.applyDialogFont(composite)
  }

  protected def createFolderControls(composite: Composite, columns: Int) {
    val tabGroup = new Composite(composite, SWT.NONE)
    val layout = new GridLayout()
    layout.marginWidth = 0
    layout.marginHeight = 0
    tabGroup.setLayout(layout)
    fFolderButton.doFillIntoGrid(tabGroup, 1)

    val gd = new GridData(GridData.FILL_HORIZONTAL)
    gd.widthHint = getMaxFieldWidth()
    gd.horizontalSpan = columns - 2
    val text = fFolderField.getTextControl(composite)
    text.setLayoutData(gd)

    val button = fFolderField.getChangeControl(composite)
    val buttonGd = new GridData(GridData.HORIZONTAL_ALIGN_FILL)
    buttonGd.widthHint = SWTUtil.getButtonWidthHint(button)
    button.setLayoutData(buttonGd)
    ControlContentAssistHelper.createTextContentAssistant(text, fSelectedFolderCompletionProcessor) //we really do want java package completion here so it lists folders
    TextFieldNavigationHandler.install(text)
  }

  override protected def packageChanged(): IStatus = {
    updateFolderPath()
    super.packageChanged()
  }

  private def updateFolderPath(): Unit = {
    if (!fFolderButton.isSelected()) fFolderField.setText(getPackageText())
  }
  override protected def containerChanged(): IStatus = {
    fSelectedFolderCompletionProcessor.setPackageFragmentRoot(getPackageFragmentRoot()) //TODO: why does ctrl space not show the one without boolean
    super.containerChanged()
  }

  protected def makeCreatedType(implicit parentCU: ICompilationUnit) = {
     createdType = parentCU.getType(getGeneratedTypeName)
  }

  def getPackageFragmentForFile() = getFolderName match {
    case Some(folderName) => getPackageFragmentRoot.getPackageFragment(folderName)
    case None => getPackageFragment
  }

  /* (non-Javadoc)
   * @see org.eclipse.jdt.ui.wizards.NewTypeWizardPage#createType(org.eclipse.core.runtime.IProgressMonitor)
   */
  override def createType(progressMonitor: IProgressMonitor): Unit = {

    def reconcile(cu: ICompilationUnit,
      astLevel: Int = ICompilationUnit.NO_AST,
      forceProblemDetection: Boolean = false,
      enableStatementsRecovery: Boolean = false,
      workingCopyOwner: WorkingCopyOwner = null,
      monitor: IProgressMonitor = null): Unit = {
      cu.reconcile(astLevel, forceProblemDetection,
        enableStatementsRecovery, workingCopyOwner, monitor)
    }

    def superTypes: List[String] = {
      val javaArrayList = getSuperInterfaces
      val jual = javaArrayList.toArray(new Array[String](javaArrayList.size))
      (getSuperClass +: jual).toList
    }

    val monitor = if (progressMonitor == null) new NullProgressMonitor()
    else progressMonitor

    monitor.beginTask(NewWizardMessages.NewTypeWizardPage_operationdesc, 8)

    implicit val packageFragment = {
      val rt = getPackageFragmentRoot
      val pf = getPackageFragmentForFile
      var p = pf match {
        case ipf: IPackageFragment => ipf
        case _ => rt.getPackageFragment("")
      }
      p.exists match {
        case true => monitor.worked(1)
        case _ => p = rt.createPackageFragment(pf.getElementName, true,
          new SubProgressMonitor(monitor, 1))
      }
      p
    }

    implicit val ld = StubUtility.getLineDelimiterUsed(
      packageFragment.getJavaProject)
    val typeName = getTypeNameWithoutParameters
    val cuName = getCompilationUnitName(typeName)

    try {

      val parentCU = packageFragment.createCompilationUnit(
        cuName, "", false, new SubProgressMonitor(monitor, 2))

      parentCU.becomeWorkingCopy(new SubProgressMonitor(monitor, 1))

      import CodeBuilder._

      type CommentGetter = (ICompilationUnit, String) => String

      def comment(cg: CommentGetter): Option[String] = {
        val s = cg(parentCU, ld)
        toOption(in = s)(guard = s != null && s.nonEmpty)
      }

      def elementModifiers = {
        val mods = getModifiers
        mods match {
          case 0 => ""
          case _ => Flags.toString(mods) + " "
        }
      }

      import templates._

      // generate basic element skeleton

      val buffer = new BufferSupport.Buffer {
        val underlying = parentCU.getBuffer
        def append(s: String) = underlying.append(s)
        def getLength() = underlying.getLength()
        def replace(offset: Int, length: Int, text: String) = underlying.replace(offset, length, text)
        def getContents() = underlying.getContents()
      }
      //start control of buffer
      val cb = CodeBuilder(getPackageNameToInject.getOrElse(""), superTypes, buffer)
      cb.append(commentTemplate(comment(getFileComment _)))
      cb.append(packageTemplate(getPackageNameToInject))
      cb.writeImports // to buffer
      cb.append(commentTemplate(comment(getTypeComment _)))
      cb.append(elementModifiers)
      cb.append(declarationType.toLowerCase)
      cb.createElementDeclaration(getTypeName, superTypes, buffer)
      cb.append(bodyStub)

      reconcile(cu = parentCU)

      makeCreatedType(parentCU)

      // refine the created type
      val typeHierarchy = createdType.newSupertypeHierarchy(Array(parentCU),
        new SubProgressMonitor(monitor, 1))

      cb.finishReWrites(typeHierarchy, createdType)(
        createConstructorsSelected)(createInheritedSelected)(
          createMainSelected)

      //end control of buffer

      val cu = createdType.getCompilationUnit
      reconcile(cu = cu)

      if (monitor.isCanceled) throw new InterruptedException()

      val formatter= new ScalaFormatterCleanUpProvider()
      val textChange= formatter.createCleanUp(cu).createChange(monitor)
      textChange.perform(monitor)

      cu.commitWorkingCopy(true, new SubProgressMonitor(monitor, 1))
      parentCU.discardWorkingCopy
    } catch {
      case ex: JavaModelException => eclipseLog.error(ex)
    } finally {
      monitor.done()
    }
  }

  /** Return the package declaration used in the resources created by the wizard.
   * This is needed because the package declaration may be different from the
   * file's location (as in the case of a `package object`).*/
  protected def getPackageNameToInject = !getPackageFragment.isDefaultPackage match {
    case true => Some(getPackageFragment.getElementName)
    case _ => None
  }

  protected def getTypeNameWithoutParameters() = getTypeName.split('[')(0)

  override def getCompilationUnitName(typeName: String) = typeName + ".scala"

  def getFolderName(): Option[String] = if (fFolderButton.isSelected()) Some(fFolderField.getText()) else None

  /*
   * Override because getTypeNameWithoutParameters is a private method in
   * superclass
   *
   * @see org.eclipse.jdt.ui.wizards.NewTypeWizardPage#getModifiedResource()
   */
  override def getModifiedResource(): IResource = {
    val enclosing = getEnclosingType()
    if (enclosing != null) {
      return enclosing.getResource()
    }
    val pack = getPackageFragmentForFile()
    if (pack != null) {
      val cuName = getCompilationUnitName(getTypeNameWithoutParameters())
      return pack.getCompilationUnit(cuName).getResource()
    }
    null
  }

  override def getCreatedType() = createdType

  override protected def typeNameChanged(): IStatus = {

    val status = super.typeNameChanged.asInstanceOf[StatusInfo]
    logger.info(">>>> Status = " + status)
    val pack = getPackageFragment

    if (pack != null) {

      val project = pack.getJavaProject

      try {
        if (!plugin.isScalaProject(project.getProject)) {
          val msg = project.getElementName + " is not a Scala project"
          logger.info(msg)
          status.setError(msg)
        }
      } catch {
        case _: CoreException => status.setError(
          "Exception when accessing project natures for " +
            project.getElementName)
      }

      if (!isEnclosingTypeSelected && (status.getSeverity < IStatus.ERROR)) {
        try {
          val theType = project.findType(pack.getElementName, getGeneratedTypeName)
          if (theType != null) {
            status.setError(
              NewWizardMessages.NewTypeWizardPage_error_TypeNameExists)
          }
        } catch {
          case _: JavaModelException =>
        }
      }
    }
    status
  }

  /** The type's name that is generated by the wizard.*/
  protected def getGeneratedTypeName = getTypeNameWithoutParameters

  def isDefaultPackage = getPackageText() == ""

  def getFullyQualifiedName = if (isDefaultPackage) getTypeNameWithoutParameters else getPackageText() + "." + getTypeNameWithoutParameters

  protected def initializeIfNotNull(dialogSettings: IDialogSettings)(f: IDialogSettings => Unit) {
    if (dialogSettings != null) {
      val section = dialogSettings.getSection(PAGE_NAME)
      if (section != null)
        f(section)
    }
  }
}
