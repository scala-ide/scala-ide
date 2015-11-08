package org.scalaide.ui.internal.editor.outline
import org.eclipse.jface.text.information.IInformationProvider
import org.eclipse.jface.text.information.IInformationProviderExtension
import org.eclipse.ui.IEditorPart
import org.scalaide.ui.internal.editor.ScalaCompilationUnitEditor
import org.eclipse.jdt.internal.ui.text.JavaWordFinder
import org.eclipse.jface.text.Region

class ScalaOutlineModelProvider(scu: ScalaCompilationUnitEditor) extends IInformationProvider with IInformationProviderExtension {
  def getInformation(viewer: org.eclipse.jface.text.ITextViewer, region: org.eclipse.jface.text.IRegion): String =
    getInformation2(viewer, region).toString()
  def getSubject(viewer: org.eclipse.jface.text.ITextViewer, offset: Int): org.eclipse.jface.text.IRegion = {
    if (viewer != null) {
      val region = JavaWordFinder.findWord(viewer.getDocument(), offset)
      Option(region) getOrElse new Region(offset, 0)
    } else
      null
  }

  // Members declared in org.eclipse.jface.text.information.IInformationProviderExtension
  def getInformation2(viewer: org.eclipse.jface.text.ITextViewer, region: org.eclipse.jface.text.IRegion): Object = {
    scu.getInteractiveCompilationUnit().scalaProject.presentationCompiler.apply { comp =>
      ModelBuilder.buildTree(comp, scu.getInteractiveCompilationUnit().sourceMap(viewer.getDocument.get.toCharArray()).sourceFile)
    }.orNull
  }
}