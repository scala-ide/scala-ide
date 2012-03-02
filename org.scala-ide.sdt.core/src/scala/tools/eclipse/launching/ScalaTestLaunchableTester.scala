package scala.tools.eclipse.launching

import org.eclipse.core.expressions.PropertyTester
import scala.tools.eclipse.javaelements.ScalaSourceFile
import org.eclipse.jdt.core.IType
import org.eclipse.jdt.core.ITypeHierarchy
import org.eclipse.jdt.core.IJavaElement
import org.eclipse.core.runtime.IAdaptable
import org.eclipse.jdt.internal.core.PackageFragment
import scala.tools.eclipse.ScalaSourceFileEditor
import scala.tools.eclipse.javaelements.ScalaClassElement
import org.eclipse.jdt.ui.JavaUI
import org.eclipse.jface.text.ITextSelection
import org.eclipse.jdt.internal.ui.actions.SelectionConverter
import org.eclipse.ui.part.FileEditorInput
import org.eclipse.ui.PlatformUI

class ScalaTestPackageTester extends PropertyTester {
  
  def test(receiver: Object, property: String, args: Array[Object], expectedValue: Object): Boolean = {
    receiver match {
      case packageFragment: PackageFragment => 
        true
      case _ => 
        false
    }
  }
}

class ScalaTestTestTester extends PropertyTester {
  
  def test(receiver: Object, property: String, args: Array[Object], expectedValue: Object): Boolean = {
    receiver match {
      case scEditor: ScalaSourceFileEditor => 
        try {
          val selectionOpt = ScalaTestLaunchShortcut.resolveSelectedAst(scEditor.getEditorInput, scEditor.getEditorSite.getSelectionProvider)
          selectionOpt match {
            case Some(selection) => 
              true
            case None => 
              false
          }
        }
        catch {
          case _ => false
        }
      case _ =>
        false
    }
  }
}

class ScalaTestSuiteTester extends PropertyTester {
  
  def test(receiver: Object, property: String, args: Array[Object], expectedValue: Object): Boolean = {
    try {
      receiver match {
        case classElement: ScalaClassElement => 
          ScalaTestLaunchShortcut.isScalaTestSuite(classElement)
        case editorInput: FileEditorInput => 
          val editorPart = PlatformUI.getWorkbench.getActiveWorkbenchWindow.getActivePage.getActiveEditor
          val typeRoot = JavaUI.getEditorInputTypeRoot(editorInput)
          if (editorPart != null) {
            val selectionProvider = editorPart.getEditorSite.getSelectionProvider
            if(selectionProvider == null)
              false
            else {
              val selection = selectionProvider.getSelection
              if (!selection.isInstanceOf[ITextSelection])
                false
              else {
                val textSelection:ITextSelection = selection.asInstanceOf[ITextSelection]
                val element = SelectionConverter.getElementAtOffset(typeRoot, selection.asInstanceOf[ITextSelection])
                ScalaTestLaunchShortcut.getScalaTestSuite(element) match {
                  case Some(_) => true
                  case None => false
                }
              }
            }
          }
          else
            false
        case _ =>
          false
      }
    }
    catch {
      case _ => false
    }
  }
}

class ScalaTestFileTester extends PropertyTester {
  def test(receiver: Object, property: String, args: Array[Object], expectedValue: Object): Boolean = {
    try {
      receiver match {
        case scSrcFile: ScalaSourceFile => 
          ScalaTestLaunchShortcut.containsScalaTestSuite(scSrcFile)
        case editorInput: FileEditorInput => 
          if(receiver.isInstanceOf[IAdaptable]) {
            val je = receiver.asInstanceOf[IAdaptable].getAdapter(classOf[IJavaElement]).asInstanceOf[IJavaElement]
            je.getOpenable match {
              case scSrcFile: ScalaSourceFile => 
                ScalaTestLaunchShortcut.containsScalaTestSuite(scSrcFile)
              case _ => false
            }
          }
          else
            false
        case _ =>
          false
      }
    }
    catch {
      case _ => false
    }
  }
}