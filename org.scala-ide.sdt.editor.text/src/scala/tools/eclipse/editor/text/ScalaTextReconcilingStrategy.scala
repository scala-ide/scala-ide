package scala.tools.eclipse
package editor.text

import org.eclipse.jface.text.{IRegion, IDocument}
import org.eclipse.jface.text.reconciler.{IReconcilingStrategyExtension, DirtyRegion, IReconcilingStrategy}
import scala.tools.eclipse.util.Tracer
import org.eclipse.core.runtime.IProgressMonitor
import java.util.HashMap
import org.eclipse.core.resources.{IMarker, IFile}
import org.eclipse.ui.texteditor.DocumentProviderRegistry
import org.eclipse.jdt.core.compiler.IProblem
import org.eclipse.jface.text.source.ISourceViewer

class ScalaTextReconcilingStrategy(sourceViewer : ISourceViewer) extends IReconcilingStrategy with IReconcilingStrategyExtension{
  
  private var _lastDocument : (Option[IDocument],Option[IFile]) = (None, None)

  /**
   * Tells this reconciling strategy on which document it will
   * work. This method will be called before any other method
   * and can be called multiple times. The regions passed to the
   * other methods always refer to the most recent document
   * passed into this method.
   *
   * @param document the document on which this strategy will work
   */
  def setDocument(document : IDocument) {
    val o = Option(document)
    _lastDocument = (o, o.flatMap(toFile)) 
    Tracer.println("reconcile (setDocument) " + this + " : " + _lastDocument)
  }

  /**
   * Called only once in the life time of this reconciling strategy.
   */
  def initialReconcile() {
    Tracer.println("reconcile (initial) " + this + " : " + _lastDocument)
    Thread.sleep(3000)
  }

  /**
   * Tells this reconciling strategy with which progress monitor
   * it will work. This method will be called before any other
   * method and can be called multiple times.
   *
   * @param monitor the progress monitor with which this strategy will work
   */
  def setProgressMonitor(monitor : IProgressMonitor) {
    
  }

  
  /**
   * Activates incremental reconciling of the specified dirty region.
   * As a dirty region might span multiple content types, the segment of the
   * dirty region which should be investigated is also provided to this
   * reconciling strategy. The given regions refer to the document passed into
   * the most recent call of {@link #setDocument(IDocument)}.
   *
   * @param dirtyRegion the document region which has been changed
   * @param subRegion the sub region in the dirty region which should be reconciled
   */
  def reconcile(dirtyRegion : DirtyRegion, subRegion : IRegion) {
    Tracer.println("reconcile (inc) " + this + " : " + _lastDocument + " -- " + dirtyRegion + " -- " + subRegion)
  }

  /**
   * Activates non-incremental reconciling. The reconciling strategy is just told
   * that there are changes and that it should reconcile the given partition of the
   * document most recently passed into {@link #setDocument(IDocument)}.
   *
   * @param partition the document partition to be reconciled
   */
  def reconcile(partition : IRegion) {
    Tracer.println("reconcile " + this + " : " + _lastDocument + " -- " + partition)
//    import org.eclipse.ui.texteditor.MarkerUtilities
//    val map = new HashMap[String, java.lang.Integer]()
//    MarkerUtilities.setLineNumber(map, 1) //1-based line numbering
//    MarkerUtilities.setMessage(map, "This is some sample warning.")
//    map.put(IMarker.SEVERITY, IMarker.SEVERITY_WARNING)
//    val file = DocumentProviderRegistry.getDefault().getDocumentProvider("scala").asInstanceOf[ScalaDocumentProvider].getFile(_lastDocument.get).get
//    //DocumentProviderRegistry.getDefault().getDocumentProvider("scala")
//    MarkerUtilities.createMarker(file, map, "problem")
    //IDocumentProvider.getAnnotationModel()
    for (d <- _lastDocument._1; f <- _lastDocument._2) {
      val problems = compile(d, f)
      displayProblems(f, problems)
    }
  }

  private def toFile(v : IDocument) : Option[IFile] = {
    DocumentProviderRegistry.getDefault().getDocumentProvider("scala").asInstanceOf[ScalaDocumentProvider].getFile(v)
  }
  
  private def compile(d: IDocument, f : IFile) : List[IProblem]= {
    import scala.tools.eclipse.util.FileUtils
//    Thread.sleep(3000) //FIXME to remove (used to simulate a freeze)
    val project = ScalaPlugin.plugin.getScalaProject(f.getProject)
    project.withPresentationCompiler{ compiler =>
//      val af = new PlainFile(Path(f.getLocation.toFile))
      FileUtils.toAbstractFile(Some(f)) match {
        case None => Nil
        case Some(af) => {
          compiler.askReload(af, d.get.toCharArray)
          compiler.askRunLoadedTyped(af)
          compiler.askProblemsOf(af)
        }
      }
    }(project.defaultOrElse)
  }
  
  private def displayProblems(f: IFile, problems : List[IProblem]) : Unit = {
    import org.eclipse.jface.text.Position
    import org.eclipse.jface.text.source.{Annotation, IAnnotationModel}
    import scala.tools.eclipse.util.{Annotations, AnnotationsTypes}
    
    // model is null because no annotation model was created from DocumentProvider
    //val model : IAnnotationModel = DocumentProviderRegistry.getDefault().getDocumentProvider("scala").asInstanceOf[ScalaDocumentProvider].getAnnotationModel(d)
    //Tracer.println("model :" + model)
    
    val toAdds= new java.util.HashMap[Annotation, Position]()
    for (p <- problems) {
      Tracer.println("add problem : " + p)
      toAdds.put(new ProblemAnnotation(p), new Position(p.getSourceStart, math.max(p.getSourceEnd - p.getSourceStart, 1)))
    }
    Annotations.update(sourceViewer, AnnotationsTypes.Problems, toAdds)
  //TODO use utils.Annotations
    
//    val warningMarkerId = ScalaPlugin.plugin.problemMarkerId
//    for (p <- problems) {
//      Tracer.println("add problem : " + p)
//      try {
//        val mrk = f.createMarker(warningMarkerId)
//        mrk.setAttribute(IMarker.SEVERITY, if (p.isError) IMarker.SEVERITY_ERROR else IMarker.SEVERITY_WARNING)
//        mrk.setAttribute(IMarker.MESSAGE, p.getMessage)
//        mrk.setAttribute(IMarker.PRIORITY, IMarker.PRIORITY_LOW)
//        if (p.getSourceStart > -1 && p.getSourceEnd > -1) {
//          mrk.setAttribute(IMarker.CHAR_START, p.getSourceStart)
//          mrk.setAttribute(IMarker.CHAR_END, p.getSourceEnd)
//        } else if (p.getSourceLineNumber > -1) {
//          mrk.setAttribute(IMarker.LINE_NUMBER, p.getSourceLineNumber)
//        } else {
//          mrk.setAttribute(IMarker.LOCATION, p.getOriginatingFileName)
//        }
//      } catch {
//        case e: Exception => e.printStackTrace();
//      }
//    }    
  }

}
