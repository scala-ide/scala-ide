/*
 * Copyright (c) 2014 Contributor. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Scala License which accompanies this distribution, and
 * is available at http://www.scala-lang.org/node/146
 */
package org.scalaide.debug.internal

import org.junit.runners.Suite
import org.junit.runner.RunWith
import org.scalaide.debug.internal.model.ScalaThreadTest
import org.scalaide.debug.internal.model.ScalaStackFrameTest
import org.scalaide.debug.internal.model.ScalaValueTest
import org.scalaide.debug.internal.model.ScalaDebugTargetTest
import org.scalaide.debug.internal.model.DebugTargetTerminationTest
import org.scalaide.debug.internal.model.MethodClassifierUnitTest
import org.scalaide.debug.internal.model.ScalaDebugCacheTest
import org.scalaide.debug.internal.launching.RemoteConnectorTest
import org.scalaide.debug.internal.expression.ExpressionsTestSuite

/**
 * Junit test suite for the Scala debugger.
 */
@RunWith(classOf[Suite])
@Suite.SuiteClasses(
  Array(
    classOf[MethodClassifierUnitTest],
    classOf[ScalaDebugSteppingTest],
    classOf[ScalaDebugResumeTest],
    classOf[ScalaThreadTest],
//    classOf[ScalaDebugModelPresentationTest], // TODO: find where to put this test
    classOf[ScalaStackFrameTest],
    classOf[ScalaValueTest],
//    classOf[LibraryJarInBootstrapTest], // the test is not running on command line right now
    classOf[ScalaDebugTargetTest],
    classOf[ScalaDebuggerDisconnectTests],
    classOf[BaseDebuggerActorTest],
    classOf[DebugTargetTerminationTest],
    classOf[RemoteConnectorTest],
    classOf[ScalaDebugBreakpointTest],
    classOf[ScalaDebugCacheTest],
    classOf[ExpressionsTestSuite]))
class ScalaDebugTestSuite {
}
