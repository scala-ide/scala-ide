/*
 * Copyright (c) 2015 Contributor. All rights reserved.
*/
package org.scalaide.debug.internal.expression.features

import org.scalaide.debug.internal.expression.BaseIntegrationTest
import org.scalaide.debug.internal.expression.BaseIntegrationTestCompanion
import org.scalaide.debug.internal.expression.DefaultBeforeAfterAll
import org.scalaide.debug.internal.expression.TestValues.MethodsAsFunctions.MethodsAsFunctionsInnerClassTestCase
import org.scalaide.debug.internal.expression.DefaultBeforeAfterEach

class MethodsAsFunctionsInnerClassTest extends BaseIntegrationTest(MethodsAsFunctionsInnerClassTest) with MethodsAsFunctionsTest with DefaultBeforeAfterEach
object MethodsAsFunctionsInnerClassTest extends BaseIntegrationTestCompanion(MethodsAsFunctionsInnerClassTestCase) with DefaultBeforeAfterAll
