package scala.tools.eclipse.structurebuilder

object T1000711TestOracle extends BaseTestOracle {
  override protected lazy val oracle = """
Nested.scala [in t1000711 [in src [in simple-structure-builder]]]
  package t1000711
  class Nested$
    t1000711.Nested MODULE$
    Nested$()
    interface A
      interface A
      class B
        B()
      class C$
        t1000711.Nested.A.C MODULE$
        C$()
    class B
      B()
      interface A
      class B
        B()
      class C$
        t1000711.Nested.B.C MODULE$
        C$()
    class C$
      t1000711.Nested.C MODULE$
      C$()
      interface A
      class B
        B()
      class C$
        t1000711.Nested.C.C MODULE$
        C$()
  class Nested
    class B
      B()
      interface A
      class B
        B()
      class C$
        t1000711.Nested.B.C MODULE$
        C$()
    interface A
      interface A
      class B
        B()
      class C$
        t1000711.Nested.A.C MODULE$
        C$()
"""
}