package org.scalaide.core.sbtbuilder

import org.junit.Test
import org.scalaide.core.internal.project.ScalaInstallation.platformInstallation
import org.scalaide.core.internal.ScalaPlugin
import org.junit.Assert

class CompilerBridgeStoreTest {

  @Test
  def platformCompilerBridgeWorks(): Unit = {
    val store = ScalaPlugin().compilerBridgeStore
    store.purgeCache()

    Assert.assertTrue("successful compiler bridge compilation", store.compilerBridgeFor(platformInstallation)(null).isRight)
    Assert.assertEquals("Zero hits and one miss", (0, 1), store.getStats)
  }

  @Test
  def platformCompilerBridgeCachesCompilers(): Unit = {
    val store = ScalaPlugin().compilerBridgeStore
    store.purgeCache()

    Assert.assertTrue("successful compiler bridge compilation", store.compilerBridgeFor(platformInstallation)(null).isRight)
    Assert.assertTrue("Second try successful", store.compilerBridgeFor(platformInstallation)(null).isRight)
    Assert.assertEquals("One hit and one miss", (1, 1), store.getStats)
  }
}
