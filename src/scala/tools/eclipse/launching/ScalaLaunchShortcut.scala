/*
 * Copyright 2005-2010 LAMP/EPFL
 * @author Jon Mundorf
 */
// $Id$

package scala.tools.eclipse.launching

import scala.collection.mutable.ArrayBuffer

import org.eclipse.core.resources.IResource
import org.eclipse.core.runtime.{ CoreException, IAdaptable }
import org.eclipse.debug.core.{ DebugPlugin, ILaunchConfiguration, ILaunchConfigurationType }
import org.eclipse.debug.ui.DebugUITools
import org.eclipse.jdt.core.{ IJavaElement, IMethod, IType }
import org.eclipse.jdt.debug.ui.launchConfigurations.JavaLaunchShortcut
import org.eclipse.jdt.internal.debug.ui.launcher.LauncherMessages
import org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants
import org.eclipse.jface.operation.IRunnableContext

import scala.tools.eclipse.javaelements.ScalaSourceFile

/* This class can be eliminiated in favour of JavaApplicationLaunch shortcut as soon as 
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
  @throws(classOf[InterruptedException])
  @throws(classOf[CoreException])
  override def findTypes(elements: Array[Object], context: IRunnableContext) : Array[IType] = {
    //Handle Exception case
    if (elements == null || elements.isEmpty){
      return null
    }     
    //Initialize return value
    var a = new ArrayBuffer[IType]    
    //Execute search for mains and return
    elements.foreach(elem => a ++= getMainMethods(elem))    
    a.toArray
  }

  
 /**
  * Given a single resource (file) captures all classes/modules that contain a main method and return
  * an array of all the declaring types that contain a main method.
  */  
 def getMainMethods(o: Object) : Array[IType] = {
   import org.eclipse.jdt.internal.core.SourceType
   //Initialize an empty return value
   var a = new ArrayBuffer[IType] 
   
   //Get a hold of scala compilation unit for the file and then get all available types
   val adapt: IAdaptable = o.asInstanceOf[IAdaptable]
   var je = adapt.getAdapter(classOf[IJavaElement]).asInstanceOf[IJavaElement]
   while ((je ne null) && !je.isInstanceOf[ScalaSourceFile])
     je = je.getParent
   if (je.isInstanceOf[ScalaSourceFile]) {
     val scu = je.asInstanceOf[ScalaSourceFile]
     val allTypesFromCU: Array[IType] = scu.getAllTypes   
  
     //Loop over multiple class/modules in a file
     for (cuType <- allTypesFromCU) {
       
       //if a class or module get all types to include superclass
       if (cuType.getDeclaringType == null) {       
         val allTypes = cuType.newSupertypeHierarchy(null).getAllTypes      
         
         //loop through main class and all super classes for the class/module in question to find main class
          for (t <- allTypes){
            //debugging purposes only
            //methodDebugInf(meth)                    
            if (t.getDeclaringType ==null){
              for (meth <- t.getMethods){                  
                if (isMainMethod(meth))           
                  a += cuType
              }
            }      
          }
       }
     }
   }
   a.toArray    
  }
 
 
  /**
   * This is where the main method is searched for.  Note that searching the super class is not yet supported.  Unable to get a handle
   * on the super class with the current type / jdt implementation.  This will need to be updated as the Eclipse Scala typing matures.
   */
  private def isMainMethod(method: IMethod) : Boolean = {
    import org.eclipse.jdt.core.Flags
    import org.eclipse.jdt.core.Signature
    
    //Begin Check for main type in the existing class
    val flags = method.getFlags
    val parameterArray = method.getParameterTypes    
    val isMain: Boolean = method.getElementName.equals("main") && method.getReturnType.equals("V") 
    val isPublicStatic: Boolean = Flags.isPublic(flags)
        
    //Check the parameterType
    if (isMain && isPublicStatic && (parameterArray != null) && (!parameterArray.isEmpty)){
      val typeSignature: String = Signature.toString(parameterArray(0))
      val simpleSignature = Signature.getSimpleName(typeSignature.toArray)
      val paramTypeMatches: Boolean = (parameterArray.length == 1) &&  
                          ("scala.Array".equals(typeSignature) ||
                           "java.lang.String[]".equals(typeSignature) ||
                           "String[]".equals(typeSignature))
                          
      return paramTypeMatches
    }
 
    //At this point there is no main in the super class or current class return false
    false  
  }
 
  /**
   * Private method - should be replaced by a logging solution
   */
  private def methodDebugInf(meth: IMethod) {
    println("Method Name -> " + meth.getElementName)
    println("Method Param Raw Names -> " + meth.getRawParameterNames.toString)
    println("Method Param Types -> " + meth.getParameterTypes)
    println("Method Param Names -> " + meth.getParameterNames)
    println("Method Return Type -> " + meth.getReturnType) 
    println("Parent Name -> " + meth.getParent.getElementName)
    println("DeclaringType Name -> " + meth.getDeclaringType)                    
  }
  
  /**
   * Given that the element name for a Scala module appends a $ the java based method can not be re-used.  It is 
   * largely re-implemented here with the appropriate element name made available.
   */
  override def createConfiguration(t: IType) : ILaunchConfiguration = {
    var config: ILaunchConfiguration = null 
    // Adjusted the name of class to be run to not include the $ sign.  When the JDT Indexing is fixed this will just
    // need to use the regular fullyQualifiedName
    val adjustedFullyQualifiedName: String = t.getFullyQualifiedName.slice(0,t.getFullyQualifiedName.length-1).toString
    // This is the name that appears on the launch configuration subwindow - since this is for
    // display purposes only this should not be changed
    val launchInstanceName: String = t.getElementName.slice(0,t.getElementName.length -1).toString

    val configType: ILaunchConfigurationType = getConfigurationType
    var wc  = configType.newInstance(null,getLaunchManager.generateUniqueLaunchConfigurationNameFrom(launchInstanceName))
    wc.setAttribute(IJavaLaunchConfigurationConstants.ATTR_MAIN_TYPE_NAME, adjustedFullyQualifiedName)
    wc.setAttribute(IJavaLaunchConfigurationConstants.ATTR_PROJECT_NAME, t.getJavaProject.getElementName)   
    wc.setMappedResources(Array[IResource](t.getUnderlyingResource))
    config = wc.doSave
    config    
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
    var candidateConfigs = new ArrayBuffer[ILaunchConfiguration]
       
    //Handle Exceptional cases
    if (t == null || configType == null)
      return null 
    
   //Update the launch configuration names to remove the $ sign
    val fullyQualifiedName: String = t.getFullyQualifiedName.slice(0,t.getFullyQualifiedName.length-1).toString
    val projectName: String = t.getJavaProject.getElementName
    
    //Match existing configurations to the existing list
    for (launchConfig <- configs) {      
      val lcTypeName: String = launchConfig.getAttribute(IJavaLaunchConfigurationConstants.ATTR_MAIN_TYPE_NAME, "")
      val lcProjectName: String = launchConfig.getAttribute(IJavaLaunchConfigurationConstants.ATTR_PROJECT_NAME, "")
      
      //  println("Incoming Config  -> " + fullyQualifiedName)
      //  println("Looping Config   -> " + lcTypeName)
      //  println("Incoming Project -> " + projectName)
      //  println("Looping Project  -> " + lcProjectName)
                
      if (lcTypeName.equals(fullyQualifiedName) && lcProjectName.equals(projectName)){
        // println("Matched Configuration")  
        candidateConfigs += launchConfig
      }          
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
    var dialog = new ElementListSelectionDialog(getShell, labelProvider)
  
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
