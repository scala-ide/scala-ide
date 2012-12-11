package scala.tools.eclipse.launching

import org.junit.runner.RunWith
import org.junit.runners.Suite

@RunWith(classOf[Suite])
@Suite.SuiteClasses(Array(
  classOf[MainMethodFinderTest],
  classOf[JUnitTestClassesFinderTest]))
class RunAsTest