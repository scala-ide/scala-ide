package scala.tools.eclipse.semantichighlighting.classifier

import SymbolTypes._
import org.junit._

class TypeParameterTest extends AbstractSymbolClassifierTest {

  @Test
  def basic_type_param() {
    checkSymbolClassification("""
      trait C[TypeParam] {
        def x: TypeParam
      }""", """
      trait C[$TPARAM $] {
        def x: $TPARAM $
      }""",
      Map("TPARAM" -> TypeParameter))
  }
  
  @Test
  def method_type_param() {
    checkSymbolClassification("""
      trait X {
        def x[TypeParam]: TypeParam
      }""", """
      trait X {
        def x[$TPARAM $]: $TPARAM $
      }""",
      Map("TPARAM" -> TypeParameter))
  }
}