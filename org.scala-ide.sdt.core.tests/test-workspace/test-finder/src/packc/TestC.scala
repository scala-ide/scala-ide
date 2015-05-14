package packc

import org.junit.Test

class TestC extends packa.TestA1 {
  @Test
  def derivedTestMethod1(): Unit = {}
}

trait T1 {
  @Test
  def traitTestMethod1(): Unit = {}
}

abstract class ATest1 {
  @Test
  def abstractClassTestMethod1(): Unit = {}
}

class TestCInherited1 extends T1 {}
class TestCInherited2 extends ATest1 {}