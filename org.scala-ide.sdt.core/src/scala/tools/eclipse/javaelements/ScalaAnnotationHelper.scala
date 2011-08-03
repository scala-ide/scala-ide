package scala.tools.eclipse.javaelements

import scala.tools.eclipse.ScalaPresentationCompiler

trait ScalaAnnotationHelper { self: ScalaPresentationCompiler =>

  import self.definitions.{ TransientAttr, VolatileAttr, NativeAttr, ScalaStrictFPAttr, ThrowsClass}

  protected def hasTransientAnn(sym: Symbol) = hasAnnotation(sym, TransientAttr)
  protected def hasVolatileAnn(sym: Symbol) = hasAnnotation(sym, VolatileAttr)
  protected def hasNativeAnn(sym: Symbol) = hasAnnotation(sym, NativeAttr)
  protected def hasStrictFPAnn(sym: Symbol) = hasAnnotation(sym, ScalaStrictFPAttr)
  protected def hasThrowsAnn(sym: Symbol) = hasAnnotation(sym, ThrowsClass)

  private def hasAnnotation(sym: Symbol, annotation: Symbol) =
    sym.annotations.exists(_.atp.typeSymbolDirect == annotation)
    
  /** 
  * Only valid Java annotations have to be exposed to JDT. Specifically, Scala's annotations 
  * `@transient`, `@volatile`, `@native`, `throws`, and `@strictfp` are mapped into Java 
  * modifiers (@see ScalaJavaMapper#mapModifiers(Symbol)). Therefore, we remove them from the 
  * set of mapped annotations or JDT will fail (red markers will appear in the editor view,
  * e.g., ticket #1000469).
  * */
  protected def isInvalidJavaAnnotation(ann: AnnotationInfo) = {
    val annSym = ann.atp.typeSymbolDirect
    hasTransientAnn(annSym)  ||  
    hasVolatileAnn(annSym)   || 
    hasNativeAnn(annSym)     ||
    hasStrictFPAnn(annSym)   ||
    hasThrowsAnn(annSym)
  }
}