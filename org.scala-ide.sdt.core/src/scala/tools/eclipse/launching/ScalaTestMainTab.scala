package scala.tools.eclipse.launching

import org.eclipse.jdt.debug.ui.launchConfigurations.JavaMainTab
import org.eclipse.swt.widgets.Composite
import org.eclipse.swt.widgets.Group
import org.eclipse.debug.internal.ui.SWTFactory
import org.eclipse.swt.layout.GridData
import org.eclipse.swt.layout.GridLayout
import org.eclipse.ui.PlatformUI
import org.eclipse.jdt.internal.debug.ui.IJavaDebugHelpContextIds
import org.eclipse.swt.widgets.Text
import org.eclipse.swt.widgets.Button
import org.eclipse.swt.events.ModifyListener
import org.eclipse.swt.events.ModifyEvent
import org.eclipse.jdt.internal.debug.ui.actions.ControlAccessibleListener
import org.eclipse.jdt.internal.debug.ui.launcher.LauncherMessages
import org.eclipse.swt.events.SelectionListener
import org.eclipse.swt.events.SelectionEvent
import org.eclipse.jdt.internal.debug.ui.launcher.SharedJavaMainTab
import org.eclipse.swt.graphics.Image
import org.eclipse.jdt.ui.JavaUI
import org.eclipse.jdt.ui.ISharedImages
import org.eclipse.jdt.core.IJavaProject
import org.eclipse.jdt.core.IJavaElement
import org.eclipse.jdt.core.JavaCore
import org.eclipse.core.resources.ResourcesPlugin
import org.eclipse.jdt.core.JavaModelException
import org.eclipse.jdt.internal.debug.ui.JDIDebugUIPlugin
import org.eclipse.jdt.core.search.IJavaSearchScope
import org.eclipse.jdt.core.search.SearchEngine
import org.eclipse.jdt.internal.debug.ui.launcher.MainMethodSearchEngine
import org.eclipse.jdt.core.IType
import java.lang.reflect.InvocationTargetException
import org.eclipse.jdt.internal.debug.ui.launcher.DebugTypeSelectionDialog
import org.eclipse.jface.window.Window
import org.eclipse.debug.core.ILaunchConfiguration
import org.eclipse.core.resources.IWorkspace
import org.eclipse.core.runtime.IStatus
import org.eclipse.core.resources.IResource
import com.ibm.icu.text.MessageFormat
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy
import org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants
import org.eclipse.core.runtime.CoreException
import scala.tools.eclipse.ScalaPlugin
import org.eclipse.core.runtime.IAdaptable
import scala.tools.eclipse.javaelements.ScalaSourceFile
import org.eclipse.ui.dialogs.FilteredResourcesSelectionDialog
import org.eclipse.ui.dialogs.ResourceListSelectionDialog
import org.eclipse.core.resources.IFile
import org.eclipse.jdt.core.IPackageFragment
import org.eclipse.ui.dialogs.ElementListSelectionDialog
import org.eclipse.jface.viewers.LabelProvider
import org.eclipse.core.resources.IProject
import ScalaTestLaunchConstants._

class ScalaTestMainTab extends SharedJavaMainTab {
  // UI widgets
  /*private var fSearchExternalJarsCheckButton: Button = null
  private var fConsiderInheritedMainButton: Button = null
  private var fStopInMainCheckButton: Button = null*/
  private var mainGroup: Group = null
  private var fSearchButton: Button = null
  private var fSuiteRadioButton: Button = null
  private var fFileRadioButton: Button = null
  private var fPackageRadioButton: Button = null
  private var fIncludeNestedCheckBox: Button = null
  
  def createControl(parent: Composite) {
    val comp = SWTFactory.createComposite(parent, parent.getFont(), 1, 1, GridData.FILL_BOTH)
    comp.getLayout.asInstanceOf[GridLayout].verticalSpacing = 0
    createProjectEditor(comp)
    createVerticalSpacer(comp, 1)
    createMainTypeEditor(comp, "Suite Class")
    setControl(comp)
    PlatformUI.getWorkbench().getHelpSystem().setHelp(getControl(), IJavaDebugHelpContextIds.LAUNCH_CONFIGURATION_DIALOG_MAIN_TAB)
  }
  
  private def updateUI() {
    if (fSuiteRadioButton.getSelection) {
      fIncludeNestedCheckBox.setVisible(false)
      mainGroup.setText("Suite Class")
    }
    else if (fFileRadioButton.getSelection) {
      fIncludeNestedCheckBox.setVisible(false)
      mainGroup.setText("Suite File")
    }
    else {
      fIncludeNestedCheckBox.setVisible(true)
      mainGroup.setText("Package Name")
    }
  }
  
  val typeChangeListener = new SelectionListener() {
    override def widgetSelected(e: SelectionEvent) {
      updateUI
      fMainText.setText("")
    }
    
    override def widgetDefaultSelected(e: SelectionEvent) {
      updateUI
    }
  }
  
  override protected def createMainTypeEditor(parent: Composite, text: String) {
    val typeGroup = SWTFactory.createGroup(parent, "Type", 3, 1, GridData.FILL_HORIZONTAL)
    fSuiteRadioButton = SWTFactory.createRadioButton(typeGroup, "Suite")
    fSuiteRadioButton.addSelectionListener(getDefaultListener)
    fSuiteRadioButton.addSelectionListener(typeChangeListener)
    fFileRadioButton = SWTFactory.createRadioButton(typeGroup, "File")
    fFileRadioButton.addSelectionListener(getDefaultListener)
    fFileRadioButton.addSelectionListener(typeChangeListener)
    fPackageRadioButton = SWTFactory.createRadioButton(typeGroup, "Package")
    fPackageRadioButton.addSelectionListener(getDefaultListener)
    fPackageRadioButton.addSelectionListener(typeChangeListener)
    
    //super.createMainTypeEditor(parent, text)
    mainGroup = SWTFactory.createGroup(parent, text, 2, 1, GridData.FILL_HORIZONTAL); 
    fMainText = SWTFactory.createSingleText(mainGroup, 1);
    fMainText.addModifyListener(new ModifyListener() {
      def modifyText(e: ModifyEvent) {
        updateLaunchConfigurationDialog();
      }
    })
    //ControlAccessibleListener.addListener(fMainText, mainGroup.getText());
    fSearchButton = createPushButton(mainGroup, LauncherMessages.AbstractJavaMainTab_2, null); 
    fSearchButton.addSelectionListener(new SelectionListener() {
      def widgetDefaultSelected(e: SelectionEvent) {}
	  def widgetSelected(e: SelectionEvent) {
        handleSearchButtonSelected();
      }
    })
    createMainTypeExtensions(mainGroup)
  }
  
  override protected def createMainTypeExtensions(parent: Composite) {
    fIncludeNestedCheckBox = SWTFactory.createCheckButton(parent, "Include nested", null, false, 1)
    fIncludeNestedCheckBox.addSelectionListener(getDefaultListener)
    
    /*fSearchExternalJarsCheckButton = SWTFactory.createCheckButton(parent, LauncherMessages.JavaMainTab_E_xt__jars_6, null, false, 2)
    fSearchExternalJarsCheckButton.addSelectionListener(getDefaultListener())

    fConsiderInheritedMainButton = SWTFactory.createCheckButton(parent, LauncherMessages.JavaMainTab_22, null, false, 2)
    fConsiderInheritedMainButton.addSelectionListener(getDefaultListener())
		
    fStopInMainCheckButton = SWTFactory.createCheckButton(parent, LauncherMessages.JavaMainTab_St_op_in_main_1, null, false, 1)
    fStopInMainCheckButton.addSelectionListener(getDefaultListener())*/
  }
  
  override def getImage = JavaUI.getSharedImages().getImage(ISharedImages.IMG_OBJS_CLASS);
  
  def getName = LauncherMessages.JavaMainTab__Main_19
  
  override def getId = "scala.tools.eclipse.launching.scalaTestMainTab"; //$NON-NLS-1$
  
  protected def handleSearchButtonSelected() {
    /*val project = getJavaProject()
    var projects: Array[IJavaProject] = null
    if ((project == null) || !project.exists) {
      val model = JavaCore.create(ResourcesPlugin.getWorkspace.getRoot)
      if (model != null) {
        try {
          projects = model.getJavaProjects.filter(proj => ScalaPlugin.plugin.isScalaProject(proj))
        }
        catch { case e: JavaModelException => JDIDebugUIPlugin.log(e) }
      }
	}
    else {
      projects = Array(project)//new IJavaElement[]{project};
    }
    if (projects == null) {
      projects = Array.empty
    }
    
    var types: Array[IType] = projects.map{ proj => 
      val scProject = ScalaPlugin.plugin.getScalaProject(proj.getProject) 
      scProject.allSourceFiles
    }.flatten.map { file =>
      val scSrcFileOpt = ScalaSourceFile.createFromPath(file.getFullPath.toString)
      scSrcFileOpt match {
          case Some(scSrcFile) =>
            scSrcFile.getAllTypes.toList
          case None =>
            List.empty
      }
    }.flatten.filter(iType => ScalaTestLaunchShortcut.isScalaTestSuite(iType)).toArray
    
    val mmsd = new DebugTypeSelectionDialog(getShell(), types, LauncherMessages.JavaMainTab_Choose_Main_Type_11) 
	if (mmsd.open() == Window.CANCEL) 
	  return
	
    val results = mmsd.getResult();	
    val selectedType = results(0).asInstanceOf[IType];
    if (selectedType != null) {
      fMainText.setText(selectedType.getFullyQualifiedName());
      fProjText.setText(selectedType.getJavaProject().getElementName());
    }*/
    
    val project = getJavaProject()
    var projects: Array[IJavaProject] = null
    if ((project == null) || !project.exists) {
      val model = JavaCore.create(ResourcesPlugin.getWorkspace.getRoot)
      if (model != null) {
        try {
          projects = model.getJavaProjects.filter(proj => ScalaPlugin.plugin.isScalaProject(proj))
        }
        catch { case e: JavaModelException => JDIDebugUIPlugin.log(e) }
      }
	}
    else {
      projects = Array(project)//new IJavaElement[]{project};
    }
    if (projects == null) {
      projects = Array.empty
    }
    
    if (fSuiteRadioButton.getSelection) {
      var types: Array[IType] = projects.map { proj => 
        val scProject = ScalaPlugin.plugin.getScalaProject(proj.getProject) 
        scProject.allSourceFiles
      }.flatten.map { file =>
        val scSrcFileOpt = ScalaSourceFile.createFromPath(file.getFullPath.toString)
        scSrcFileOpt match {
          case Some(scSrcFile) =>
            scSrcFile.getAllTypes.toList
          case None =>
            List.empty
        }
      }.flatten.filter(iType => ScalaTestLaunchShortcut.isScalaTestSuite(iType)).toArray
    
      val mmsd = new DebugTypeSelectionDialog(getShell(), types, LauncherMessages.JavaMainTab_Choose_Main_Type_11) 
	  mmsd.setTitle("ScalaTest Suite Selection")
      if (mmsd.open() == Window.CANCEL) 
	    return
	
      val results = mmsd.getResult	
      val selectedType = results(0).asInstanceOf[IType]
      if (selectedType != null) {
        fMainText.setText(selectedType.getFullyQualifiedName())
        fProjText.setText(selectedType.getJavaProject().getElementName)
      }
    }
    else if (fFileRadioButton.getSelection) {
      val files: Array[IResource] = projects.map { proj => 
        val scProject = ScalaPlugin.plugin.getScalaProject(proj.getProject) 
        scProject.allSourceFiles        
      }.flatten.filter { file => 
        val scSrcFileOpt = ScalaSourceFile.createFromPath(file.getFullPath.toString)
        scSrcFileOpt match {
          case Some(scSrcFile) =>
            val types = scSrcFile.getAllTypes
            types.find(iType => ScalaTestLaunchShortcut.isScalaTestSuite(iType)) match {
              case Some(_) => 
                true
              case None => 
                false
            }
          case None =>
            false
        }
      }.map(file => file.asInstanceOf[IResource])
      
      val fileSelectionDialog = new ResourceListSelectionDialog(getShell, files)
      fileSelectionDialog.setTitle("Scala Source File Selection")
      if (fileSelectionDialog.open() == Window.CANCEL) 
        return
        
      val results = fileSelectionDialog.getResult
      val selectedFile = results(0).asInstanceOf[IFile]
      if (selectedFile != null) {
        fMainText.setText(selectedFile.getFullPath.toPortableString)
        fProjText.setText(selectedFile.getProject.getName)
      }
    }
    else if (fPackageRadioButton.getSelection) {
      case class PackageOption(name: String, project: IJavaProject)
      // Use mutable for better performance
      var packageSet = new scala.collection.mutable.HashSet[PackageOption]()
      projects.map { proj => 
        val scProject = ScalaPlugin.plugin.getScalaProject(proj.getProject) 
        scProject.allSourceFiles        
      }.flatten.foreach { file => 
        val scSrcFileOpt = ScalaSourceFile.createFromPath(file.getFullPath.toString)
        scSrcFileOpt match {
          case Some(scSrcFile) =>
            val types = scSrcFile.getAllTypes
            types.foreach { iType => 
              if (ScalaTestLaunchShortcut.isScalaTestSuite(iType))
                packageSet += PackageOption(iType.getPackageFragment.getElementName, iType.getJavaProject)
            }
          case None =>
        }
      }
      
      val packageSelectionDialog = new ElementListSelectionDialog(getShell, new LabelProvider() { override def getText(element: Any) = element.asInstanceOf[PackageOption].name })
      packageSelectionDialog.setTitle("Package Selection")
      packageSelectionDialog.setMessage("Select a String (* = any string, ? = any char):")
      packageSelectionDialog.setElements(packageSet.toArray)
      if (packageSelectionDialog.open() == Window.CANCEL)
        return
        
      val results = packageSelectionDialog.getResult
      val selectedPackageOption = results(0).asInstanceOf[PackageOption]
      if (selectedPackageOption != null) {
        fMainText.setText(selectedPackageOption.name)
        fProjText.setText(selectedPackageOption.project.getElementName)
      }
    }
  }
  
  override def initializeFrom(config: ILaunchConfiguration) {
    super.initializeFrom(config)
    updateMainTypeFromConfig(config)
    //updateStopInMainFromConfig(config)
    //updateInheritedMainsFromConfig(config)
    //updateExternalJars(config)
  }
  
  override protected def updateMainTypeFromConfig(config: ILaunchConfiguration) {
    super.updateMainTypeFromConfig(config)
    val launchType = config.getAttribute(SCALATEST_LAUNCH_TYPE_NAME, TYPE_SUITE)
    launchType match {
      case TYPE_SUITE => 
        fSuiteRadioButton.setSelection(true)
        fFileRadioButton.setSelection(false)
        fPackageRadioButton.setSelection(false)
      case TYPE_FILE => 
        fSuiteRadioButton.setSelection(false)
        fFileRadioButton.setSelection(true)
        fPackageRadioButton.setSelection(false)
      case TYPE_PACKAGE => 
        fSuiteRadioButton.setSelection(false)
        fFileRadioButton.setSelection(false)
        fPackageRadioButton.setSelection(true)
    }
    val includeNestedStr = config.getAttribute(SCALATEST_LAUNCH_INCLUDE_NESTED_NAME, INCLUDE_NESTED_FALSE)
    if (includeNestedStr == INCLUDE_NESTED_TRUE)
      fIncludeNestedCheckBox.setSelection(true)
    else
      fIncludeNestedCheckBox.setSelection(false)	
    
    updateUI()
  }
  
  override def isValid(config: ILaunchConfiguration): Boolean = {
    setErrorMessage(null)
    setMessage(null)
    var name = fProjText.getText().trim()
    if (name.length > 0) {
      val workspace = ResourcesPlugin.getWorkspace
      val status = workspace.validateName(name, IResource.PROJECT)
      if (status.isOK) {
        val project= ResourcesPlugin.getWorkspace.getRoot.getProject(name)
        if (!project.exists()) {
          setErrorMessage(MessageFormat.format(LauncherMessages.JavaMainTab_20, Array(name)))
          return false
        }
	    if (!project.isOpen) {
          setErrorMessage(MessageFormat.format(LauncherMessages.JavaMainTab_21, Array(name))) 
          return false
        }
      }
      else {
        setErrorMessage(MessageFormat.format(LauncherMessages.JavaMainTab_19, Array(status.getMessage()))) 
        return false
      }
    }
    name = fMainText.getText.trim
    if (name.length == 0) {
      if (fSuiteRadioButton.getSelection)
        setErrorMessage("Suite Class cannot be empty.")
      else if (fFileRadioButton.getSelection)
        setErrorMessage("Suite File cannot be empty.")
     else
        setErrorMessage("Package Name cannot be empty.")
      return false
    }
    return true
  }
  
  def performApply(config: ILaunchConfigurationWorkingCopy) {
    config.setAttribute(IJavaLaunchConfigurationConstants.ATTR_PROJECT_NAME, fProjText.getText.trim)
    config.setAttribute(IJavaLaunchConfigurationConstants.ATTR_MAIN_TYPE_NAME, fMainText.getText.trim)
    val launchType = 
      if (fSuiteRadioButton.getSelection)
        TYPE_SUITE
      else if (fFileRadioButton.getSelection)
        TYPE_FILE
      else
        TYPE_PACKAGE  
    config.setAttribute(SCALATEST_LAUNCH_TYPE_NAME, launchType)
    config.setAttribute(SCALATEST_LAUNCH_INCLUDE_NESTED_NAME, if (fIncludeNestedCheckBox.getSelection) INCLUDE_NESTED_TRUE else INCLUDE_NESTED_FALSE)
    mapResources(config)
  }
  
  def setDefaults(config: ILaunchConfigurationWorkingCopy) {
    val javaElement = getContext
    if (javaElement != null) 
      initializeJavaProject(javaElement, config)
    else 
      config.setAttribute(IJavaLaunchConfigurationConstants.ATTR_PROJECT_NAME, "")
    initializeMainTypeAndName(javaElement, config)
    config.setAttribute(SCALATEST_LAUNCH_TYPE_NAME, TYPE_SUITE)
    config.setAttribute(SCALATEST_LAUNCH_INCLUDE_NESTED_NAME, INCLUDE_NESTED_FALSE)
  }
}