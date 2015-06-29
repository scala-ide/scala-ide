/*
 * Copyright (c) 2015 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.features

import org.junit.Test
import org.scalaide.debug.internal.expression.BaseIntegrationTest
import org.scalaide.debug.internal.expression.BaseIntegrationTestCompanion
import org.scalaide.debug.internal.expression.Names.Java
import org.scalaide.debug.internal.expression.TestValues.SuperTestCase

class SuperTest extends BaseIntegrationTest(SuperTest) {

  @Test
  def normalSuperCall(): Unit = {
    eval("name", "DerivedClass", Java.String)
    eval("super.name", "BaseClass", Java.String)
  }

  @Test
  def genericSuperCall(): Unit = {
    eval("id(1)", "DerivedClass:1", Java.String)
    eval("super.id(1)", "BaseClass:1", Java.String)
  }

  @Test
  def varargSuperCall(): Unit = {
    eval("list(1, 2)", "DerivedClass:List(1, 2)", Java.String)
    eval("super.list(1, 2)", "BaseClass:List(1, 2)", Java.String)
  }

  @Test
  def varargInnerObjectSuperCall(): Unit = {
    eval("super.InnerObject.simple", "1", Java.primitives.int)
    eval("super.InnerObject.vararg(1, 2)", "BaseTrait:InnerObject:List(1, 2)", Java.String)
  }

  @Test
  def traitSuperCall(): Unit = {
    eval("super[BaseTrait1].name", "BaseTrait1", Java.String)
    eval("super[BaseTrait2].name", "BaseTrait2", Java.String)
  }

  @Test
  def traitOneParamSuperCall(): Unit = {
    eval("super[BaseTrait1].id(1)", "BaseTrait1:1", Java.String)
    eval("super[BaseTrait2].id(1)", "BaseTrait2:1", Java.String)
  }

  @Test
  def traitVarargSuperCall(): Unit = {
    eval("super[BaseTrait1].list(1, 2)", "BaseTrait1:List(1, 2)", Java.String)
    eval("super[BaseTrait2].list(1, 2)", "BaseTrait2:List(1, 2)", Java.String)
  }
}

object SuperTest extends BaseIntegrationTestCompanion(SuperTestCase)
