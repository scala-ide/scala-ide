package scala.tools.eclipse.semantichighlighting.ui

import scala.collection.immutable
import scala.tools.eclipse.properties.syntaxcolouring.ScalaSyntaxClass
import scala.tools.eclipse.properties.syntaxcolouring.ScalaSyntaxClasses.ANNOTATION
import scala.tools.eclipse.properties.syntaxcolouring.ScalaSyntaxClasses.CASE_CLASS
import scala.tools.eclipse.properties.syntaxcolouring.ScalaSyntaxClasses.CASE_OBJECT
import scala.tools.eclipse.properties.syntaxcolouring.ScalaSyntaxClasses.CLASS
import scala.tools.eclipse.properties.syntaxcolouring.ScalaSyntaxClasses.LAZY_LOCAL_VAL
import scala.tools.eclipse.properties.syntaxcolouring.ScalaSyntaxClasses.LAZY_TEMPLATE_VAL
import scala.tools.eclipse.properties.syntaxcolouring.ScalaSyntaxClasses.LOCAL_VAL
import scala.tools.eclipse.properties.syntaxcolouring.ScalaSyntaxClasses.LOCAL_VAR
import scala.tools.eclipse.properties.syntaxcolouring.ScalaSyntaxClasses.METHOD
import scala.tools.eclipse.properties.syntaxcolouring.ScalaSyntaxClasses.OBJECT
import scala.tools.eclipse.properties.syntaxcolouring.ScalaSyntaxClasses.PACKAGE
import scala.tools.eclipse.properties.syntaxcolouring.ScalaSyntaxClasses.PARAM
import scala.tools.eclipse.properties.syntaxcolouring.ScalaSyntaxClasses.TEMPLATE_VAL
import scala.tools.eclipse.properties.syntaxcolouring.ScalaSyntaxClasses.TEMPLATE_VAR
import scala.tools.eclipse.properties.syntaxcolouring.ScalaSyntaxClasses.TRAIT
import scala.tools.eclipse.properties.syntaxcolouring.ScalaSyntaxClasses.TYPE
import scala.tools.eclipse.properties.syntaxcolouring.ScalaSyntaxClasses.TYPE_PARAMETER
import scala.tools.eclipse.semantichighlighting.Preferences
import scala.tools.eclipse.semantichighlighting.classifier.SymbolInfo
import scala.tools.eclipse.semantichighlighting.classifier.SymbolType
import scala.tools.eclipse.semantichighlighting.classifier.SymbolTypes.Annotation
import scala.tools.eclipse.semantichighlighting.classifier.SymbolTypes.CaseClass
import scala.tools.eclipse.semantichighlighting.classifier.SymbolTypes.CaseObject
import scala.tools.eclipse.semantichighlighting.classifier.SymbolTypes.Class
import scala.tools.eclipse.semantichighlighting.classifier.SymbolTypes.LazyLocalVal
import scala.tools.eclipse.semantichighlighting.classifier.SymbolTypes.LazyTemplateVal
import scala.tools.eclipse.semantichighlighting.classifier.SymbolTypes.LocalVal
import scala.tools.eclipse.semantichighlighting.classifier.SymbolTypes.LocalVar
import scala.tools.eclipse.semantichighlighting.classifier.SymbolTypes.Method
import scala.tools.eclipse.semantichighlighting.classifier.SymbolTypes.Object
import scala.tools.eclipse.semantichighlighting.classifier.SymbolTypes.Package
import scala.tools.eclipse.semantichighlighting.classifier.SymbolTypes.Param
import scala.tools.eclipse.semantichighlighting.classifier.SymbolTypes.TemplateVal
import scala.tools.eclipse.semantichighlighting.classifier.SymbolTypes.TemplateVar
import scala.tools.eclipse.semantichighlighting.classifier.SymbolTypes.Trait
import scala.tools.eclipse.semantichighlighting.classifier.SymbolTypes.Type
import scala.tools.eclipse.semantichighlighting.classifier.SymbolTypes.TypeParameter

import org.eclipse.jface.preference.IPreferenceStore
import org.eclipse.jface.text.Position
import org.eclipse.jface.text.TextAttribute
import org.eclipse.swt.SWT
import org.eclipse.swt.custom.StyleRange

/** Represents a colored position in the editor.
  *
  * This class is thread-safe. Thread-safety is ensured by synchronizing accesses to all `Position`'s members. This is 
  * needed because an instance of this class is usually shared across different threads, and hence atomicity of operations 
  * must be ensured. 
  */
private class HighlightedPosition(
  offset: Int,
  length: Int,
  private val style: HighlightedPosition.HighlightingStyle) extends Position(offset, length) {

  /** Lock used to protect concurrent access to `this` instance.*/
  private val lock: AnyRef = new Object

  override def hashCode(): Int = lock.synchronized { super.hashCode() }

  override def delete(): Unit = lock.synchronized { super.delete() }

  override def undelete(): Unit = lock.synchronized { super.undelete() }

  override def equals(that: Any): Boolean = that match {
    // This implementation of `equals` is NOT symmetric.
    case that: HighlightedPosition =>
      lock.synchronized { style == that.style && this.isDeleted() == that.isDeleted() && super.equals(that) }
    case _ => false
  }

  override def getLength(): Int = lock.synchronized { super.getLength() }

  override def getOffset(): Int = lock.synchronized { super.getOffset() }

  override def includes(index: Int): Boolean = lock.synchronized { super.includes(index) }

  override def overlapsWith(rangeOffset: Int, rangeLength: Int): Boolean = lock.synchronized { super.overlapsWith(rangeOffset, rangeLength) }

  override def isDeleted(): Boolean = lock.synchronized { super.isDeleted() }

  override def setLength(length: Int): Unit = lock.synchronized { super.setLength(length) }

  override def setOffset(offset: Int): Unit = lock.synchronized { super.setOffset(offset) }

  override def toString(): String = lock.synchronized { super.toString() }

  private def getOffsetAndLenght(): (Int, Int) = lock.synchronized { (getOffset, getLength) }

  def createStyleRange: StyleRange = {
    val (offset, length) = getOffsetAndLenght()
    val textAttribute = style.textAttribute
    val s = textAttribute.getStyle()
    val fontStyle = s & (SWT.ITALIC | SWT.BOLD | SWT.NORMAL)
    val styleRange = new StyleRange(offset, length, textAttribute.getForeground(), textAttribute.getBackground(), fontStyle)
    styleRange.strikeout = (s & TextAttribute.STRIKETHROUGH) != 0
    styleRange.underline = (s & TextAttribute.UNDERLINE) != 0
    styleRange
  }
}

object HighlightedPosition {

  import org.eclipse.jface.preference.IPreferenceStore
  import scala.tools.eclipse.properties.syntaxcolouring.ScalaSyntaxClass
  import scala.tools.eclipse.properties.syntaxcolouring.ScalaSyntaxClasses
  import scala.tools.eclipse.semantichighlighting.classifier.SymbolTypes

  /** Factory for `HighlightedPosition`s. */
  def apply(preferences: Preferences)(symbolInfos: List[SymbolInfo]): immutable.HashSet[Position] = {
    // Apply the same setting for all collected highlighted positions. 
    val strikethroughDeprecatedSymbols = preferences.isStrikethroughDeprecatedDecorationEnabled()

    (for {
      SymbolInfo(symbolType, regions, isDeprecated) <- symbolInfos
      region <- regions
      if region.getLength > 0
      deprecated = (isDeprecated && strikethroughDeprecatedSymbols)
      annotation = HighlightedPosition.HighlightingStyle(preferences.store, symbolType, deprecated)
    } yield new HighlightedPosition(region.getOffset, region.getLength, annotation))(collection.breakOut)
  }

  private[semantichighlighting] case class HighlightingStyle(textAttribute: TextAttribute, enabled: Boolean)

  private object HighlightingStyle {
    def apply(prefStore: IPreferenceStore, symbolType: SymbolType, deprecated: Boolean): HighlightingStyle = {
      val syntaxClass = symbolTypeToSyntaxClass(symbolType)
      var ta = syntaxClass.getTextAttribute(prefStore)
      if (deprecated)
        ta = new TextAttribute(ta.getForeground, ta.getBackground, ta.getStyle | TextAttribute.STRIKETHROUGH, ta.getFont)
      val enabled = syntaxClass.getStyleInfo(prefStore).enabled
      HighlightingStyle(ta, true)
    }

    private def symbolTypeToSyntaxClass(symbolType: SymbolType): ScalaSyntaxClass = {
      import SymbolTypes._
      import ScalaSyntaxClasses._
      symbolType match {
        case Annotation      => ANNOTATION
        case CaseClass       => CASE_CLASS
        case CaseObject      => CASE_OBJECT
        case Class           => CLASS
        case LazyLocalVal    => LAZY_LOCAL_VAL
        case LazyTemplateVal => LAZY_TEMPLATE_VAL
        case LocalVal        => LOCAL_VAL
        case LocalVar        => LOCAL_VAR
        case Method          => METHOD
        case Param           => PARAM
        case Object          => OBJECT
        case Package         => PACKAGE
        case TemplateVar     => TEMPLATE_VAR
        case TemplateVal     => TEMPLATE_VAL
        case Trait           => TRAIT
        case Type            => TYPE
        case TypeParameter   => TYPE_PARAMETER
      }
    }
  }
}