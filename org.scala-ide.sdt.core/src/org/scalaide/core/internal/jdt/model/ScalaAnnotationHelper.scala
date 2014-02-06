package org.scalaide.core.internal.jdt.model

import org.scalaide.core.compiler.ScalaPresentationCompiler

trait ScalaAnnotationHelper { self: ScalaPresentationCompiler =>

  protected def hasTransientAnn(sym: Symbol) = sym.hasAnnotation(definitions.TransientAttr)
  protected def hasVolatileAnn(sym: Symbol) = sym.hasAnnotation(definitions.VolatileAttr)
  protected def hasNativeAnn(sym: Symbol) = sym.hasAnnotation(definitions.NativeAttr)
  protected def hasStrictFPAnn(sym: Symbol) = sym.hasAnnotation(definitions.ScalaStrictFPAttr)
  protected def hasThrowsAnn(sym: Symbol) = sym.hasAnnotation(definitions.ThrowsClass)
  protected def hasDeprecatedAnn(sym: Symbol) = sym.hasAnnotation(definitions.DeprecatedAttr)

  protected def isScalaAnnotation(ann: AnnotationInfo) = {
    val isJava = ann.atp.typeSymbol.isJavaDefined
    !isJava
  }
}
