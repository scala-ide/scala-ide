package org.scalaide.core.semantichighlighting.classifier

import org.junit.Test
import org.scalaide.core.internal.decorators.semantichighlighting.classifier.SymbolTypes._

class DynamicTest extends AbstractSymbolClassifierTest {

  @Test
  def selectDynamic() {
    checkSymbolClassification("""
      object X {
        (new D).field
      }
      import language.dynamics
      class D extends Dynamic {
        def selectDynamic(name: String) = ???
      }
      """, """
      object X {
        (new D).$VAR$
      }
      import language.dynamics
      class D extends Dynamic {
        def selectDynamic(name: String) = ???
      }
      """,
      Map("VAR" -> TemplateVar))
  }

  @Test
  def updateDynamic() {
    checkSymbolClassification("""
      object X {
        val d = new D
        d.field = 10
        d.field
      }
      import language.dynamics
      class D extends Dynamic {
        def selectDynamic(name: String) = ???
        def updateDynamic(name: String)(value: Any) = ???
      }
      """, """
      object X {
        val d = new D
        d.$VAR$ = 10
        d.$VAR$
      }
      import language.dynamics
      class D extends Dynamic {
        def selectDynamic(name: String) = ???
        def updateDynamic(name: String)(value: Any) = ???
      }
      """,
      Map("VAR" -> TemplateVar))
  }

  @Test
  def applyDynamic() {
    checkSymbolClassification("""
      object X {
        val d = new D
        d.method(10)
        d(10)
      }
      import language.dynamics
      class D extends Dynamic {
        def applyDynamic(name: String)(value: Any) = ???
      }
      """, """
      object X {
        val d = new D
        d.$METH$(10)
        d(10)
      }
      import language.dynamics
      class D extends Dynamic {
        def applyDynamic(name: String)(value: Any) = ???
      }
      """,
      Map("METH" -> Method))
  }

  @Test
  def applyDynamicNamed() {
    checkSymbolClassification("""
      object X {
        val d = new D
        d.method(value = 10)
      }
      import language.dynamics
      class D extends Dynamic {
        def applyDynamicNamed(name: String)(value: (String, Any)) = ???
      }
      """, """
      object X {
        val d = new D
        d.$METH$($ARG$ = 10)
      }
      import language.dynamics
      class D extends Dynamic {
        def applyDynamicNamed(name: String)(value: (String, Any)) = ???
      }
      """,
      Map("METH" -> Method, "ARG" -> Param))
  }

}