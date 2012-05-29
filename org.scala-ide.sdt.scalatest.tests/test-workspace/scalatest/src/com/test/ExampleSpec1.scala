package com.test

import org.specs.Specification
import org.scalatest.WrapWith
import org.scalatest.specs.Spec1Runner

@WrapWith(classOf[Spec1Runner])
class ExampleSpec1 extends Specification {
  
  "My system" should {
    "provides basic feature 1" in {
       1 must_== 1
    }
    "provides basic feature 2" in {
          
    }
  }
  "My system also" can {
    "provides advanced feature 1" in {
          
    }
    "provides advanced feature 2" in {
      2 must_== 2
    }
  }

}