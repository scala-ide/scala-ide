package scala.tools.eclipse.launching

import org.eclipse.jdt.junit.launcher.JUnitLaunchShortcut
import org.eclipse.jdt.core.IJavaElement
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy
import org.eclipse.jdt.internal.junit.launcher.JUnitLaunchConfigurationConstants

/** A `Run As Scala JUnit Test` shortcut. The only thing that we need to change compared to
 *  the plain Java JUnit shortcut is the test runner kind. We introduced a new test kind,
 *  similar to the JDT 'JUnit4' and 'JUnit3' test kinds, whose sole responsibility is to
 *  locate tests.
 *
 *  @see the `internal_testkinds` extension point.
 *
 */
class ScalaJUnitLaunchShortcut extends JUnitLaunchShortcut {

  /** Add the Scala JUnit test kind to the configuration.. */
  override def createLaunchConfiguration(element: IJavaElement): ILaunchConfigurationWorkingCopy = {
    val conf = super.createLaunchConfiguration(element)

    conf.setAttribute(JUnitLaunchConfigurationConstants.ATTR_TEST_RUNNER_KIND, ScalaJUnitLaunchShortcut.SCALA_JUNIT_TEST_KIND)
    conf
  }

  /** We need to force the creation of a new launch configuration if the test kind is different, otherwise
   *  the plain JDT test finder would be run, and possibly miss tests.
   */
  override def getAttributeNamesToCompare(): Array[String] = {
    super.getAttributeNamesToCompare() :+ JUnitLaunchConfigurationConstants.ATTR_TEST_RUNNER_KIND
  }
}

object ScalaJUnitLaunchShortcut {
  /**
   *  We're using the JUnit test kind ID because of an internal test in
   *  JUnitLaunchConfigurationTab.getMethodsForType. That test kicks in
   *  when the user presses the Search button for methods inside a class,
   *  and based on the ID it filters methods by name (JUnit3-style) or by
   *  annotation (JUnit 4 style).
   */
  final val SCALA_JUNIT_TEST_KIND = "org.eclipse.jdt.junit.loader.junit4"
}
