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

class ScalaTestMainTab extends SharedJavaMainTab {
  // UI widgets
  /*private var fSearchExternalJarsCheckButton: Button = null
  private var fConsiderInheritedMainButton: Button = null
  private var fStopInMainCheckButton: Button = null*/
  
  def createControl(parent: Composite) {
    val comp = SWTFactory.createComposite(parent, parent.getFont(), 1, 1, GridData.FILL_BOTH)
    comp.getLayout.asInstanceOf[GridLayout].verticalSpacing = 0
    createProjectEditor(comp)
    createVerticalSpacer(comp, 1)
    createMainTypeEditor(comp, "Test Class")
    setControl(comp)
    PlatformUI.getWorkbench().getHelpSystem().setHelp(getControl(), IJavaDebugHelpContextIds.LAUNCH_CONFIGURATION_DIALOG_MAIN_TAB)
  }
  
  override protected def createMainTypeExtensions(parent: Composite) {
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
    /*var constraints = IJavaSearchScope.SOURCES
    constraints |= IJavaSearchScope.APPLICATION_LIBRARIES
    if (fSearchExternalJarsCheckButton.getSelection()) {
      constraints |= IJavaSearchScope.SYSTEM_LIBRARIES
    }*/
    
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
    }
  }
  
  override def initializeFrom(config: ILaunchConfiguration) {
    super.initializeFrom(config)
    updateMainTypeFromConfig(config)
    //updateStopInMainFromConfig(config)
    //updateInheritedMainsFromConfig(config)
    //updateExternalJars(config)
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
      setErrorMessage("Test Class cannot be empty.") 
      return false
    }
    return true
  }
  
  def performApply(config: ILaunchConfigurationWorkingCopy) {
    config.setAttribute(IJavaLaunchConfigurationConstants.ATTR_PROJECT_NAME, fProjText.getText.trim)
    config.setAttribute(IJavaLaunchConfigurationConstants.ATTR_MAIN_TYPE_NAME, fMainText.getText.trim)
    mapResources(config)
    
    /*val nullString: String = null
	
    
    // attribute added in 2.1, so null must be used instead of false for backwards compatibility
    if (fStopInMainCheckButton.getSelection) {
      config.setAttribute(IJavaLaunchConfigurationConstants.ATTR_STOP_IN_MAIN, true)
    }
    else {
      config.setAttribute(IJavaLaunchConfigurationConstants.ATTR_STOP_IN_MAIN, nullString)
    }
		
    // attribute added in 2.1, so null must be used instead of false for backwards compatibility
    if (fSearchExternalJarsCheckButton.getSelection()) {
      config.setAttribute(JavaMainTab.ATTR_INCLUDE_EXTERNAL_JARS, true)
    }
    else {
      config.setAttribute(JavaMainTab.ATTR_INCLUDE_EXTERNAL_JARS, nullString)
    }
		
    // attribute added in 3.0, so null must be used instead of false for backwards compatibility
    if (fConsiderInheritedMainButton.getSelection()) {
      config.setAttribute(JavaMainTab.ATTR_CONSIDER_INHERITED_MAIN, true)
    }
    else {
      config.setAttribute(JavaMainTab.ATTR_CONSIDER_INHERITED_MAIN, nullString)
    }*/
  }
  
  def setDefaults(config: ILaunchConfigurationWorkingCopy) {
    val javaElement = getContext
    if (javaElement != null) 
      initializeJavaProject(javaElement, config)
    else 
      config.setAttribute(IJavaLaunchConfigurationConstants.ATTR_PROJECT_NAME, "")
    initializeMainTypeAndName(javaElement, config)
  }
  
  /*private def updateExternalJars(config: ILaunchConfiguration) {
    var search = false
    try {
      search = config.getAttribute(JavaMainTab.ATTR_INCLUDE_EXTERNAL_JARS, false);
    }
    catch {
      case e: CoreException => 
        JDIDebugUIPlugin.log(e)
    }
    fSearchExternalJarsCheckButton.setSelection(search)     
  }
  
  private def updateInheritedMainsFromConfig(config: ILaunchConfiguration) {
    var inherit = false;
    try {
      inherit = config.getAttribute(JavaMainTab.ATTR_CONSIDER_INHERITED_MAIN, false);
    }
	catch {
      case e: CoreException => 
        JDIDebugUIPlugin.log(e)
    }
    fConsiderInheritedMainButton.setSelection(inherit);
  }
  
  private def updateStopInMainFromConfig(config: ILaunchConfiguration) {
    var stop = false;
    try {
      stop = config.getAttribute(IJavaLaunchConfigurationConstants.ATTR_STOP_IN_MAIN, false);
    }
    catch {
      case e: CoreException => 
        JDIDebugUIPlugin.log(e)
    }
    fStopInMainCheckButton.setSelection(stop);
  }*/
}