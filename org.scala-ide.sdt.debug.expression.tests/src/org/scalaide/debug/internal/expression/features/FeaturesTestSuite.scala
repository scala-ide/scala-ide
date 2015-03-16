/*
 * Copyright (c) 2014 Contributor. All rights reserved.
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
    classOf[GenericsTest],
    classOf[TraitsTest],
    classOf[ArrayTest],
    classOf[MethodArgumentsAccessTest],
    classOf[ThisTest],
    classOf[VisibilityTest],
    classOf[ExceptionsTest],
    classOf[PrimitivesIntegrationTest],
    classOf[StringAdditionTest],
    classOf[LambdasTest],
    classOf[TypedLambdasTest],
    classOf[ObjectsTest],
    classOf[JavaObjectMethodsTest],
    classOf[JavaStaticFieldsAndMethodsTest],
    classOf[ModificationOfJavaStaticFieldsTest],
    classOf[JavaNonStaticFieldsAndMethodsTest],
    classOf[InstantiatingJavaInnerClassesTest],
    classOf[JavaEnumsTest],
    classOf[MultipleParametersListTest],
    classOf[ValAccessTest],
    classOf[VarsTest],
    classOf[NestedScopeTest],
    classOf[LiteralsTest],
    classOf[NewKeywordTest],
    classOf[ImplicitTest],
    classOf[VarargsTest],
    classOf[PartialFunctionLambdaTest],
    classOf[AnyValTest],
    classOf[InnerMethodsTest],
    classOf[MethodsAsFunctionsInnerTraitTest],
    classOf[MethodsAsFunctionsInnerClassTest],
    classOf[MethodsAsFunctionsInnerObjectTest],
    classOf[NamedParameterTest],
    classOf[AppObjectTest],
    classOf[NestedClassTest],
    classOf[ImportsTest],
    classOf[ControlStructuresTest]))
class FeaturesTestSuite
