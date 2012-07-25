package scala.tools.eclipse.structurebuilder

object AbstractMembersTestOracle extends BaseTestOracle {
  override protected lazy val oracle = """
FooImpl.scala [in abstract_members [in src [in simple-structure-builder]]]
  package abstract_members
  class FooImpl
    FooImpl()
    java.lang.Object obj1
    java.lang.Object obj1()
    java.lang.Object obj2
    java.lang.Object obj2()
    void obj2_$eq(java.lang.Object)
    abstract_members.Foo obj3()
    java.lang.String obj4(java.lang.String)
Foo.scala [in abstract_members [in src [in simple-structure-builder]]]
  package abstract_members
  class Foo
    Foo()
    java.lang.Object obj1
    java.lang.Object obj1()
    java.lang.Object obj2
    java.lang.Object obj2()
    void obj2_$eq(java.lang.Object)
    abstract_members.Foo obj3()
    java.lang.String obj4(java.lang.String)
"""

}