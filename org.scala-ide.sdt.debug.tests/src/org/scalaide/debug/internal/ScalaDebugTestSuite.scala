/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal

import org.junit.runner.RunWith
import org.junit.runners.Suite
import org.scalaide.debug.internal.editor.StackFrameVariableOfTreeFinderTest
import org.scalaide.debug.internal.hcr.HotCodeReplaceTest
import org.scalaide.debug.internal.launching.LibraryJarInBootstrapTest
import org.scalaide.debug.internal.launching.RemoteConnectorTest
import org.scalaide.debug.internal.launching.ScalaApplicationLaunchConfigurationDelegateTest
import org.scalaide.debug.internal.launching.ScalaJUnitLaunchConfigurationDelegateTest
import org.scalaide.debug.internal.model.DebugTargetTerminationTest
import org.scalaide.debug.internal.model.MethodClassifierUnitTest
import org.scalaide.debug.internal.model.ScalaDebugCacheTest
import org.scalaide.debug.internal.model.ScalaDebugModelPresentationTest
import org.scalaide.debug.internal.model.ScalaDebugTargetTest
import org.scalaide.debug.internal.model.ScalaStackFrameTest
import org.scalaide.debug.internal.model.ScalaThreadTest
import org.scalaide.debug.internal.model.ScalaValueTest
import org.scalaide.debug.internal.model.ScalaJdiEventDispatcherTest

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
    classOf[ScalaDebugModelPresentationTest],
    classOf[ScalaStackFrameTest],
    classOf[ScalaValueTest],
    classOf[HotCodeReplaceTest],
    classOf[LibraryJarInBootstrapTest],
    classOf[ScalaDebugTargetTest],
    classOf[ScalaDebuggerDisconnectTests],
    classOf[DebugTargetTerminationTest],
    classOf[RemoteConnectorTest],
    classOf[ScalaDebugBreakpointTest],
    classOf[ScalaDebugCacheTest],
    classOf[StackFrameVariableOfTreeFinderTest],
    classOf[ScalaApplicationLaunchConfigurationDelegateTest],
    classOf[ScalaJUnitLaunchConfigurationDelegateTest],
    classOf[ScalaJdiEventDispatcherTest]
    ))
class ScalaDebugTestSuite
