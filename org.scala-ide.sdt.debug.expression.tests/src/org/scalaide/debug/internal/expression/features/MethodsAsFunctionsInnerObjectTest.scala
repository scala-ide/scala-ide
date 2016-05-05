/*
 * Copyright (c) 2015 Contributor. All rights reserved.
*/
package org.scalaide.debug.internal.expression.features

import org.scalaide.debug.internal.expression.BaseIntegrationTest
import org.scalaide.debug.internal.expression.BaseIntegrationTestCompanion
import org.scalaide.debug.internal.expression.DefaultBeforeAfterAll
import org.scalaide.debug.internal.expression.TestValues.MethodsAsFunctions.MethodsAsFunctionsInnerObjectTestCase
import org.scalaide.debug.internal.expression.DefaultBeforeAfterEach

class MethodsAsFunctionsInnerObjectTest extends BaseIntegrationTest(MethodsAsFunctionsInnerObjectTest) with MethodsAsFunctionsTest with DefaultBeforeAfterEach
object MethodsAsFunctionsInnerObjectTest extends BaseIntegrationTestCompanion(MethodsAsFunctionsInnerObjectTestCase) with DefaultBeforeAfterAll
