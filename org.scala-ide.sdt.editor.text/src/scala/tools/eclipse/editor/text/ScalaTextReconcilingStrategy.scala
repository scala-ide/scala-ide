package scala.tools.eclipse
package editor.text

import org.eclipse.jface.text.{IRegion, IDocument}
import org.eclipse.jface.text.reconciler.{IReconcilingStrategyExtension, DirtyRegion, IReconcilingStrategy}
import scala.tools.eclipse.util.Tracer
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.ui.texteditor.MarkerUtilities
import java.util.HashMap
import org.eclipse.core.resources.IMarker
import org.eclipse.ui.texteditor.DocumentProviderRegistry

class ScalaTextReconcilingStrategy extends IReconcilingStrategy with IReconcilingStrategyExtension{
  
  private var _lastDocument : Option[IDocument] = None

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
    _lastDocument = Option(document)
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
//    val map = new HashMap[String, java.lang.Integer]()
//    MarkerUtilities.setLineNumber(map, 1) //1-based line numbering
//    MarkerUtilities.setMessage(map, "This is some sample warning.")
//    map.put(IMarker.SEVERITY, IMarker.SEVERITY_WARNING)
//    val file = DocumentProviderRegistry.getDefault().getDocumentProvider("scala").asInstanceOf[ScalaDocumentProvider].getFile(_lastDocument.get).get
//    //DocumentProviderRegistry.getDefault().getDocumentProvider("scala")
//    MarkerUtilities.createMarker(file, map, "problem")
    //IDocumentProvider.getAnnotationModel()
    Thread.sleep(3000) //FIXME to remove (used to simulate a freeze)
  }

}