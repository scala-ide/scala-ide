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
//import org.scalatest.tools.Runner

class ScalaTestLaunchShortcut extends ILaunchShortcut {
  
  def launch(selection:ISelection, mode:String) {
    // This get called when user right-clicked .scala file on package navigator and choose 'Run As' -> ScalaTest
    // Should just run all suites within the selected file.
  }
  
  def launch(editorPart:IEditorPart, mode:String) {
    // This get called when user right-clicked within the opened file editor and choose 'Run As' -> ScalaTest
    val element:IJavaElement = resolveSelectedElement(editorPart, JavaUI.getEditorInputTypeRoot(editorPart.getEditorInput()))
    if(element == null)
      println("#####Launch all suites within the source file")
    else {
      val elementParent = element.getParent()
      println("#####Launch selected element: " + element)
    }
    //Runner.run(Array());
  }
  
  def resolveSelectedElement(editor:IEditorPart, element:ITypeRoot):IJavaElement = {
    val selectionProvider:ISelectionProvider = editor.getSite().getSelectionProvider()
    if(selectionProvider == null)
      return null
    val selection:ISelection = selectionProvider.getSelection()
    if(!selection.isInstanceOf[ITextSelection])
      return null
    else {
      val textSelection:ITextSelection = selection.asInstanceOf[ITextSelection]
      println("##############getText():" + textSelection.getText())
      println("##############getStartLine():" + textSelection.getStartLine())
      println("##############getEndLine():" + textSelection.getEndLine())
      println("##############getOffset():" + textSelection.getOffset())
      println("##############getLength():" + textSelection.getLength())
      SelectionConverter.getElementAtOffset(element, selection.asInstanceOf[ITextSelection])
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