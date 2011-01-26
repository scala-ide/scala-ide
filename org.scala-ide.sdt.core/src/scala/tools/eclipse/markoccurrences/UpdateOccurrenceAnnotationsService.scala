package scala.tools.eclipse
package markoccurrences

import org.eclipse.core.runtime.IAdaptable
import org.eclipse.jdt.core.IJavaElement
import org.eclipse.jdt.core.dom.CompilationUnit
import org.eclipse.jface.text._
import org.eclipse.jface.text.source.Annotation
import org.eclipse.ui.IEditorInput
import org.eclipse.ui.texteditor.IDocumentProvider
import scala.collection.mutable
import scala.collection.JavaConversions._
import scala.tools.eclipse.internal.logging.Defensive
import scala.tools.eclipse.javaelements.ScalaSourceFile
import scala.tools.eclipse.util.{Annotations, AnnotationsTypes, IDESettings}
import scala.actors.Reactor

class UpdateOccurrenceAnnotationsService {
  private val Strategies_NotInMain = "not in main thread"
  private val Strategies_InCaller = "in caller"
  private val Strategies_InWorker = "in dedicated worker"
  private val Strategies_InActor = "in shared actor"
    
  val strategies = List(Strategies_InActor, Strategies_NotInMain, Strategies_InWorker, Strategies_InCaller)
    
  def askUpdateOccurrenceAnnotations(editor : ScalaSourceFileEditor, selection: ITextSelection, astRoot: CompilationUnit) {
    import org.eclipse.core.runtime.jobs.Job
    
    if (selection eq null) return
    
    val documentProvider = editor.getDocumentProvider
    
    if (documentProvider eq null)
      return
    if (IDESettings.markOccurencesForSelectionOnly.value && selection.getLength < 1)
      return

    IDESettings.markOccurencesTStrategy.value match {
      case Strategies_NotInMain => Defensive.askRunOutOfMain("updateOccurrenceAnnotations", Job.DECORATE) { updateOccurrenceAnnotations0(editor, documentProvider, selection, astRoot) }
      case Strategies_InCaller => updateOccurrenceAnnotations0(editor, documentProvider, selection, astRoot)
      case Strategies_InWorker => Defensive.askRunInJob("updateOccurrenceAnnotations", Job.DECORATE) { updateOccurrenceAnnotations0(editor, documentProvider, selection, astRoot) }
      case Strategies_InActor => _processorQ ! UpdateOccurrenceAnnotationsReq(editor, documentProvider, selection, astRoot)
    }
  }
  
  private def updateOccurrenceAnnotations0(editor : ScalaSourceFileEditor, documentProvider : IDocumentProvider, selection: ITextSelection, astRoot: CompilationUnit) : Unit = Defensive.tryOrLog {
    val scalaSourceFile = editor.getEditorInput.asInstanceOf[IAdaptable].getAdapter(classOf[IJavaElement]).asInstanceOf[ScalaSourceFile]
    if (!Defensive.notNull(scalaSourceFile, "scalaSourceFile")) //issue_0001
      return
    val annotations = getAnnotations(selection, scalaSourceFile)
    Annotations.update(documentProvider.getAnnotationModel(editor.getEditorInput),  AnnotationsTypes.Occurrences, annotations) 
    editor.superUpdateOccurrenceAnnotations(selection, astRoot)
  }

  private def getAnnotations(selection: ITextSelection, scalaSourceFile: ScalaSourceFile): mutable.Map[Annotation, Position] = {
    val annotations = for {
      Occurrences(name, locations) <- new ScalaOccurrencesFinder(scalaSourceFile, selection.getOffset, selection.getLength).findOccurrences.toList
      location <- locations
      val offset = location.getOffset
      val length = location.getLength
      val position = new Position(location.getOffset, location.getLength)
    } yield new Annotation(AnnotationsTypes.Occurrences, false, "Occurrence of '" + name + "'") -> position
    mutable.Map(annotations: _*)
  }
  
  
  private case class UpdateOccurrenceAnnotationsReq(editor : ScalaSourceFileEditor, documentProvider : IDocumentProvider, selection: ITextSelection, astRoot: CompilationUnit)
  private lazy val _processorQ = new Reactor[UpdateOccurrenceAnnotationsReq] {
    private var _lastReq : UpdateOccurrenceAnnotationsReq = null
    def act() = loop {
      react {
        case r @ UpdateOccurrenceAnnotationsReq(editor, documentProvider, selection, astRoot) if r != _lastReq => { updateOccurrenceAnnotations0(editor, documentProvider, selection, astRoot) }
        case _ => // forgot
      }
    }
  }.start
}