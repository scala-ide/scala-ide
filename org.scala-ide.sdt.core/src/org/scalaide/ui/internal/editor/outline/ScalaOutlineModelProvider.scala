package org.scalaide.ui.internal.editor.outline
import org.eclipse.jface.text.information.IInformationProvider
import org.eclipse.jface.text.information.IInformationProviderExtension
import org.eclipse.ui.IEditorPart
import org.scalaide.ui.internal.editor.ScalaCompilationUnitEditor
import org.eclipse.jdt.internal.ui.text.JavaWordFinder
import org.eclipse.jface.text.Region;

class ScalaOutlineModelProvider(editor: IEditorPart) extends IInformationProvider with IInformationProviderExtension {
  val scu: Option[ScalaCompilationUnitEditor] = editor match {
    case u: ScalaCompilationUnitEditor => Some(u)
    case _ => None
  }
  def getInformation(viewer: org.eclipse.jface.text.ITextViewer, region: org.eclipse.jface.text.IRegion): String =
    getInformation2(viewer, region).toString();
  def getSubject(viewer: org.eclipse.jface.text.ITextViewer, offset: Int): org.eclipse.jface.text.IRegion = {
    if (viewer != null && editor != null) {
      val region = JavaWordFinder.findWord(viewer.getDocument(), offset)
      if (region != null)
        return region;
      else
        return new Region(offset, 0);
    }
    return null;
  }

  // Members declared in org.eclipse.jface.text.information.IInformationProviderExtension
  def getInformation2(viewer: org.eclipse.jface.text.ITextViewer, region: org.eclipse.jface.text.IRegion): Object = {
    val r = scu.map { x =>
      x.getInteractiveCompilationUnit().scalaProject.presentationCompiler.apply { comp =>
        ModelBuilder.buildTree(comp, x.getInteractiveCompilationUnit().sourceMap(viewer.getDocument.get.toCharArray()).sourceFile)
      }.get
    }
    r.getOrElse(null)
  }
}