/*
 * Copyright 2005-2010 LAMP/EPFL
 * @author Matthew Farwell
 */
// $Id$

package scala.tools.eclipse.launching

import org.eclipse.core.expressions.PropertyTester
import org.eclipse.core.resources.IContainer
import org.eclipse.core.resources.IResource
import org.eclipse.core.runtime.CoreException
import org.eclipse.core.runtime.IAdaptable
import org.eclipse.jdt.core.Flags
import org.eclipse.jdt.core.IClassFile
import org.eclipse.jdt.core.ICompilationUnit
import org.eclipse.jdt.core.IJavaElement
import org.eclipse.jdt.core.IMember
import org.eclipse.jdt.core.IMethod
import org.eclipse.jdt.core.IType
import org.eclipse.jdt.core.JavaModelException
import scala.tools.eclipse.util.EclipseUtils._
import scala.tools.eclipse.javaelements.ScalaSourceFile

class ScalaLaunchableTester extends PropertyTester {
  /**
   * name for the "has main" property
   */
  private val PROPERTY_HAS_MAIN = "hasMain" //$NON-NLS-1$

    /**
   * name for the "can launch as junit" property
   */
  private val PROPERTY_CAN_LAUNCH_AS_JUNIT = "canLaunchAsJUnit"; //$NON-NLS-1$

  /**
   * Determines if the Scala element contains main method(s).
   *
   * @param element the element to check for the method
   * @return true if a method is found in the element, false otherwise
   */
  private def hasMain(element: IJavaElement): Boolean = {
    try {
      ScalaLaunchShortcut.getMainMethods(element).length > 0
    } catch {
      case e: JavaModelException => false
      case e: CoreException => false
    }
  }

  /**
   * Determines if the Scala element is in a source that contains one (or more) runnable JUnit test class.
   *
   * @param element the element to check for the method
   * @return true if one or more JUnit test classes are found in the element, false otherwise
   */
  private def canLaunchAsJUnit(element: IJavaElement): Boolean = {
    try {
      element match {
        case e: ScalaSourceFile =>
          ScalaLaunchShortcut.getJunitTestClasses(element).nonEmpty
        case _ => true
      }
    } catch {
      case e: JavaModelException => false
      case e: CoreException => false
    }
  }

  /**
   * Method runs the tests defined from extension points for Run As... and Debug As... menu items.
   * Currently this test optimistically considers everything not a source file. In this context we
   * consider an optimistic approach to mean that the test will always return true.
   *
   * There are many reasons for the optimistic choice some of them are outlined below.
   * <ul>
   * <li>Performance (in terms of time needed to display menu) cannot be preserved. To know what to allow
   * in any one of the menus we would have to search all of the children of the container to determine what it contains
   * and what can be launched by what.</li>
   * <li>If inspection of children of containers were done, a user might want to choose a different launch type, even though our tests
   * filter it out.</li>
   * </ul>
   * @see org.eclipse.core.expressions.IPropertyTester#test(java.lang.Object, java.lang.String, java.lang.Object[], java.lang.Object)
   * @since 3.2
   * @return true if the specified tests pass, false otherwise
   */
  def test(receiver: Object, property: String, args: Array[Object], expectedValue: Object): Boolean = {
    var element: IJavaElement = null

    if (receiver.isInstanceOf[IAdaptable]) {
      element = receiver.asInstanceOf[IAdaptable].adaptTo[IJavaElement]
      if (element == null || !element.exists()) {
        return false
      }
    }

    property match {
      case PROPERTY_HAS_MAIN => hasMain(element)
      case PROPERTY_CAN_LAUNCH_AS_JUNIT => canLaunchAsJUnit(element)
      case _ => false
    }
  }
}
