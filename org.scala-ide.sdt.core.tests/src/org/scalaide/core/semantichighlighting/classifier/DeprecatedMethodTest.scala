package org.scalaide.core.semantichighlighting.classifier

import org.scalaide.core.internal.decorators.semantichighlighting.classifier.SymbolTypes._
import org.scalaide.core.internal.decorators.semantichighlighting.classifier.SymbolInfo
import org.junit._

class DeprecatedMethodTest extends AbstractSymbolClassifierTest {

  @Test
  def deprecated_method() {
    checkSymbolInfoClassification("""
      object A {
        @deprecated def deprecatedMethod() = 12
        val a = deprecatedMethod
      }""", """
      object A {
        @deprecated def $   DEP_METH   $() = 12
        val a = $   DEP_METH   $
      }""",
      Map("DEP_METH" -> SymbolInfo(Method, Nil, deprecated = true, inInterpolatedString = false)))
  }
}