/*
 * Copyright (c) 2015 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression
package remote

import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.runner.RunWith
import org.junit.runners.Suite

import org.scalaide.debug.internal.expression.features.FeaturesTestSuite

@RunWith(classOf[Suite])
@Suite.SuiteClasses(
Array(
    // Uncomment this line to perform full remote debugging test. WithoutProjectSetTest test only some basics scenarios.
    // classOf[FeaturesTestSuite],
    classOf[WithoutProjectSetTest]
))
class RemoteTestSuite

object RemoteTestSuite extends CachedEclipseProjectContext