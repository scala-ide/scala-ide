package org.scalaide.debug.internal.expression

import org.junit.BeforeClass
import org.junit.AfterClass

trait BeforeAfterAll {
  def beforeAll(): Unit
  def afterAll(): Unit
}

trait DefaultBeforeAfterAll extends BeforeAfterAll { self: BaseIntegrationTestCompanion =>
  @BeforeClass
  def beforeAll() = {
    setup()
    prepareTestDebugSession()
  }

  @AfterClass
  def afterAll() = {
    doCleanup()
  }
}
