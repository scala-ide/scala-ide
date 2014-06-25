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
      Map("VAR" -> DynamicSelect))
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
        d.$SEL$
      }
      import language.dynamics
      class D extends Dynamic {
        def selectDynamic(name: String) = ???
        def updateDynamic(name: String)(value: Any) = ???
      }
      """,
      Map("VAR" -> DynamicUpdate, "SEL" -> DynamicSelect))
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
      Map("METH" -> DynamicApply))
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
      Map("METH" -> DynamicApplyNamed, "ARG" -> Param))
  }

  @Test
  def dynamicWithTypeParameter() {
    checkSymbolClassification("""
      object X {
        val d = new D
        d.field[Int]
        d.method[Int](10)
        d.method[Int](value = 10)
      }
      import language.dynamics
      class D extends Dynamic {
        def selectDynamic[A](name: String): A = ???
        def applyDynamic[A](name: String)(value: Any): A = ???
        def applyDynamicNamed[A](name: String)(value: (String, Any)): A = ???
      }
      """, """
      object X {
        val d = new D
        d.$VAR$[$C$]
        d.$METH$[$C$](10)
        d.$DAN $[$C$](10)
      }
      import language.dynamics
      class D extends Dynamic {
        def selectDynamic[A](name: String): A = ???
        def applyDynamic[A](name: String)(value: Any): A = ???
        def applyDynamicNamed[A](name: String)(value: (String, Any)): A = ???
      }
      """,
      Map("METH" -> DynamicApply, "VAR" -> DynamicSelect, "DAN" -> DynamicApplyNamed, "C" -> Class))
  }
}