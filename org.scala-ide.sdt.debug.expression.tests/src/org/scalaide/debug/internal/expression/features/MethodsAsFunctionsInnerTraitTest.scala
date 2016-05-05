/*
 * Copyright (c) 2015 Contributor. All rights reserved.
*/
package org.scalaide.debug.internal.expression.features

import org.junit.Ignore
import org.scalaide.debug.internal.expression.BaseIntegrationTest
import org.scalaide.debug.internal.expression.BaseIntegrationTestCompanion
import org.scalaide.debug.internal.expression.DefaultBeforeAfterAll
import org.scalaide.debug.internal.expression.TestValues.MethodsAsFunctions.MethodsAsFunctionsInnerTraitTestCase
import org.scalaide.debug.internal.expression.DefaultBeforeAfterEach

@Ignore("Fails with 'Different class symbols have the same bytecode-level internal name'")
class MethodsAsFunctionsInnerTraitTest extends BaseIntegrationTest(MethodsAsFunctionsInnerTraitTest) with MethodsAsFunctionsTest with DefaultBeforeAfterEach
object MethodsAsFunctionsInnerTraitTest extends BaseIntegrationTestCompanion(MethodsAsFunctionsInnerTraitTestCase) with DefaultBeforeAfterAll
