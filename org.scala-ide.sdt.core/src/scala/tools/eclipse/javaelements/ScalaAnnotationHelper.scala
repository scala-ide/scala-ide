package scala.tools.eclipse.javaelements

import scala.tools.eclipse.ScalaPresentationCompiler

trait ScalaAnnotationHelper { self: ScalaPresentationCompiler =>

  // The following two fiels can be removed once we drop support for 2.8.x. Simply use
  // definitions.TransientAttr and deficitions.ScalaStrictFPAttr
  private lazy val TransientAttr: Symbol = definitions.getClass("scala.transient")
  private lazy val ScalaStrictFPAttr: Symbol = {
    try {
      definitions.getClass("scala.annotation.strictfp")
    } catch {
      // this annotation does not exists on 2.8.x, therefore
      // we intercept the exception
      case e: scala.tools.nsc.MissingRequirementError =>
        NoSymbol
    }
  }

  protected def hasTransientAnn(sym: Symbol) = sym == TransientAttr
  protected def hasVolatileAnn(sym: Symbol) = sym == definitions.VolatileAttr
  protected def hasNativeAnn(sym: Symbol) = sym == definitions.NativeAttr
  protected def hasStrictFPAnn(sym: Symbol) = sym == ScalaStrictFPAttr
  protected def hasThrowsAnn(sym: Symbol) = sym == definitions.ThrowsClass

  /**
   * Only valid Java annotations have to be exposed to JDT. Specifically, Scala's annotations
   * `@transient`, `@volatile`, `@native`, `throws`, and `@strictfp` are mapped into Java
   * modifiers (@see ScalaJavaMapper#mapModifiers(Symbol)). Therefore, we remove them from the
   * set of mapped annotations or JDT will fail (red markers will appear in the editor view,
   * e.g., ticket #1000469).
   */
  protected def isInvalidJavaAnnotation(ann: AnnotationInfo) = {
    val annSym = ann.atp.typeSymbolDirect
    hasTransientAnn(annSym) ||
    hasVolatileAnn(annSym)  ||
    hasNativeAnn(annSym)    ||
    hasThrowsAnn(annSym)    ||
    hasStrictFPAnn(annSym)
  }
}