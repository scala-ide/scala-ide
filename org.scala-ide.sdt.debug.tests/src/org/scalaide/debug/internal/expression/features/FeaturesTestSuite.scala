/*
 * Copyright (c) 2014 Contributor. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Scala License which accompanies this distribution, and
 * is available at http://www.scala-lang.org/node/146
 */
package org.scalaide.debug.internal.expression.features

import org.junit.runner.RunWith
import org.junit.runners.Suite

/**
 * Junit test suite for the Scala debugger.
 */
@RunWith(classOf[Suite])
@Suite.SuiteClasses(
  Array(
    classOf[MethodArgumentsAccessTest],
    classOf[ThisTest],
    classOf[VisibilityTest],
    classOf[ExceptionsTest],
    classOf[PrimitivesIntegrationTest],
    classOf[StringAdditionTest],
    classOf[LambdasTest],
    classOf[TypedLambdasTest],
    classOf[ObjectsTest],
    classOf[ToStringTest],
    classOf[MultipleParametersListTest],
    classOf[ValAccessTest],
    classOf[VarsTest],
    classOf[NestedScopeTest],
    classOf[LiteralsTest],
    classOf[NewKeywordTest],
    classOf[ImplicitTest],
    classOf[VarargsTest],
    classOf[PartialFunctionLambdaTest],
    classOf[ControlStructuresTest]))
class FeaturesTestSuite
