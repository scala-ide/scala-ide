package org.scalaide.core.launching

import org.junit.runner.RunWith
import org.junit.runners.Suite

@RunWith(classOf[Suite])
@Suite.SuiteClasses(Array(
  classOf[MainMethodFinderTest],
  classOf[JUnit4TestFinderTest]))
class RunAsTest