package scala.tools.eclipse.structurebuilder

object T1000568TestOracle extends BaseTestOracle {
  override protected lazy val oracle = """
Entity.scala [in t1000568 [in src [in simple-structure-builder]]]
  package t1000568
  class Entity
    Entity()
    java.util.List<t1000568.Entity> getEntities()
ExtensionTester.java [in t1000568 [in src [in simple-structure-builder]]]
  package t1000568
  class ExtensionTester
    void doSomething()
"""
}