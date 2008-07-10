/*
 * Copyright 2005-2008 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse.javaelements

import scala.util.DynamicVariable

import scala.tools.nsc.util.NameTransformer

import org.eclipse.core.resources.IFile
import org.eclipse.jdt.core.{ ICompilationUnit, JavaCore, JavaModelException }
import org.eclipse.jdt.internal.compiler.classfmt.ClassFileConstants
import org.eclipse.jdt.internal.ui.JavaPlugin
import org.eclipse.jdt.internal.ui.viewsupport.{ JavaElementImageProvider, TreeHierarchyLayoutProblemsDecorator }
import org.eclipse.jdt.ui.JavaElementImageDescriptor
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
      
      def replaceImage(replacement : ScalaIcon, original : Image, element : AnyRef) : Image = {
        val desc = replacement.descriptor
        val rect = original.getBounds
        
        import JavaElementImageProvider.{ BIG_SIZE, SMALL_ICONS, SMALL_SIZE }
        val flags = if (rect.width == 16) SMALL_ICONS else 0
        val size = if ((flags & SMALL_ICONS) != 0) SMALL_SIZE else BIG_SIZE
        val img = JavaPlugin.getImageDescriptorRegistry.get(new JavaElementImageDescriptor(desc, 0, size))
        if(img == null)
          return null
    
        // the Java ProblemsDecorator is not registered in the official
        // decorator list of eclipse, so we need it to call ourself.
        // Problem: if the JDT includes more decorators, we won't know it.
        // Also apply standard decorators (eg. CVS)
        preventRecursion.withValue(true) {
          decman.decorateImage(problemsDecorator.decorateImage(img, element), element)
        }
      }
  
      element match {
        case scu : ScalaCompilationUnit => {
          val file = scu.getCorrespondingResource.asInstanceOf[IFile]
          if(file == null)
            return null
    
          import ScalaImages.{ SCALA_FILE, EXCLUDED_SCALA_FILE }
          val project = JavaCore.create(file.getProject)
          val replacement = if(project.isOnClasspath(file)) SCALA_FILE else EXCLUDED_SCALA_FILE
          replaceImage(replacement, image, element)
        }
        case sc : ScalaClassElement => replaceImage(ScalaImages.SCALA_CLASS, image, element)
        case st : ScalaTraitElement => replaceImage(ScalaImages.SCALA_TRAIT, image, element)
        case so : ScalaModuleElement => replaceImage(ScalaImages.SCALA_OBJECT, image, element)
        case sv : ScalaValElement => {
          val flags = sv.getFlags
          val replacement =
            if ((flags & ClassFileConstants.AccPublic) != 0)
              ScalaImages.PUBLIC_VAL
            else if ((flags & ClassFileConstants.AccProtected) != 0)
              ScalaImages.PROTECTED_VAL
            else
              ScalaImages.PRIVATE_VAL
          replaceImage(replacement, image, element)
        }
        case st : ScalaTypeElement => replaceImage(ScalaImages.SCALA_TYPE, image, element)
        case _ => null
      }
    } catch {
      case _ : JavaModelException => null
    }
  }
  
  def decorateText(text : String, element : AnyRef) : String = {
    try {
      if (preventRecursion.value)
        return null

      element match {
        case so : ScalaModuleElement => {
          val scalaText = text.replace(so.getElementName, so.scalaName)
          preventRecursion.withValue(true) {
            decman.decorateText(problemsDecorator.decorateText(scalaText, element), element)
          }
        }
        case sd : ScalaDefElement => {
          val scalaText = text.replace(sd.getElementName, NameTransformer.decode(sd.getElementName))
          preventRecursion.withValue(true) {
            decman.decorateText(problemsDecorator.decorateText(scalaText, element), element)
          }
        }
        case _ => null
      }
    } catch {
      case _ : JavaModelException => null
    }
  }

  def addListener(listener : ILabelProviderListener ) = {}

  def removeListener(listener : ILabelProviderListener) = {}

  def dispose = {}
}
