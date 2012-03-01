package scala.tools.eclipse.launching

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

class ScalaTestFileLaunchShortcut extends ILaunchShortcut {
  
  private def launchScalaSourceFile(scSrcFile: ScalaSourceFile, mode: String) {
    val configType = getLaunchManager.getLaunchConfigurationType("scala.scalatest")
    val existingConfigs = getLaunchManager.getLaunchConfigurations(configType)
    val simpleName = scSrcFile.getElementName
    val existingConfigOpt = existingConfigs.find(config => config.getName == simpleName)
    val config = existingConfigOpt match {
                   case Some(existingConfig) => existingConfig
                   case None => 
                     val wc = configType.newInstance(null, getLaunchManager.generateUniqueLaunchConfigurationNameFrom(simpleName))
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
  
  def launch(selection:ISelection, mode:String) {
    // This get called when user right-clicked .scala file on package navigator and choose 'Run As' -> ScalaTest
    // Should just run all suites within the selected file.
    selection match {
      case treeSelection: ITreeSelection => 
        val scSrcFilePaths = treeSelection.getPaths.filter(p => p.getLastSegment.isInstanceOf[ScalaSourceFile])//.find(p => p.isInstanceOf[ScalaSourceFile])
        if (scSrcFilePaths.length > 0)
          launchScalaSourceFile(scSrcFilePaths(0).getLastSegment.asInstanceOf[ScalaSourceFile], mode)
        else
          MessageDialog.openError(null, "Error", "Please select a Scala source file.")
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
  
  private def getLaunchManager = DebugPlugin.getDefault.getLaunchManager
}

class ScalaTestSuiteLaunchShortcut extends ILaunchShortcut {
  
  def launch(selection:ISelection, mode:String) {
    // This get called when user right-clicked .scala file on package navigator and choose 'Run As' -> ScalaTest
    // Should just run all suites within the selected file.
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
          val configType = getLaunchManager.getLaunchConfigurationType("scala.scalatest")
          val existingConfigs = getLaunchManager.getLaunchConfigurations(configType)
          val simpleName = classElement.labelName
          val existingConfigOpt = existingConfigs.find(config => config.getName == simpleName)
          val config = existingConfigOpt match {
                         case Some(existingConfig) => existingConfig
                         case None => 
                           val wc = configType.newInstance(null, getLaunchManager.generateUniqueLaunchConfigurationNameFrom(simpleName))
                           val project = editorPart.getEditorInput.asInstanceOf[IFileEditorInput].getFile.getProject
                           val scProject = ScalaPlugin.plugin.getScalaProject(project)
                           wc.setAttribute(IJavaLaunchConfigurationConstants.ATTR_MAIN_TYPE_NAME, classElement.getFullyQualifiedName)
                           wc.setAttribute(IJavaLaunchConfigurationConstants.ATTR_PROJECT_NAME, project.getName)
                           wc.setAttribute(SCALATEST_LAUNCH_TYPE_NAME, TYPE_SUITE)
                           wc.setAttribute(SCALATEST_LAUNCH_INCLUDE_NESTED_NAME, INCLUDE_NESTED_FALSE)
                           wc.doSave
                         }
          DebugUITools.launch(config, mode)
        case None => 
          MessageDialog.openError(null, "Error", "Please select a ScalaTest suite to launch.")
      }
    }
    else
      MessageDialog.openError(null, "Error", "Please select a ScalaTest suite to launch.")
  }
  
  private def getLaunchManager = DebugPlugin.getDefault.getLaunchManager
}

class ScalaTestTestLaunchShortcut extends ILaunchShortcut {
  
  def launch(selection:ISelection, mode:String) {
    // This get called when user right-clicked .scala file on package navigator and choose 'Run As' -> ScalaTest
    // Should just run all suites within the selected file.
  }
  
  def launch(editorPart:IEditorPart, mode:String) {
    val selectionOpt = ScalaTestLaunchShortcut.resolveSelectedAst(editorPart, JavaUI.getEditorInputTypeRoot(editorPart.getEditorInput()))
    selectionOpt match {
      case Some(selection) => 
        println("***Test Found, display name: " + selection.displayName() + ", test name(s):")
        selection.testNames.foreach(println(_))
        val message = "Display Name: " + selection.displayName + "\n" + 
                      "Tests: \n" + selection.testNames.mkString("\n")
        MessageDialog.openInformation(null, "Test Found", message)
      case None =>
        println("#####Unable to determine selected test, nothing to launch.")
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
    val typeHier:ITypeHierarchy = iType.newSupertypeHierarchy(null)
    val superTypeArr:Array[IType] = typeHier.getAllSupertypes(iType)
    superTypeArr.findIndexOf {superType => superType.getFullyQualifiedName == "org.scalatest.Suite"} >= 0
  }
  
  def containsScalaTestSuite(element: AnyRef): Boolean = {
    val je = element.asInstanceOf[IAdaptable].getAdapter(classOf[IJavaElement]).asInstanceOf[IJavaElement]
    je.getOpenable match {
      case scu: ScalaSourceFile => 
        val suiteOpt = scu.getAllTypes().find { tpe => tpe.isInstanceOf[ScalaClassElement] && isScalaTestSuite(tpe) }
        suiteOpt match {
          case Some(suite) => true
          case None => false
        }
      case _ =>
        false
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
  
  def resolveSelectedAst(editorPart: IEditorPart, typeRoot: ITypeRoot): Option[Selection] = {
    val selectionProvider:ISelectionProvider = editorPart.getSite().getSelectionProvider()
    if(selectionProvider == null)
      None
    val selection:ISelection = selectionProvider.getSelection()
    
    if(!selection.isInstanceOf[ITextSelection])
      None
    else {
      val textSelection:ITextSelection = selection.asInstanceOf[ITextSelection]
      val element = SelectionConverter.getElementAtOffset(typeRoot, selection.asInstanceOf[ITextSelection])
      val project = editorPart.getEditorInput.asInstanceOf[IFileEditorInput].getFile.getProject
      val scProject = ScalaPlugin.plugin.getScalaProject(project)
      val loaderUrls = scProject.classpath.map{ cp =>
        val cpFile = new File(cp.toString)
        if (cpFile.exists && cpFile.isDirectory && !cp.toString.endsWith(File.separator))
          new URL("file://" + cp + "/")
        else
          new URL("file://" + cp)
      }
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
}