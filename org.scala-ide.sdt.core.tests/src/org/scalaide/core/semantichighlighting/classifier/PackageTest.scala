package org.scalaide.core.semantichighlighting.classifier

import org.scalaide.core.internal.decorators.semantichighlighting.classifier.SymbolTypes._
import org.junit._

class PackageTest extends AbstractSymbolClassifierTest {

  @Test
  def package_decl() {
    checkSymbolClassification("""
      package packageName1.packageName2
      """, """
      package $  PACKAGE $.$  PACKAGE $""",
      Map("PACKAGE" -> Package))
  }

  @Test
  @Ignore("Packages' symbol seem to take a TransparentPosition, why?")
  def package_in_visibility_classifier() {
    checkSymbolClassification("""
      package packageName1
      protected[packageName1] class A
      """, """
      package $  PACKAGE $
      protected[$  PACKAGE $] class A
      """,
      Map("PACKAGE" -> Package))
  }

}