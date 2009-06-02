/*
 * Copyright 2005-2009 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse.javaelements

import scala.util.DynamicVariable

import org.eclipse.core.resources.IFile
import org.eclipse.jdt.core.{ ICompilationUnit, JavaCore, JavaModelException }
import org.eclipse.jdt.internal.compiler.classfmt.ClassFileConstants
import org.eclipse.jdt.internal.ui.JavaPlugin
import org.eclipse.jdt.internal.ui.viewsupport.{ JavaElementImageProvider, TreeHierarchyLayoutProblemsDecorator }
import org.eclipse.jdt.ui.JavaElementImageDescriptor
import org.eclipse.jface.resource.ImageDescriptor
import org.eclipse.jface.viewers.{ ILabelDecorator, ILabelProviderListener }
import org.eclipse.swt.graphics.Image
import org.eclipse.ui.internal.WorkbenchPlugin

class ScalaLabelDecorator extends ILabelDecorator {
  
  private val preventRecursion = new DynamicVariable(false)
  private val problemsDecorator = new TreeHierarchyLayoutProblemsDecorator
  private val decman = WorkbenchPlugin.getDefault.getDecoratorManager
    
  def isLabelProperty(element : AnyRef, property : String) = false

  def decorateImage(image : Image, element : AnyRef) : Image = {
    try {
      if (preventRecursion.value)
        return null
      
      element match {
        case se : ScalaElement => {
          val replacement = se.mapLabelImage(image)
          if(replacement == null)
            null
          else {
            // the Java ProblemsDecorator is not registered in the official
            // decorator list of eclipse, so we need it to call ourself.
            // Problem: if the JDT includes more decorators, we won't know it.
            // Also apply standard decorators (eg. CVS)
            preventRecursion.withValue(true) {
              decman.decorateImage(problemsDecorator.decorateImage(replacement, element), element)
            }
          }
        }
        case _ => null
      }
    } catch {
      case _ : JavaModelException => null
    }
  }
  
  def decorateText(text : String, element : AnyRef) : String = {
    try {
      element match {
        case se : ScalaElement => se.mapLabelText(text)
        case _ => null
      }
    } catch {
      case _ : JavaModelException => null
    }
  }

  def addListener(listener : ILabelProviderListener) = {}

  def removeListener(listener : ILabelProviderListener) = {}

  def dispose = {}
}
