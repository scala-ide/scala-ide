package org.scalaide.sbt.core

import org.junit.runner.RunWith
import org.junit.runners.Suite
import org.scalaide.sbt.core.build.BuildSbtProjectTest

@RunWith(classOf[Suite])
@Suite.SuiteClasses(
  Array(classOf[BuildSbtProjectTest])
)
class TestSuite
