/*
 * Copyright 2005-2010 LAMP/EPFL
 * @author Jon Mundorf
 */
// $Id$

package scala.tools.eclipse.launching

import scala.collection.mutable.ArrayBuffer

import org.eclipse.core.resources.IResource
import org.eclipse.core.runtime.IAdaptable
import org.eclipse.debug.core.{ DebugPlugin, ILaunchConfiguration, ILaunchConfigurationType }
import org.eclipse.debug.ui.DebugUITools
import org.eclipse.jdt.core.{ Flags, IJavaElement, IMethod, IType, Signature }
import org.eclipse.jdt.debug.ui.launchConfigurations.JavaLaunchShortcut
import org.eclipse.jdt.internal.debug.ui.launcher.LauncherMessages
import org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants
import org.eclipse.jface.operation.IRunnableContext

import scala.tools.eclipse.javaelements.{ ScalaModuleElement, ScalaSourceFile }

/* This class can be eliminated in favour of JavaApplicationLaunch shortcut as soon as 
 * the JDTs method search works correctly for Scala.
 */

/**
 * This class extends the Java Launch functionality and overrides the sections that
 * require finding the main method as well as finding and creating new launch 
 * configurations.
 */
class ScalaLaunchShortcut extends JavaLaunchShortcut {
  
  /**
   * findTypes is the entry method that is used to find main types within a given class.
   */
  override def findTypes(elements: Array[AnyRef], context: IRunnableContext) : Array[IType] = {
    if (elements == null || elements.isEmpty)
      null
    else {
      
      elements.flatMap(ScalaLaunchShortcut.getMainMethods).toArray
    }
  }
  
  /**
   * Given that the element name for a Scala module appends a $ the java based method can not be re-used.  It is 
   * largely re-implemented here with the appropriate element name made available.
   */
  override def createConfiguration(t: IType) : ILaunchConfiguration = {
    val fullyQualifiedName = {
      val nm = t.getFullyQualifiedName
      if (nm.endsWith("$"))
        nm.substring(0, nm.length-1)
      else
        nm
    }
    val launchInstanceName = t.getElementName

    val configType: ILaunchConfigurationType = getConfigurationType
    val wc = configType.newInstance(null,getLaunchManager.generateUniqueLaunchConfigurationNameFrom(launchInstanceName))
    wc.setAttribute(IJavaLaunchConfigurationConstants.ATTR_MAIN_TYPE_NAME, fullyQualifiedName)
    wc.setAttribute(IJavaLaunchConfigurationConstants.ATTR_PROJECT_NAME, t.getJavaProject.getElementName)   
    wc.setMappedResources(Array[IResource](t.getUnderlyingResource))
    wc.doSave
  }
  
  /**
   * Finds and returns an <b>existing</b> configuration to re-launch for the given type,   
   * or <code>null</code> if there is no existing configuration.
   * 
   * @return a configuration to use for launching the given type or <code>null</code> if none
  */
  override def findLaunchConfiguration(t : IType, configType: ILaunchConfigurationType) : ILaunchConfiguration = {
    //Get working values / collections
    val configs: Array[ILaunchConfiguration] = DebugPlugin.getDefault.getLaunchManager.getLaunchConfigurations(configType)        
    val candidateConfigs = new ArrayBuffer[ILaunchConfiguration]
       
    //Handle Exceptional cases
    if (t == null || configType == null)
      return null 
    
    val fullyQualifiedName = {
      val nm = t.getFullyQualifiedName
      if (nm.endsWith("$"))
        nm.substring(0, nm.length-1)
      else
        nm
    }
    val projectName: String = t.getJavaProject.getElementName
    
    //Match existing configurations to the existing list
    for (launchConfig <- configs) {      
      val lcTypeName: String = launchConfig.getAttribute(IJavaLaunchConfigurationConstants.ATTR_MAIN_TYPE_NAME, "")
      val lcProjectName: String = launchConfig.getAttribute(IJavaLaunchConfigurationConstants.ATTR_PROJECT_NAME, "")
      if (lcTypeName.equals(fullyQualifiedName) && lcProjectName.equals(projectName))
        candidateConfigs += launchConfig
    }
    
    //Return matched configurations or null if none exist                         
    val candidateCount = candidateConfigs.toArray.length
    if (candidateCount == 1)
      candidateConfigs(0)
    else if (candidateCount > 1)
      chooseConfiguration(candidateConfigs.toList)
    else
      null
  }
  
  /**
   * Returns a configuration from the given collection of configurations that should be launched,
   * or <code>null</code> to cancel. Default implementation opens a selection dialog that allows
   * the user to choose one of the specified launch configurations.  Returns the chosen configuration,
   * or <code>null</code> if the user cancels.
   * 
   * @param configList list of configurations to choose from
   * @return configuration to launch or <code>null</code> to cancel
   */
  def chooseConfiguration(configList: List[ILaunchConfiguration]) : ILaunchConfiguration = {
    import org.eclipse.ui.dialogs.ElementListSelectionDialog
    import org.eclipse.jface.window.Window
    import org.eclipse.jdt.internal.debug.ui.launcher.LauncherMessages
    
    val labelProvider = DebugUITools.newDebugModelPresentation
    val dialog = new ElementListSelectionDialog(getShell, labelProvider)
  
    dialog.setElements(configList.toArray[Object])
    dialog.setTitle(getTypeSelectionTitle)
    dialog.setMessage(LauncherMessages.JavaLaunchShortcut_2)
    dialog.setMultipleSelection(false)
    val result = dialog.open
    labelProvider.dispose
    
    if (result == Window.OK)
      dialog.getFirstResult.asInstanceOf[ILaunchConfiguration]
    else
      null
  }        
  
  /**
   * Utility method to shorten the retrieval of the launch manager
   */
  private def getLaunchManager =
    DebugPlugin.getDefault.getLaunchManager
  
  /**
   * Required to have the new launch configuration be placed as a Scala Launch configuration instead of a Java 
   * Launch Configuration
   */
  override def getConfigurationType =
    getLaunchManager.getLaunchConfigurationType("scala.application")        
  
  override def getSelectionEmptyMessage =
    LauncherMessages.JavaApplicationLaunchShortcut_2
  
  override def getEditorEmptyMessage =
    LauncherMessages.JavaApplicationLaunchShortcut_1
  
  override def getTypeSelectionTitle =
    LauncherMessages.JavaApplicationLaunchShortcut_0
}

object ScalaLaunchShortcut {
  def getMainMethods(element: AnyRef) = {
    def isMainMethod(method: IMethod) = {
      val flags = method.getFlags
      val params = method.getParameterTypes    
      method.getElementName == "main" && 
      Flags.isPublic(flags) &&
      method.getReturnType == "V" &&
      params != null &&
      params.length == 1 &&
      (Signature.toString(params(0)) match {
          case "scala.Array" | "java.lang.String[]" | "String[]" => true
          case _ => false
      })
    }
    
    val je = element.asInstanceOf[IAdaptable].getAdapter(classOf[IJavaElement]).asInstanceOf[IJavaElement]
    je.getOpenable match {
      case scu : ScalaSourceFile =>
        def isTopLevel(tpe : IType) = tpe.getDeclaringType == null
        def hasAncestralMainMethod(tpe : IType) = 
          tpe.newSupertypeHierarchy(null).getAllTypes.exists(t => isTopLevel(t) && t.getMethods.exists(isMainMethod)) 
        scu.getAllTypes.filter(tpe => tpe.isInstanceOf[ScalaModuleElement] && isTopLevel(tpe) && hasAncestralMainMethod(tpe)).toList
      case _ => Nil
    }
  }
}
