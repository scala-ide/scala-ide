package org.scalaide.debug.internal.expression

import org.junit.After
import org.junit.AfterClass
import org.junit.Before
import org.junit.BeforeClass

trait BeforeAfterAll {
  def beforeAll(): Unit
  def afterAll(): Unit
}

trait DefaultBeforeAfterAll extends BeforeAfterAll { self: BaseIntegrationTestCompanion =>
  @BeforeClass
  def beforeAll() = {
    setup()
  }

  @AfterClass
  def afterAll() = {
    deleteProject()
    doCleanup()
  }
}

trait BeforeAfterEach {
  def beforeEach(): Unit
  def afterEach(): Unit
}

trait DefaultBeforeAfterEach extends BeforeAfterEach { _: BaseIntegrationTest =>
  import companion._

  @Before
  def beforeEach(): Unit = {
    prepareTestDebugSession()
  }

  @After
  def afterEach(): Unit = {
    cleanDebugSession()
  }
}
