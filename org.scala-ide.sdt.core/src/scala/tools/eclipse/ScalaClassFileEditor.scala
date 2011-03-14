/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse

import org.eclipse.jdt.core.{ ICompilationUnit, IJavaElement }
import org.eclipse.jdt.internal.ui.javaeditor.ClassFileEditor
import org.eclipse.jdt.ui.text.JavaSourceViewerConfiguration
import org.eclipse.jface.text.source.SourceViewerConfiguration

import scala.tools.eclipse.javaelements.ScalaClassFile

class ScalaClassFileEditor extends ClassFileEditor with ScalaEditor {
  override def createJavaSourceViewerConfiguration : JavaSourceViewerConfiguration =
    new ScalaSourceViewerConfiguration(getPreferenceStore, ScalaPlugin.plugin.getPreferenceStore, this)

  override def setSourceViewerConfiguration(configuration : SourceViewerConfiguration) {
    super.setSourceViewerConfiguration(
      configuration match {
        case svc : ScalaSourceViewerConfiguration => svc
        case _ => new ScalaSourceViewerConfiguration(getPreferenceStore, ScalaPlugin.plugin.getPreferenceStore, this)
      })
  }

  override def getElementAt(offset : Int) : IJavaElement = {
    getInputJavaElement match {
      case scf : ScalaClassFile => scf.getElementAt(offset)
      case _ => null
    }
  }

  override def getCorrespondingElement(element : IJavaElement) : IJavaElement = {
      getInputJavaElement match {
        case scf : ScalaClassFile => scf.getCorrespondingElement(element).getOrElse(super.getCorrespondingElement(element))
        case _ => super.getCorrespondingElement(element)
    }
  }
}
