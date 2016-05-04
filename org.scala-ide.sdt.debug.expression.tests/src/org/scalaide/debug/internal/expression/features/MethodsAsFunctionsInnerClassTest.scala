/*
 * Copyright (c) 2015 Contributor. All rights reserved.
*/
package org.scalaide.debug.internal.expression.features

import org.scalaide.debug.internal.expression.BaseIntegrationTest
import org.scalaide.debug.internal.expression.BaseIntegrationTestCompanion
import org.scalaide.debug.internal.expression.DefaultBeforeAfterAll
import org.scalaide.debug.internal.expression.TestValues.MethodsAsFunctions.MethodsAsFunctionsInnerClassTestCase

class MethodsAsFunctionsInnerClassTest extends BaseIntegrationTest(MethodsAsFunctionsInnerClassTest) with MethodsAsFunctionsTest
object MethodsAsFunctionsInnerClassTest extends BaseIntegrationTestCompanion(MethodsAsFunctionsInnerClassTestCase) with DefaultBeforeAfterAll
