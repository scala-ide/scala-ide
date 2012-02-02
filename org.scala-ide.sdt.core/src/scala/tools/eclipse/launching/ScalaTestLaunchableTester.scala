package scala.tools.eclipse.launching

import org.eclipse.core.expressions.PropertyTester
import scala.tools.eclipse.javaelements.ScalaSourceFile
import org.eclipse.jdt.core.IType
import org.eclipse.jdt.core.ITypeHierarchy
import org.eclipse.jdt.core.IJavaElement
import org.eclipse.core.runtime.IAdaptable

class ScalaTestLaunchableTester extends PropertyTester {
  
  def test(receiver: Object, property: String, args: Array[Object], expectedValue: Object): Boolean = {
    if(receiver.isInstanceOf[IAdaptable]) {
      val je = receiver.asInstanceOf[IAdaptable].getAdapter(classOf[IJavaElement]).asInstanceOf[IJavaElement]
      canLaunchAsScalaTest(je)
    }
    else
      false
  }
  
  private def canLaunchAsScalaTest(element: IJavaElement):Boolean = {
    try {
      ScalaTestLaunchShortcut.getScalaTestSuites(element).length > 0
    } catch {
      case e:Exception => false
    }
  }
}