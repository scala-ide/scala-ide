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

class ScalaTestLaunchShortcut extends ILaunchShortcut {
  
  def launch(selection:ISelection, mode:String) {
    // This get called when user right-clicked .scala file on package navigator and choose 'Run As' -> ScalaTest
    // Should just run all suites within the selected file.
  }
  
  def launch(editorPart:IEditorPart, mode:String) {
    // This get called when user right-clicked within the opened file editor and choose 'Run As' -> ScalaTest
    val selectionOpt = resolveSelectedAst(editorPart, JavaUI.getEditorInputTypeRoot(editorPart.getEditorInput()))
    selectionOpt match {
      case Some(selection) => 
        println("***Test Found, display name: " + selection.displayName() + ", test name(s):")
        selection.testNames.foreach(println(_))
      case None =>
        println("#####Launch all suites within the source file")
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
}

object ScalaTestLaunchShortcut {
  def getScalaTestSuites(element: AnyRef):List[IType] = {
    val je = element.asInstanceOf[IAdaptable].getAdapter(classOf[IJavaElement]).asInstanceOf[IJavaElement]
    je.getOpenable match {
      case scu: ScalaSourceFile => 
        val ts = scu.getAllTypes()
        ts.filter {tpe => 
          tpe.isInstanceOf[ScalaClassElement] && isScalaTestSuite(tpe)
        }.toList
    }
  }
  
  def isScalaTestSuite(iType: IType): Boolean = {
    val typeHier:ITypeHierarchy = iType.newSupertypeHierarchy(null)
    val superTypeArr:Array[IType] = typeHier.getAllSupertypes(iType)
    superTypeArr.findIndexOf {superType => superType.getFullyQualifiedName == "org.scalatest.Suite"} >= 0
  }
}