package com.test

import org.scalatest.FunSuite
import org.scalatest.FreeSpec
import org.scalatest.WordSpec

class TestingFunSuite extends FunSuite {
  test("test 1") {
    
  }
  test("test 2") {
    
  }
  test("test 3") {
    
  }
}

class Fraction(n: Int, d: Int) {
  require(d != 0)
  require(d != Integer.MIN_VALUE)
  require(n != Integer.MIN_VALUE)

  val numer = 
    if (d < 0) 
      -1 * n 
    else 
      n 
  val denom = d.abs

  override def toString = numer + " / " + denom 
}

class TestingFreeSpec extends FreeSpec {
  info("hoho")
  "A Stack" - {
    info("hello")
    "whenever it is empty" - {
      "certainly ought to" - {
        "be empty" in {
          assert(1 === 1)
        }
        "complain on peek" in {

        }
        "complain on pop" in {

        }
      }
    }
    "but when full, by contrast, must" - {
      markup("this is a markup")
      "be full" in {
        assert(1 == 1)
      }
      "complain on push" in {

      }
    }
  }
}

class StackSpec2 extends WordSpec {
  "A Stack" when {
    "empty" should {
      "be empty" in {
        // ...
      }
      "complain on peek" in {
        // ...
      }
      "complain on pop" in {
        // ...
      }
    }
    "full" should {
      "be full" in {
        // ...
      }
      "complain on push" in {
        // ...
      }
    }
  }
}