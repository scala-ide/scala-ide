package org.scalaide.ui.wizards

import org.junit.runner.RunWith
import org.junit.runners.Suite

@RunWith(classOf[Suite])
@Suite.SuiteClasses(Array(
  classOf[ScalaFileCreatorInitialPathTest],
  classOf[ScalaFileCreatorValidationTest],
  classOf[ScalaFileCreatorTemplateVariablesTest]
))
class WizardTests