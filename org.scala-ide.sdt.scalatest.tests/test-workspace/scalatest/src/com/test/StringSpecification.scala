package com.test

import org.scalacheck.Properties
import org.scalacheck.Prop
import org.scalatest.scalacheck.ScalaCheckSuite

class StringSpecification extends Properties("String") with ScalaCheckSuite {

  property("startsWith") = Prop.forAll((a: String, b: String) => (a+b).startsWith(a))

  property("endsWith") = Prop.forAll((a: String, b: String) => (a+b).endsWith(b))

  // Is this really always true?
  property("concat") = Prop.forAll((a: String, b: String) => 
    (a+b).length > a.length && (a+b).length > b.length
  )

  property("substring 1") = Prop.forAll((a: String, b: String) => 
    (a+b).substring(a.length) == b
  )

  property("substring 2") = Prop.forAll((a: String, b: String, c: String) =>
    (a+b+c).substring(a.length, a.length+b.length) == b
  )
  
}