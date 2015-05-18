package org.scalaide.core
package semantichighlighting.classifier

import org.scalaide.core.internal.decorators.semantichighlighting.classifier.SymbolTypes._
import org.junit._

class AnnotationTest extends AbstractSymbolClassifierTest {

  @Test
  def class_annotation(): Unit = {
    checkSymbolClassification("""
      @Deprecated
      class A
      """, """
      @$ ANNOT  $
      class A
      """,
      Map("ANNOT" -> Annotation))
  }

  @Test
  def annotation_imports(): Unit = {
    checkSymbolClassification("""
      import scala.annotation.tailrec
      """, """
      import scala.annotation.$CLASS$
      """,
      Map("CLASS" -> Class))
  }

  @Test
  def fully_qualified_annotation(): Unit = {
    checkSymbolClassification("""
      @scala.annotation.tailrec
      class X
      """, """
      @$PKG$.$   PKG  $.$ANNOT$
      class X
      """,
      Map(
        "ANNOT" -> Annotation,
        "PKG" -> Package))
  }


  @Test
  @Ignore("does not work until presentation compiler stores more information in the AST (ticket #1001352)")
  def annotated_type(): Unit = {
    checkSymbolClassification("""
      trait X {
        def f[TPE](a: TPE): TPE @ annotation.unchecked.uncheckedVariance
      }
      """, """
      trait X {
        def f[$T$](a: $T$): $T$ @ annotation.unchecked.$ANNOT          $
      }
      """,
      Map("T" -> Type, "ANNOT" -> Annotation))
  }

}
