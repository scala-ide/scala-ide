/*
 * Copyright (c) 2015 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.remote

import org.junit.BeforeClass
import org.junit.AfterClass
import org.scalaide.debug.internal.expression.EclipseProjectContext

/** Mix this trait to make your test class run in remote context. */
trait RemoteTestCase {
  @BeforeClass
  def setRemoteOn(): Unit = {
    EclipseProjectContext.isRemote = true
  }

  @AfterClass
  def setRemoteOff(): Unit = {
    EclipseProjectContext.isRemote = false
  }
}