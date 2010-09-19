/*
 * Copyright 2010 LAMP/EPFL
 * 
 * @author Tim Clendenen
 * 
 */
package scala.tools.eclipse.wizards

import scala.tools.eclipse.Tracer
import org.eclipse.core.resources.IResource

import org.eclipse.core.runtime.{ CoreException, FileLocator, IPath, 
	IProgressMonitor, IStatus, NullProgressMonitor, Path, SubProgressMonitor }

import org.eclipse.jdt.core.{ Flags, ICompilationUnit, IJavaElement, 
	IPackageFragment, IPackageFragmentRoot, IType, ITypeHierarchy,
	JavaModelException, Signature, WorkingCopyOwner }

import org.eclipse.jdt.core.dom.{ AST, ASTParser, CompilationUnit }

import org.eclipse.jdt.internal.corext.codemanipulation.StubUtility
import org.eclipse.jdt.internal.corext.util.JavaModelUtil
import org.eclipse.jdt.internal.ui.dialogs.StatusInfo
import org.eclipse.jdt.internal.ui.wizards.NewWizardMessages
import org.eclipse.jdt.internal.ui.wizards.dialogfields.{ DialogField, 
	IDialogFieldListener, LayoutUtil, SelectionButtonDialogFieldGroup }

import org.eclipse.jdt.ui.wizards.NewTypeWizardPage

import org.eclipse.jface.dialogs.{ Dialog, IDialogSettings }
import org.eclipse.jface.resource.ImageDescriptor
import org.eclipse.jface.viewers.IStructuredSelection

import org.eclipse.swt.SWT
import org.eclipse.swt.layout.{ GridData, GridLayout }
import org.eclipse.swt.widgets.Composite

import collection.Seq
import collection.mutable.Buffer

import scala.tools.eclipse.ScalaPlugin._

abstract class AbstractNewElementWizardPage extends NewTypeWizardPage(1,"") {
  
  val declarationType: String
  
  val imageName = "new" + declarationType.toLowerCase + "_wiz.gif"
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
      updateStatus(modifiersChanged)
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
  
  protected var createdType : IType = _
  
  def init(selection: IStructuredSelection): Unit = {
    val jelem = getInitialJavaElement(selection)
    initContainerPage(jelem)
    initTypePage(jelem)
    val dialogSettings = getDialogSettings()
    initializeOptions(dialogSettings)
  }
  
  def initializeOptions(dialogSettings: IDialogSettings): Unit
  
  override def getModifiers = {
    var modifiers = 0
    if(privateSelected) modifiers += F_PRIVATE
    else if(protectedSelected) modifiers += F_PROTECTED
    if(abstractSelected) modifiers += F_ABSTRACT
    if(finalSelected) modifiers += F_FINAL
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
	} 
	else {
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
	val parentStatus = if(isEnclosingTypeSelected) fEnclosingTypeStatus 
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
    val buttonGroup= methodStubButtons.getSelectionButtonsGroup(composite)
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
  
  protected def makeCreatedType(implicit parentCU: ICompilationUnit)

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
      import scala.collection.JavaConversions._
      val javaArrayList = getSuperInterfaces
      val jual = javaArrayList.toArray(new Array[String](javaArrayList.size))
      (getSuperClass +: jual).toList
    }
	
    val monitor = if (progressMonitor == null) new NullProgressMonitor() 
                  else progressMonitor
                  
    monitor.beginTask(NewWizardMessages.NewTypeWizardPage_operationdesc, 8)

    implicit val packageFragment = {
      val rt = getPackageFragmentRoot
      val pf = getPackageFragment
      var  p = pf match {
        case ipf: IPackageFragment => ipf
        case  _                    => rt.getPackageFragment("")
      }
      p.exists match {
        case true => monitor.worked(1)
        case _    => p = rt.createPackageFragment(pf.getElementName, true, 
        		         new SubProgressMonitor(monitor, 1)) 
      }
      p
    }
    
    val packageName = {
      !packageFragment.isDefaultPackage match {
        case true => Some(packageFragment.getElementName)
        case _ => None
      }
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
    
      val buffer = parentCU.getBuffer
      //start control of buffer
      val cb = CodeBuilder(packageName.getOrElse(""), superTypes, buffer)
      cb.append(commentTemplate(comment(getFileComment _)))
      cb.append(packageTemplate(packageName))
      cb.writeImports// to buffer
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

      cu.commitWorkingCopy(true, new SubProgressMonitor(monitor, 1))
      parentCU.discardWorkingCopy
    }
    catch {
      case ex: JavaModelException => Tracer.println("<<<<<<< Error >>>>>>>\n" + ex)
    }
    finally {
      monitor done
    }
  }

  protected def getTypeNameWithoutParameters() = getTypeName.split('[')(0)
  
  override def getCompilationUnitName(typeName: String) = typeName + ".scala"
  
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
	val pack = getPackageFragment()
	if (pack != null) {
	  val cuName = getCompilationUnitName(getTypeNameWithoutParameters())
	  return pack.getCompilationUnit(cuName).getResource()
	}
	null
  }

  override def getCreatedType() = createdType
  
  override protected def typeNameChanged(): IStatus = {
	  
    var status = super.typeNameChanged.asInstanceOf[StatusInfo]
    Tracer.println(">>>> Status = " + status)
    val pack = getPackageFragment
    
    if (pack != null) {
    	
      val project = pack.getJavaProject
    
      try {
        if(!plugin.isScalaProject(project.getProject)) {
          val msg = project.getElementName + " is not a Scala project"
          Tracer.println(msg)
          status.setError(msg)
        }
      } 
      catch {
        case _ : CoreException => status.setError(
    		     "Exception when accessing project natures for " + 
    		     project.getElementName)
      }
      
      if (!isEnclosingTypeSelected && (status.getSeverity < IStatus.ERROR)) {
        try {
          val theType = project.findType(pack.getElementName, getTypeName)
          if (theType != null) {
            status.setError(
        		   NewWizardMessages.NewTypeWizardPage_error_TypeNameExists)
          }
        } 
        catch {
          case _ : JavaModelException => 
        }
      }
    }
    status
  }
}
