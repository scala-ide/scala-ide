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
    classOf[NamedParametersTest],
    classOf[OperatorsTest],
    classOf[AsInstanceOfTest],
    classOf[IsInstanceOfTest],
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
    classOf[MethodsAsFunctionsInnerTraitTest],
    classOf[MethodsAsFunctionsInnerClassTest],
    classOf[MethodsAsFunctionsInnerObjectTest],
    classOf[AppObjectTest],
    classOf[NestedClassTest],
    classOf[ImportsTest],
    classOf[NestedMethodsTest],
    classOf[ControlStructuresTest],
    classOf[ClassOfTest],
    classOf[SuperTest],
    classOf[ToolBoxBugsTest],
    classOf[NestedPackagesTest],
    classOf[NestedLambdaTest],
    classOf[NestedLambdaInObjectTest]))
class FeaturesTestSuite
