/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.features

import org.junit.Test
import org.scalaide.debug.internal.expression.BaseIntegrationTest
import org.scalaide.debug.internal.expression.BaseIntegrationTestCompanion
import org.scalaide.debug.internal.expression.Names
import org.scalaide.debug.internal.expression.TestValues

class NestedClassTest extends BaseIntegrationTest(NestedClassTest) {

  @Test
  def testParentObjectField(): Unit =
    eval("parentObjectField", "parentObjectField", Names.Java.String)

  @Test
  def testParentObjectMethod(): Unit =
    eval("parentObjectMethod", "parentObjectMethod", Names.Java.String)

  @Test
  def testParentObject2Field(): Unit =
    eval("parentObject2Field", "parentObject2Field", Names.Java.String)

  @Test
  def testParentObject2Method(): Unit =
    eval("parentObject2Method", "parentObject2Method", Names.Java.String)

  @Test
  def parentClassMethod(): Unit =
    eval("parentClassMethod", "parentClassMethod", Names.Java.String)

  @Test
  def parentClassField(): Unit =
    eval("parentClassField", "parentClassField", Names.Java.String)

}

object NestedClassTest extends BaseIntegrationTestCompanion(TestValues.NestedClassesTestCase)
