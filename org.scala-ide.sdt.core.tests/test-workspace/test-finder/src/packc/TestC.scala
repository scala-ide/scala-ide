package packc

import org.junit.Test

class TestC extends packa.TestA1 {
  @Test
  def derivedTestMethod1() {}
}

trait T1 {
  @Test
  def traitTestMethod1() {}
}

abstract class ATest1 {
  @Test
  def abstractClassTestMethod1() {}
}

class TestCInherited1 extends T1 {}
class TestCInherited2 extends ATest1 {}