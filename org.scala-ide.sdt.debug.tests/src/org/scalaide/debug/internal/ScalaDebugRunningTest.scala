/*
 * Copyright (c) 2014 Contributor. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Scala License which accompanies this distribution, and
 * is available at http://www.scala-lang.org/node/146
 */
package org.scalaide.debug.internal

import org.eclipse.core.runtime.preferences.InstanceScope
import org.eclipse.debug.core.DebugPlugin
import org.eclipse.ui.preferences.ScopedPreferenceStore

trait ScalaDebugRunningTest {

  // debug tests need this
  disableStatusHandlers()

  def disableStatusHandlers() {
    // disable UI-dependent checks done during pre-launch. Gets rid of annoying exceptions during tests
    val prefs = new ScopedPreferenceStore(InstanceScope.INSTANCE, DebugPlugin.getUniqueIdentifier);
    prefs.setValue("org.eclipse.debug.core.PREF_ENABLE_STATUS_HANDLERS", false)
  }

  val TYPENAME_FC_LS = "stepping.ForComprehensionListString"
  val TYPENAME_FC_LS2 = "stepping.ForComprehensionListString2"
  val TYPENAME_FC_LO = "stepping.ForComprehensionListObject"
  val TYPENAME_FC_LI = "stepping.ForComprehensionListInt"
  val TYPENAME_FC_LIO = "stepping.ForComprehensionListIntOptimized"
  val TYPENAME_AF_LI = "stepping.AnonFunOnListInt"
  val TYPENAME_AF_LS = "stepping.AnonFunOnListString"
  val TYPENAME_VARIABLES = "debug.Variables"
  val TYPENAME_SIMPLE_STEPPING = "stepping.SimpleStepping"
  val TYPENAME_STEP_FILTERS = "stepping.StepFilters"
  val TYPENAME_HELLOWORLD = "debug.HelloWorld"
  val TYPENAME_SAYHELLOWORLD = "debug.SayHelloWorld"
}