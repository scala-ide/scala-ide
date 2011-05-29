package scala.tools.eclipse;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;


/**
 * To run this class DO NOT FORGET to set the config.ini in the  "configuration" tab.
 * @author ratiu
 */
@RunWith(Suite.class)
@Suite.SuiteClasses({
  ReferenceSearchTest.class,
  ScalaSourceFileEditorTest.class
})
class TestsSuite { }