package scala.tools.eclipse.semantichighlighting

import scala.tools.eclipse.properties.syntaxcolouring.ScalaSyntaxClass
import scala.tools.eclipse.properties.syntaxcolouring.ScalaSyntaxClasses
import scala.tools.eclipse.semantichighlighting.classifier.SymbolType
import scala.tools.eclipse.semantichighlighting.classifier.SymbolTypes
import org.eclipse.jface.preference.IPreferenceStore
import org.eclipse.jface.text.source.AnnotationPainter
import org.eclipse.ui.texteditor.SourceViewerDecorationSupport

/**
 * Misc wiring for semantic highlighting annotations
 */
object SemanticHighlightingAnnotations {

  private val TEXT_PREFERENCE_KEY = "scala.tools.eclipse.semantichighlighting.text"

  private def annotationType(syntaxClass: ScalaSyntaxClass, deprecated: Boolean) =
    syntaxClass.baseName + (if (deprecated) ".deprecated" else "") + ".annotationType"

  private def paintingStrategyId(syntaxClass: ScalaSyntaxClass, deprecated: Boolean) =
    syntaxClass.baseName + (if (deprecated) ".deprecated" else "") + ".paintingStrategyId"

  // Used to look up the paintingStrategyId in a pref store
  private def stylePreferenceKey(syntaxClass: ScalaSyntaxClass, deprecated: Boolean) =
    syntaxClass.baseName + (if (deprecated) ".deprecated" else "") + ".stylePreferenceKey"

  def initAnnotationPreferences(javaPrefStore: IPreferenceStore) {
    javaPrefStore.setDefault(TEXT_PREFERENCE_KEY, true)
    for {
      syntaxClass <- ScalaSyntaxClasses.scalaSemanticCategory.children
      deprecated <- List(false, true)
      paintingId = paintingStrategyId(syntaxClass, deprecated)
      preferenceKey = stylePreferenceKey(syntaxClass, deprecated)
    } javaPrefStore.setDefault(preferenceKey, paintingId)
  }

  def symbolAnnotation(symbolType: SymbolType, deprecated: Boolean) = {
    val syntaxClass = symbolTypeToSyntaxClass(symbolType)
    new SemanticHighlightingAnnotation(annotationType(syntaxClass, deprecated))
  }

  def addAnnotationPreferences(support: SourceViewerDecorationSupport) =
    for {
      syntaxClass <- ScalaSyntaxClasses.scalaSemanticCategory.children
      deprecated <- List(false, true)
    } support.setAnnotationPreference(annotationPreference(syntaxClass, deprecated))

  private def annotationPreference(syntaxClass: ScalaSyntaxClass, deprecated: Boolean) =
    new AnnotationPreferenceWithForegroundColourStyle(
      annotationType(syntaxClass, deprecated),
      TEXT_PREFERENCE_KEY,
      stylePreferenceKey(syntaxClass, deprecated))

  def addTextStyleStrategies(annotationPainter: AnnotationPainter) {
    for {
      syntaxClass <- ScalaSyntaxClasses.scalaSemanticCategory.children
      deprecated <- List(false, true)
      paintStrategyId = paintingStrategyId(syntaxClass, deprecated)
      textStyleStrategy = new SemanticHighlightingTextStyleStrategy(syntaxClass, deprecated)
    } annotationPainter.addTextStyleStrategy(paintStrategyId, textStyleStrategy)
  }

  private def symbolTypeToSyntaxClass(symbolType: SymbolType) = {
    import SymbolTypes._
    import ScalaSyntaxClasses._
    symbolType match {
      case Annotation => ANNOTATION
      case CaseClass => CASE_CLASS
      case CaseObject => CASE_OBJECT
      case Class => CLASS
      case LazyLocalVal => LAZY_LOCAL_VAL
      case LazyTemplateVal => LAZY_TEMPLATE_VAL
      case LocalVal => LOCAL_VAL
      case LocalVar => LOCAL_VAR
      case Method => METHOD
      case Param => PARAM
      case Object => OBJECT
      case Package => PACKAGE
      case TemplateVar => TEMPLATE_VAR
      case TemplateVal => TEMPLATE_VAL
      case Trait => TRAIT
      case Type => TYPE
      case TypeParameter => TYPE_PARAMETER
    }
  }
  
}
