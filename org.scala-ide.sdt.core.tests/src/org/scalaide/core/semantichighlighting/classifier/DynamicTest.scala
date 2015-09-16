package org.scalaide.core.semantichighlighting.classifier

import org.junit.Test
import org.scalaide.core.internal.decorators.semantichighlighting.classifier.SymbolTypes._

class DynamicTest extends AbstractSymbolClassifierTest {

  @Test
  def selectDynamic(): Unit = {
    checkSymbolClassification("""
      object X {
        (new D).field
      }
      class D extends Dynamic {
        def selectDynamic(name: String) = ???
      }
      """, """
      object X {
        (new D).$VAR$
      }
      class D extends Dynamic {
        def selectDynamic(name: String) = ???
      }
      """,
      Map("VAR" -> DynamicSelect))
  }

  @Test
  def updateDynamic(): Unit = {
    checkSymbolClassification("""
      object X {
        val d = new D
        d.field = 10
        d.field
      }
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
      class D extends Dynamic {
        def selectDynamic(name: String) = ???
        def updateDynamic(name: String)(value: Any) = ???
      }
      """,
      Map("VAR" -> DynamicUpdate, "SEL" -> DynamicSelect))
  }

  @Test
  def applyDynamic(): Unit = {
    checkSymbolClassification("""
      object X {
        val d = new D
        d.method(10)
        d(10)
      }
      class D extends Dynamic {
        def applyDynamic(name: String)(value: Any) = ???
      }
      """, """
      object X {
        val d = new D
        d.$METH$(10)
        d(10)
      }
      class D extends Dynamic {
        def applyDynamic(name: String)(value: Any) = ???
      }
      """,
      Map("METH" -> DynamicApply))
  }

  @Test
  def applyDynamicNamed(): Unit = {
    checkSymbolClassification("""
      object X {
        val d = new D
        d.method(value = 10)
      }
      class D extends Dynamic {
        def applyDynamicNamed(name: String)(value: (String, Any)) = ???
      }
      """, """
      object X {
        val d = new D
        d.$METH$($ARG$ = 10)
      }
      class D extends Dynamic {
        def applyDynamicNamed(name: String)(value: (String, Any)) = ???
      }
      """,
      Map("METH" -> DynamicApplyNamed, "ARG" -> Param))
  }

  @Test
  def dynamicWithTypeParameter(): Unit = {
    checkSymbolClassification("""
      object X {
        val d = new D
        d.field[Int]
        d.method[Int](10)
        d.method[Int](value = 10)
      }
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
      class D extends Dynamic {
        def selectDynamic[A](name: String): A = ???
        def applyDynamic[A](name: String)(value: Any): A = ???
        def applyDynamicNamed[A](name: String)(value: (String, Any)): A = ???
      }
      """,
      Map("METH" -> DynamicApply, "VAR" -> DynamicSelect, "DAN" -> DynamicApplyNamed, "C" -> Class))
  }
}