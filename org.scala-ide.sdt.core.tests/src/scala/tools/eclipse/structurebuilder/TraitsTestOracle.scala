package scala.tools.eclipse.structurebuilder

object TraitsTestOracle extends BaseTestOracle {
  override protected lazy val oracle = """
T1.scala [in traits [in src [in simple-structure-builder]]]
  package traits
  interface T1
    scala.Nothing notImplemented()
    java.lang.Object t
    java.lang.Object t()
    scala.collection.immutable.List xs
    scala.collection.immutable.List<java.lang.Object> xs()
    scala.Nothing m(int, int)
    scala.Nothing n(int, int, int)
    java.lang.Object T
    java.lang.Object U
    java.lang.Object Z
    class Inner
      Inner()
    class InnerWithGenericParams
      scala.collection.immutable.List xs
      InnerWithGenericParams(traits.T1, scala.collection.immutable.List<java.lang.Object>)
C1.scala [in traits [in src [in simple-structure-builder]]]
  package traits
  import scala.annotation.*
  import scala.reflect.BeanProperty
  import org.junit.Test
  class C
    int _x
    traits.C.T _y
    C(int, T)
    C(T)
    int x
    int x()
    int lz
    int lz()
    java.lang.String v
    java.lang.String v()
    void v_$eq(java.lang.String)
    java.lang.Object volVar
    java.lang.Object volVar()
    void volVar_$eq(java.lang.Object)
    java.lang.String CONSTANT
    java.lang.String CONSTANT()
    int beanVal
    int beanVal()
    int getBeanVal()
    int beanVar
    int beanVar()
    void beanVar_$eq(int)
    int getBeanVar()
    void setBeanVar(int)
    boolean nullaryMethod()
    void method()
    void annotatedMethod()
    int curriedMethod(int, int)
    boolean nullaryMethod1()
    void method1()
    void method2()
    int curriedMethod1(int, int)
    class InnerC
      int x
      int x()
      InnerC(traits.C<T>, int)
    java.lang.Object T
    java.lang.Object U
    scala.runtime.Null. map(scala.Function1<scala.collection.immutable.List<java.lang.Object>,U>)
    scala.runtime.Null. takeArray(scala.collection.immutable.List<java.lang.String>[])
    scala.Null takeArray2(byte[][])
    java.lang.Object localClass(int)
      class class Object
        $anon()
        void run()
    int localMethod(int)
    long localVals(int)"""
}