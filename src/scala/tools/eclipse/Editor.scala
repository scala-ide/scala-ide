/*
 * Copyright 2005-2009 LAMP/EPFL
 * @author Sean McDirmid
 */
// $Id$

package scala.tools.eclipse

import org.eclipse.jdt.core.{ ICompilationUnit, IJavaElement, JavaModelException }
import org.eclipse.jdt.internal.corext.util.JavaModelUtil
import org.eclipse.jdt.internal.ui.JavaPlugin
import org.eclipse.jdt.internal.ui.javaeditor.ClassFileDocumentProvider
import org.eclipse.ui.IEditorInput

import scala.tools.eclipse.contribution.weaving.jdt.ui.javaeditor.ScalaCompilationUnitDocumentProvider

class Editor extends { val plugin = Driver.driver } with lampion.eclipse.Editor {
  override def doSetInput(input : IEditorInput) = {
    input match {
      case null =>
      case input : plugin.ClassFileInput =>
        setDocumentProvider(new ClassFileDocumentProvider)
      case _ =>
        setDocumentProvider(new ScalaCompilationUnitDocumentProvider)
    }
    super.doSetInput(input)
  }

  override def getCorrespondingElement(element : IJavaElement ) : IJavaElement = element

  override def getElementAt(offset : Int) : IJavaElement = getElementAt(offset, true)

  override def getElementAt(offset : Int, reconcile : Boolean) : IJavaElement = {
    getInputJavaElement match {
      case unit : ICompilationUnit => 
        try {
          if (reconcile) {
            JavaModelUtil.reconcile(unit)
            unit.getElementAt(offset)
          } else if (unit.isConsistent())
            unit.getElementAt(offset)
          else
            null
        } catch {
          case ex : JavaModelException => {
            if (!ex.isDoesNotExist)
              JavaPlugin.log(ex.getStatus)
            null
          }
        }
      case _ => null
    }
  }
}
