package scala.tools.eclipse.semantichighlighting.classifier

import SymbolTypes._
import org.junit._

class AnnotationTest extends AbstractSymbolClassifierTest {

  @Test
  def class_annotation() {
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
  def annotation_imports() {
    checkSymbolClassification("""
      import scala.annotation.tailrec
      """, """
      import scala.annotation.$CLASS$
      """,
      Map("CLASS" -> Class))
  }

  @Test
  def fully_qualified_annotation() {
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

}