package scala.tools.eclipse.semantichighlighting

import scala.collection.JavaConversions._
import scala.collection.Set
import scala.tools.eclipse.javaelements.ScalaCompilationUnit
import scala.tools.eclipse.properties.syntaxcolouring.ScalaSyntaxClasses
import scala.tools.eclipse.semantichighlighting.classifier._
import scala.tools.eclipse.ScalaPlugin
import scala.tools.eclipse.util.RichAnnotationModel._
import scala.tools.eclipse.logging.HasLogger
import org.eclipse.jface.text.source._
import org.eclipse.jface.text.Position
import scala.tools.eclipse.semantic.SemanticAction
import scala.tools.eclipse.util.Utils.debugTimed
import org.eclipse.core.runtime._
import org.eclipse.core.runtime.jobs.Job
import scala.tools.eclipse.javaelements.ScalaCompilationUnit

class SemanticHighlightingAnnotationsManager(sourceViewer: ISourceViewer) extends SemanticAction with HasLogger {

  private var annotations: Set[Annotation] = Set()

  /** One job family per annotation manager, so we can cancel existing jobs before scheduling another job */
  private val jobFamily = new Object

  override def apply(scu: ScalaCompilationUnit) {
    if (semanticHighlightingRequired) {
      // cancel any in-progress or not-yet-scheduled jobs for this family
      Job.getJobManager.cancel(jobFamily)

      val job = semanticHighlightingJob(scu)

      job.setPriority(Job.DECORATE)
      job.schedule()
    } else
      removeAllAnnotations()
  }

  private def semanticHighlightingJob(scu: ScalaCompilationUnit): Job =
    new SemanticHighlightingJob(scu)

  private def semanticHighlightingRequired: Boolean =
    isSemanticHighlightingEnabled &&
      (ScalaSyntaxClasses.scalaSemanticCategory.children.map(_.enabledKey) exists prefStore.getBoolean)

  @inline private def prefStore = ScalaPlugin.prefStore

  private def isSemanticHighlightingEnabled: Boolean = prefStore.getBoolean(ScalaSyntaxClasses.ENABLE_SEMANTIC_HIGHLIGHTING)
  private def isUseSyntacticHintsEnabled: Boolean = prefStore.getBoolean(ScalaSyntaxClasses.USE_SYNTACTIC_HINTS)

  private def makeAnnotations(symbolInfos: List[SymbolInfo]): Map[Annotation, Position] = {
    val strikethroughDeprecatedSymbols = isStrikethroughDeprecatedDecorationEnabled
    for {
      SymbolInfo(symbolType, regions, isDeprecated) <- symbolInfos
      region <- regions
      deprecated = isDeprecated && strikethroughDeprecatedSymbols
      annotation = SemanticHighlightingAnnotations.symbolAnnotation(symbolType, deprecated)
    } yield (annotation -> asPosition(region))
  }.toMap

  private def isStrikethroughDeprecatedDecorationEnabled: Boolean =
    prefStore.getBoolean(ScalaSyntaxClasses.STRIKETHROUGH_DEPRECATED)

  private def setAnnotations(symbolInfos: List[SymbolInfo]) {
    val annotationsToPositions: Map[Annotation, Position] = debugTimed("makeAnnotations")(makeAnnotations(symbolInfos))
    for (annotationModel <- annotationModelOpt) {
      annotationModel.withLock {
        debugTimed("replaceAnnotations")(annotationModel.replaceAnnotations(annotations, annotationsToPositions))
        annotations = annotationsToPositions.keySet
      }
    }
  }

  private def asPosition(region: Region) = new Position(region.offset, region.length)

  private def removeAllAnnotations() =
    if (annotations.nonEmpty)
      for (annotationModel <- annotationModelOpt)
        annotationModel.withLock {
          annotationModel.deleteAnnotations(annotations)
          annotations = Set()
        }

  private def annotationModelOpt = Option(sourceViewer.getAnnotationModel)

  /** A background job that performs semantic highlighting. */
  private class SemanticHighlightingJob(scu: ScalaCompilationUnit) extends Job("semantic highlighting") {
    @volatile private var cancelled = false

    def run(monitor: IProgressMonitor): IStatus = {
      scu.doWithSourceFile { (sourceFile, compiler) =>
        val useSyntacticHints = isUseSyntacticHintsEnabled
        logger.info("Semantic highlighting " + scu.getResource.getName)
        val symbolInfos =
          try SymbolClassifier.classifySymbols(sourceFile, compiler, useSyntacticHints)
          catch {
            case e =>
              logger.error("Error performing semantic highlighting", e)
              Nil
          }
        if (!cancelled) setAnnotations(symbolInfos)
      }
      Status.OK_STATUS
    }

    override def canceling() {
      cancelled = true
    }

    /** It belongs to the semantic highlighting family. */
    override def belongsTo(family: Object) =
      jobFamily eq family
  }
}