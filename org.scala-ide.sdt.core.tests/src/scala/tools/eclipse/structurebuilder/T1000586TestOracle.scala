package scala.tools.eclipse.structurebuilder

object T1000586TestOracle extends BaseTestOracle {
  override protected lazy val oracle = """
Foo.scala [in t1000586 [in src [in simple-structure-builder]]]
  package t1000586
  class Foo
    Foo()
    t1000586.Foo[] getFoos()
    scala.Option<t1000586.Foo[]> getFoos2()
"""
}