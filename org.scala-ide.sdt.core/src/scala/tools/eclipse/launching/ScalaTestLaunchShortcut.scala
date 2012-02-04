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
//import org.scalatest.tools.Runner

class ScalaTestLaunchShortcut extends ILaunchShortcut {
  
  type Tree = Trees#Tree
  
  def launch(selection:ISelection, mode:String) {
    // This get called when user right-clicked .scala file on package navigator and choose 'Run As' -> ScalaTest
    // Should just run all suites within the selected file.
  }
  
  def launch(editorPart:IEditorPart, mode:String) {
    // This get called when user right-clicked within the opened file editor and choose 'Run As' -> ScalaTest
    //val element: ScalaElement = resolveSelectedElement(editorPart, JavaUI.getEditorInputTypeRoot(editorPart.getEditorInput()))
    val element = resolveSelectedAst(editorPart, JavaUI.getEditorInputTypeRoot(editorPart.getEditorInput()))
    if(element == null)
      println("#####Launch all suites within the source file")
    else {
      //val elementParent = element.getParent()
      //println("#####Launch selected element: " + element)
      println("#####Launch selected element: " + element.getClass.getName + ", children: " + element.children.length)
    }
    
    //scProject.withPresentationCompiler[Unit] { compiler => 
      //compiler.locateTree(pos)
    //}
    //Runner.run(Array());
  }
  
  private def getClassElement(element: IJavaElement): ScalaClassElement = {
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
  
  def printTree(node: Tree, level: Int): Unit = {
    println(("+" * level) + node.getClass.getName)
    node.children.foreach { child =>
      printTree(child, level + 1)
    }
  }
  
  case class ParentChild(parent: Tree, child: Tree)
  
  def getParent(candidates: Array[ParentChild], node: Tree): Option[Tree] = {
    import scala.collection.mutable.ListBuffer
    val foundOpt = candidates.find(c => c.child == node)
    foundOpt match {
      case Some(a) =>
        Some(a.parent)
      case _ =>
        val nextLevel = new ListBuffer[ParentChild]
        candidates.foreach { c => 
          c.child.children.foreach{ child => 
            nextLevel += ParentChild(c.child, child)
          }
        }
        if (nextLevel.length > 0)
          getParent(nextLevel.toArray, node)
        else
          None
    }
  }
  
  def resolveSelectedAst(editorPart: IEditorPart, typeRoot: ITypeRoot): Tree = {
    val selectionProvider:ISelectionProvider = editorPart.getSite().getSelectionProvider()
    if(selectionProvider == null)
      return null
    val selection:ISelection = selectionProvider.getSelection()
    
    if(!selection.isInstanceOf[ITextSelection])
      return null
    else {
      val textSelection:ITextSelection = selection.asInstanceOf[ITextSelection]
      val element = SelectionConverter.getElementAtOffset(typeRoot, selection.asInstanceOf[ITextSelection])
      element match {
        case scElement: ScalaElement => 
          val classElement = getClassElement(element)
          println("#####scElement: " + scElement.getClass.getName + ", children count: " + scElement.getChildren.length)
          val project = editorPart.getEditorInput.asInstanceOf[IFileEditorInput].getFile.getProject
          val scProject = ScalaPlugin.plugin.getScalaProject(project)
          scProject.withPresentationCompiler { compiler =>
            val scu = scElement.getCompilationUnit.asInstanceOf[ScalaCompilationUnit]
            
            val classPosition = new OffsetPosition(scu.createSourceFile, classElement.getSourceRange.getOffset)
            val rootTree = compiler.locateTree(classPosition)
            
            val position = new OffsetPosition(scu.createSourceFile, textSelection.getOffset)
            val selectedTree = compiler.locateTree(position)
            val parentOpt = getParent(rootTree.children.map(t => ParentChild(rootTree, t)).toArray, selectedTree)
            parentOpt match {
              case Some(parent) => println("#####Found!!, parent is: " + parent)
              case None => println("#####Parent Not Found")
            }
            compiler.locateTree(position)
          } (null)
        case _ =>
          return null
      }
    }
  }
}

object ScalaTestLaunchShortcut {
  def getScalaTestSuites(element: AnyRef):List[IType] = {
    def isScalaTestSuite(iType: IType): Boolean = {
      val typeHier:ITypeHierarchy = iType.newSupertypeHierarchy(null)
      val superTypeArr:Array[IType] = typeHier.getAllSupertypes(iType)
      superTypeArr.findIndexOf {superType => superType.getFullyQualifiedName == "org.scalatest.Suite"} >= 0
    }
    
    val je = element.asInstanceOf[IAdaptable].getAdapter(classOf[IJavaElement]).asInstanceOf[IJavaElement]
    je.getOpenable match {
      case scu: ScalaSourceFile => 
        val ts = scu.getAllTypes()
        ts.filter {tpe => 
          tpe.isInstanceOf[ScalaClassElement] && isScalaTestSuite(tpe)
        }.toList
    }
  }
}