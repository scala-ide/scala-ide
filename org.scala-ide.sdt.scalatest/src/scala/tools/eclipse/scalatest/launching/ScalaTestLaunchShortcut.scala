/*
 * SCALA LICENSE
 *
 * Copyright (C) 2011-2012 Artima, Inc. All rights reserved.
 *
 * This software was developed by Artima, Inc.
 *
 * Permission to use, copy, modify, and distribute this software in source
 * or binary form for any purpose with or without fee is hereby granted,
 * provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the EPFL nor the names of its contributors
 *    may be used to endorse or promote products derived from this
 *    software without specific prior written permission.
 *
 *
 * THIS SOFTWARE IS PROVIDED BY THE REGENTS AND CONTRIBUTORS ``AS IS'' AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE REGENTS OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
 * OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 */

package scala.tools.eclipse.scalatest.launching

import org.eclipse.debug.ui.ILaunchShortcut
import org.eclipse.jface.viewers.ISelection
import org.eclipse.ui.IEditorPart
import org.eclipse.jdt.core.IType
import org.eclipse.jdt.core.ITypeHierarchy
import org.eclipse.core.runtime.IAdaptable
import scala.tools.eclipse.javaelements.ScalaSourceFile
import org.eclipse.jdt.core.IJavaElement
import scala.tools.eclipse.javaelements.ScalaClassElement
import org.eclipse.jface.viewers.ISelectionProvider
import org.eclipse.jdt.core.ITypeRoot
import org.eclipse.jface.text.ITextSelection
import org.eclipse.jdt.internal.ui.actions.SelectionConverter
import org.eclipse.jdt.ui.JavaUI
import org.eclipse.ui.IFileEditorInput
import scala.tools.eclipse.ScalaPlugin
import scala.tools.eclipse.javaelements.ScalaElement
import scala.reflect.generic.Trees
import scala.tools.eclipse.javaelements.ScalaCompilationUnit
import scala.tools.nsc.util.OffsetPosition
import scala.tools.eclipse.javaelements.ScalaClassElement
import scala.annotation.tailrec
import scala.tools.nsc.util.Position
import scala.tools.nsc.util.Position$
import org.scalatest.finders.AstNode
import org.scalatest.finders.Selection
import java.net.URLClassLoader
import java.net.URL
import java.io.File
import org.eclipse.debug.core.DebugPlugin
import org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants
import org.eclipse.debug.ui.DebugUITools
import org.eclipse.jface.dialogs.MessageDialog
import ScalaTestLaunchConstants._
import org.eclipse.jface.viewers.ITreeSelection
import org.eclipse.core.resources.IProject
import org.eclipse.jdt.internal.core.PackageFragment
import ScalaTestLaunchShortcut._
import org.eclipse.ui.IEditorSite
import org.eclipse.ui.IEditorInput

class ScalaTestFileLaunchShortcut extends ILaunchShortcut {
  
  def launch(selection:ISelection, mode:String) {
    selection match {
      case treeSelection: ITreeSelection => 
        treeSelection.getFirstElement match {
          case scSrcFile: ScalaSourceFile => 
            launchScalaSourceFile(scSrcFile, mode)
          case _ => 
            MessageDialog.openError(null, "Error", "Please select a Scala source file.")
        }
      case _ => 
        MessageDialog.openError(null, "Error", "Please select a Scala source file.")
    }
  }
  
  def launch(editorPart:IEditorPart, mode:String) {
    val typeRoot = JavaUI.getEditorInputTypeRoot(editorPart.getEditorInput)
    typeRoot match {
      case scSrcFile: ScalaSourceFile => 
        launchScalaSourceFile(scSrcFile, mode)
      case _ => 
        MessageDialog.openError(null, "Error", "Please select a Scala source file.")
    }
  }
}

class ScalaTestSuiteLaunchShortcut extends ILaunchShortcut {
  
  def launch(selection:ISelection, mode:String) {
    println(selection)
    selection match {
      case treeSelection: ITreeSelection => 
        treeSelection.getFirstElement match {
          case classElement: ScalaClassElement => 
            launchSuite(classElement, mode)
          case _ => 
            MessageDialog.openError(null, "Error", "Please select a ScalaTest suite to launch.")
        }
      case _ => 
        MessageDialog.openError(null, "Error", "Please select a ScalaTest suite to launch.")
    }
  }
  
  def launch(editorPart:IEditorPart, mode:String) {
    // This get called when user right-clicked within the opened file editor and choose 'Run As' -> ScalaTest
    val typeRoot = JavaUI.getEditorInputTypeRoot(editorPart.getEditorInput())
    val selectionProvider:ISelectionProvider = editorPart.getSite().getSelectionProvider()
    if (selectionProvider != null) {
      val selection:ISelection = selectionProvider.getSelection()
      val textSelection:ITextSelection = selection.asInstanceOf[ITextSelection]
      val element = SelectionConverter.getElementAtOffset(typeRoot, selection.asInstanceOf[ITextSelection])
      val classElementOpt = ScalaTestLaunchShortcut.getScalaTestSuite(element)
      classElementOpt match {
        case Some(classElement) => 
          launchSuite(classElement, mode)
        case None => 
          MessageDialog.openError(null, "Error", "Please select a ScalaTest suite to launch.")
      }
    }
    else
      MessageDialog.openError(null, "Error", "Please select a ScalaTest suite to launch.")
  }
}

class ScalaTestPackageLaunchShortcut extends ILaunchShortcut {
  
  def launch(selection:ISelection, mode:String) {
    selection match {
      case treeSelection: ITreeSelection => 
        treeSelection.getFirstElement match {
          case packageFragment: PackageFragment => 
            launchPackage(packageFragment, mode)
          case _ => 
            MessageDialog.openError(null, "Error", "Please select a package.")
        }
      case _ => 
        MessageDialog.openError(null, "Error", "Please select a package.")
    }
  }
  
  def launch(editorPart:IEditorPart, mode:String) {
    
  }
}

class ScalaTestTestLaunchShortcut extends ILaunchShortcut {
  
  def launch(selection:ISelection, mode:String) {
    // This get called when user right-clicked .scala file on package navigator and choose 'Run As' -> ScalaTest
    // Not applicable for launch selected test in source now, unless more details of class is shown in package navigator.
  }
  
  def launch(editorPart:IEditorPart, mode:String) {
    val typeRoot = JavaUI.getEditorInputTypeRoot(editorPart.getEditorInput())
    val selectionOpt = ScalaTestLaunchShortcut.resolveSelectedAst(editorPart.getEditorInput, editorPart.getEditorSite.getSelectionProvider)
    selectionOpt match {
      case Some(selection) => 
        launchTests(typeRoot.getJavaProject.getProject, selection.className, selection.displayName, selection.testNames, mode)
      case None =>
        MessageDialog.openError(null, "Error", "Sorry, unable to determine selected test.")
    }
  }
}

object ScalaTestLaunchShortcut {
  def getScalaTestSuites(element: AnyRef): List[IType] = {
    val je = element.asInstanceOf[IAdaptable].getAdapter(classOf[IJavaElement]).asInstanceOf[IJavaElement]
    je.getOpenable match {
      case scu: ScalaSourceFile => 
        val ts = scu.getAllTypes()
        ts.filter {tpe => 
          tpe.isInstanceOf[ScalaClassElement] && isScalaTestSuite(tpe)
        }.toList
      case _ =>
        List.empty
    }
  }
  
  def isScalaTestSuite(iType: IType): Boolean = {
    //val typeHier:ITypeHierarchy = iType.newSupertypeHierarchy(null)
    //val superTypeArr:Array[IType] = typeHier.getAllSupertypes(iType)
    //superTypeArr.findIndexOf {superType => superType.getFullyQualifiedName == "org.scalatest.Suite"} >= 0
    iType.getSuperInterfaceNames().contains("org.scalatest.Suite") || 
    iType.getAnnotations.exists(annt => annt.getElementName == "WrapWith") // org.scalatest.WrapWith does not work
  }
  
  def containsScalaTestSuite(scSrcFile: ScalaSourceFile): Boolean = {
    val suiteOpt = scSrcFile.getAllTypes().find { tpe => tpe.isInstanceOf[ScalaClassElement] && isScalaTestSuite(tpe) }
    suiteOpt match {
      case Some(suite) => true
      case None => false
    }
  }
  
  def getScalaTestSuite(element: IJavaElement): Option[ScalaClassElement] = {
    element match {
      case scElement: ScalaElement => 
        val classElement = ScalaTestLaunchShortcut.getClassElement(element)
        if (classElement != null && ScalaTestLaunchShortcut.isScalaTestSuite(classElement)) 
          Some(classElement)
        else
          None
      case _ =>
        None
    }
  }
  
  def resolveSelectedAst(editorInput: IEditorInput, selectionProvider: ISelectionProvider): Option[Selection] = {
    val typeRoot: ITypeRoot = JavaUI.getEditorInputTypeRoot(editorInput)
    if(selectionProvider == null)
      None
    val selection:ISelection = selectionProvider.getSelection()
    
    if(!selection.isInstanceOf[ITextSelection])
      None
    else {
      val textSelection:ITextSelection = selection.asInstanceOf[ITextSelection]
      val element = SelectionConverter.getElementAtOffset(typeRoot, selection.asInstanceOf[ITextSelection])
      val project = typeRoot.getJavaProject.getProject
      val scProject = ScalaPlugin.plugin.getScalaProject(project)
      val loaderUrls = scProject.classpath.map { cp => new File(cp.toString).toURI.toURL }
      val loader:ClassLoader = new URLClassLoader(loaderUrls.toArray, getClass.getClassLoader)
      
      scProject.withPresentationCompiler { compiler =>
        val scalatestFinder = new ScalaTestFinder(compiler, loader)
        try {
          scalatestFinder.find(textSelection, element)
        }
        catch {
          // This could due to custom classes not compiled.
          case e: Exception => 
            e.printStackTrace()
          None
        }
      } (null)
    }
  }
  
  @tailrec
  def getClassElement(element: IJavaElement): ScalaClassElement = {
    element match {
      case scClassElement: ScalaClassElement => 
        scClassElement
      case _ =>
        if (element.getParent != null)
          getClassElement(element.getParent)
        else
          null
    }
  }
  
  def getLaunchManager = DebugPlugin.getDefault.getLaunchManager
  
  def launchScalaSourceFile(scSrcFile: ScalaSourceFile, mode: String) {
    val configType = getLaunchManager.getLaunchConfigurationType("scala.scalatest")
    val existingConfigs = getLaunchManager.getLaunchConfigurations(configType)
    val simpleName = scSrcFile.getElementName
    val existingConfigOpt = existingConfigs.find(config => config.getName == simpleName)
    val config = existingConfigOpt match {
                   case Some(existingConfig) => existingConfig
                   case None => 
                     val wc = configType.newInstance(null, getLaunchManager.generateUniqueLaunchConfigurationNameFrom(simpleName.replaceAll(":", "-").replaceAll("\"", "'")))
                     val project = scSrcFile.getJavaProject.getProject
                     val scProject = ScalaPlugin.plugin.getScalaProject(project)
                     wc.setAttribute(IJavaLaunchConfigurationConstants.ATTR_MAIN_TYPE_NAME, scSrcFile.getPath.toPortableString)
                     wc.setAttribute(IJavaLaunchConfigurationConstants.ATTR_PROJECT_NAME, project.getName)
                     wc.setAttribute(SCALATEST_LAUNCH_TYPE_NAME, TYPE_FILE)
                     wc.setAttribute(SCALATEST_LAUNCH_INCLUDE_NESTED_NAME, INCLUDE_NESTED_FALSE)
                     wc.doSave
                   }
    DebugUITools.launch(config, mode)
  }
  
  def launchPackage(packageFragment: PackageFragment, mode: String) {
    val configType = getLaunchManager.getLaunchConfigurationType("scala.scalatest")
    val existingConfigs = getLaunchManager.getLaunchConfigurations(configType)
    val simpleName = packageFragment.getElementName
    val existingConfigOpt = existingConfigs.find(config => config.getName == simpleName)
    val config = existingConfigOpt match {
                   case Some(existingConfig) => existingConfig
                   case None => 
                     val wc = configType.newInstance(null, getLaunchManager.generateUniqueLaunchConfigurationNameFrom(simpleName.replaceAll(":", "-").replaceAll("\"", "'")))
                     val project = packageFragment.getJavaProject.getProject
                     val scProject = ScalaPlugin.plugin.getScalaProject(project)
                     wc.setAttribute(IJavaLaunchConfigurationConstants.ATTR_MAIN_TYPE_NAME, simpleName)
                     wc.setAttribute(IJavaLaunchConfigurationConstants.ATTR_PROJECT_NAME, project.getName)
                     wc.setAttribute(SCALATEST_LAUNCH_TYPE_NAME, TYPE_PACKAGE)
                     wc.setAttribute(SCALATEST_LAUNCH_INCLUDE_NESTED_NAME, INCLUDE_NESTED_FALSE)
                     wc.doSave
                   }
    DebugUITools.launch(config, mode)
  }
  
  def launchSuite(classElement: ScalaClassElement, mode: String) {
    val configType = getLaunchManager.getLaunchConfigurationType("scala.scalatest")
    val existingConfigs = getLaunchManager.getLaunchConfigurations(configType)
    val simpleName = classElement.labelName
    val existingConfigOpt = existingConfigs.find(config => config.getName == simpleName)
    val config = existingConfigOpt match {
                   case Some(existingConfig) => existingConfig
                   case None => 
                     val wc = configType.newInstance(null, getLaunchManager.generateUniqueLaunchConfigurationNameFrom(simpleName.replaceAll(":", "-").replaceAll("\"", "'")))
                     val project = classElement.getJavaProject.getProject
                     val scProject = ScalaPlugin.plugin.getScalaProject(project)
                     wc.setAttribute(IJavaLaunchConfigurationConstants.ATTR_MAIN_TYPE_NAME, classElement.getFullyQualifiedName)
                     wc.setAttribute(IJavaLaunchConfigurationConstants.ATTR_PROJECT_NAME, project.getName)
                     wc.setAttribute(SCALATEST_LAUNCH_TYPE_NAME, TYPE_SUITE)
                     wc.setAttribute(SCALATEST_LAUNCH_INCLUDE_NESTED_NAME, INCLUDE_NESTED_FALSE)
                     wc.setAttribute(SCALATEST_LAUNCH_TESTS_NAME, new java.util.HashSet[String]())
                     wc.doSave
                 }
    DebugUITools.launch(config, mode)
  }
  
  def launchTests(project: IProject, className: String, displayName: String, testNames: Array[String], mode: String) {
    val configType = getLaunchManager.getLaunchConfigurationType("scala.scalatest")
    val existingConfigs = getLaunchManager.getLaunchConfigurations(configType)
    val simpleName = displayName
    val existingConfigOpt = existingConfigs.find(config => config.getName == simpleName)
    val config = existingConfigOpt match {
                   case Some(existingConfig) => existingConfig
                   case None => 
                     val wc = configType.newInstance(null, getLaunchManager.generateUniqueLaunchConfigurationNameFrom(simpleName.replaceAll(":", "-").replaceAll("\"", "'").replaceAll(">", "-").replaceAll("<", "-")))
                     val scProject = ScalaPlugin.plugin.getScalaProject(project)
                     wc.setAttribute(IJavaLaunchConfigurationConstants.ATTR_MAIN_TYPE_NAME, className)
                     wc.setAttribute(IJavaLaunchConfigurationConstants.ATTR_PROJECT_NAME, project.getName)
                     wc.setAttribute(SCALATEST_LAUNCH_TYPE_NAME, TYPE_SUITE)
                     wc.setAttribute(SCALATEST_LAUNCH_INCLUDE_NESTED_NAME, INCLUDE_NESTED_FALSE)
                     val testNameSet = new java.util.HashSet[String]()
                     testNames.foreach(tn => testNameSet.add(tn))
                     wc.setAttribute(SCALATEST_LAUNCH_TESTS_NAME, testNameSet)
                     wc.doSave
                 }
    DebugUITools.launch(config, mode)
  }
}