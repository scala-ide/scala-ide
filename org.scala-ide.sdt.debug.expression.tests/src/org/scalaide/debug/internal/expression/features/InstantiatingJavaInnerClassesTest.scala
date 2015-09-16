/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.features

import org.junit.Ignore
import org.junit.Test
import org.scalaide.debug.internal.expression.BaseIntegrationTest
import org.scalaide.debug.internal.expression.BaseIntegrationTestCompanion
import org.scalaide.debug.internal.expression.TestValues

class InstantiatingJavaInnerClassesTest extends BaseIntegrationTest(InstantiatingJavaInnerClassesTest) {

  @Ignore("TODO O-7235 there's toolbox error (SI-8956) -it throws exception when creating an instance of inner non-static Java class")
  @Test
  def newInstanceOfInnerClass(): Unit = {
    eval("val inst = new JavaLibClass(); new inst.InnerClass(10)",
      "InnerClass(10)", "debug.JavaLibClass$InnerClass")

    // basically generics dont't differ from non-generic things from the point of view the prepared tree
    // it's just a test whether there aren't another surprises related to Toolbox
    eval("val inst = new JavaLibClass(); new inst.InnerGenericClass[Int](10, 5)",
      "InnerGenericClass[Integer](10, 5)", "debug.JavaLibClass$InnerGenericClass")
  }

  @Ignore("TODO O-7235 there's toolbox error (SI-8956) -it throws exception when creating an instance of inner non-static Java class")
  @Test
  def newInstanceOfInnerClassOfInnerClass(): Unit = {
    eval("val inst = new JavaLibClass(); val inst2 = new inst.InnerClass(0); new inst2.InnerInInner(5)",
      "InnerInInner(5)", "debug.JavaLibClass$InnerClass$InnerInInner")
  }

  @Test
  def newInstanceOfInnerStaticClass(): Unit = {
    eval("new JavaLibClass.InnerStaticClass(7)", "InnerStaticClass(7)", "debug.JavaLibClass$InnerStaticClass")
    eval("new JavaLibClass.InnerStaticGenericClass[String](7, \"foo\")",
      "InnerStaticGenericClass[String](7, foo)", "debug.JavaLibClass$InnerStaticGenericClass")
  }

  @Ignore("TODO O-7235 there's toolbox error (SI-8956) -it throws exception when creating an instance of inner non-static Java class")
  @Test
  def newInstanceOfInnerClassOfInnerStaticClass(): Unit = {
    eval("val inst = new JavaLibClass.InnerStaticClass(0); new inst.InnerInStatic(11)",
      "InnerInStatic(11)", "debug.JavaLibClass$InnerStaticClass$InnerInStatic")
    eval("val inst = new JavaLibClass.InnerStaticGenericClass(0, 'a'); new inst.InnerGenericInStaticGeneric(11, 5)",
      "InnerGenericInStaticGeneric[Integer](11, 5)",
      "debug.JavaLibClass$InnerStaticGenericClass$InnerGenericInStaticGeneric")
  }

  @Test
  def newInstanceOfInnerStaticClassOfInnerStaticClass(): Unit = {
    eval("new JavaLibClass.InnerStaticClass.InnerStaticInStatic(14 + 2)",
      "InnerStaticInStatic(16)", "debug.JavaLibClass$InnerStaticClass$InnerStaticInStatic")
    eval("new JavaLibClass.InnerStaticGenericClass.InnerStaticGenericInStaticGeneric(1, 6)",
      "InnerStaticGenericInStaticGeneric[Integer](1, 6)",
      "debug.JavaLibClass$InnerStaticGenericClass$InnerStaticGenericInStaticGeneric")
  }

}

object InstantiatingJavaInnerClassesTest extends BaseIntegrationTestCompanion(TestValues.JavaTestCase)
