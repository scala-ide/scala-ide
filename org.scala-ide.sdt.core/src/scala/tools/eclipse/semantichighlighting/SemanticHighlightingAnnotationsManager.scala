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

class SemanticHighlightingAnnotationsManager(sourceViewer: ISourceViewer) extends SemanticAction with HasLogger {

  private var annotations: Set[Annotation] = Set()

  override def apply(scu: ScalaCompilationUnit) {
    if (semanticHighlightingRequired)
      scu.doWithSourceFile { (sourceFile, compiler) =>
        val useSyntacticHints = isUseSyntacticHintsEnabled
        
        val symbolInfos = 
          try SymbolClassifier.classifySymbols(sourceFile, compiler, useSyntacticHints)
          catch {
            case e =>
              logger.error("Error performing semantic highlighting", e)
              Nil
          }
        setAnnotations(symbolInfos)
      }
    else
      removeAllAnnotations()
  }

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
    for (annotationModel <- annotationModelOpt)
      annotationModel.withLock {
        debugTimed("replaceAnnotations")(annotationModel.replaceAnnotations(annotations, annotationsToPositions))
        annotations = annotationsToPositions.keySet
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

}