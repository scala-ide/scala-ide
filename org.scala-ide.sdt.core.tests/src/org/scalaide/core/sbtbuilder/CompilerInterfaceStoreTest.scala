package org.scalaide.core.sbtbuilder

import org.junit.Test
import org.scalaide.core.internal.project.ScalaInstallation.platformInstallation
import org.scalaide.core.internal.ScalaPlugin
import org.junit.Assert
import org.eclipse.core.runtime.Platform

class CompilerInterfaceStoreTest {

  @Test
  def platformCompilerInterfaceWorks() {
    val store = ScalaPlugin().compilerInterfaceStore
    store.purgeCache()

    Assert.assertTrue("successful compiler interface compilation", store.compilerInterfaceFor(platformInstallation)(null).isRight)
    Assert.assertEquals("Zero hits and one miss", (0, 1), store.getStats)
  }

  @Test
  def platformCompilerInterfaceCachesCompilers() {
    val store = ScalaPlugin().compilerInterfaceStore
    store.purgeCache()

    Assert.assertTrue("successful compiler interface compilation", store.compilerInterfaceFor(platformInstallation)(null).isRight)
    Assert.assertTrue("Second try successful", store.compilerInterfaceFor(platformInstallation)(null).isRight)
    Assert.assertEquals("One hit and one miss", (1, 1), store.getStats)
  }
}