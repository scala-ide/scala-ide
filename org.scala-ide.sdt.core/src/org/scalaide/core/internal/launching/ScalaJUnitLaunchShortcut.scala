package org.scalaide.core.internal.launching

import org.eclipse.core.runtime.CoreException
import org.eclipse.core.runtime.NullProgressMonitor
import org.eclipse.debug.core.DebugPlugin
import org.eclipse.debug.core.ILaunchConfiguration
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy
import org.eclipse.debug.core.ILaunchManager
import org.eclipse.debug.ui.DebugUITools
import org.eclipse.jdt.core.IJavaElement
import org.eclipse.jdt.core.IType
import org.eclipse.jdt.internal.junit.launcher.JUnitLaunchConfigurationConstants
import org.eclipse.jdt.internal.junit.ui.JUnitMessages
import org.eclipse.jdt.internal.junit.ui.JUnitPlugin
import org.eclipse.jdt.junit.launcher.JUnitLaunchShortcut
import org.eclipse.jdt.ui.JavaUI
import org.eclipse.jface.viewers.ISelection
import org.eclipse.jface.viewers.IStructuredSelection
import org.eclipse.jface.window.Window
import org.eclipse.ui.IEditorPart
import org.eclipse.ui.dialogs.ElementListSelectionDialog
import org.scalaide.core.internal.jdt.model.ScalaSourceFile

/** A `Run As Scala JUnit Test` shortcut. The only thing that we need to change compared to
 *  the plain Java JUnit shortcut is the test runner kind. We introduced a new test kind,
 *  similar to the JDT 'JUnit4' and 'JUnit3' test kinds, whose sole responsibility is to
 *  locate tests.<p>
 *  Implementation Note: code of `JUnitLaunchShortcut` has been rewritten here because some
 *  of functionality (mainly selected element selection) is hidden in its private methods.
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

  /** Launch Scala Test Finder for compilation units only. In other cases drop to `super.launch(...)`. */
  override def launch(selection: ISelection, mode: String) = selection match {
    case struct: IStructuredSelection if isCompilationUnit(struct) =>
      launch(element(struct), mode).getOrElse(super.launch(struct, mode))
    case _ => super.launch(selection, mode)
  }

  /** Launch Scala Test Finder for compilation units only. In other cases drop to `super.launch(...)`. */
  override def launch(editor: IEditorPart, mode: String): Unit = {
    JavaUI.getEditorInputTypeRoot(editor.getEditorInput()) match {
      case element: ScalaSourceFile =>
        launch(Option(element), mode).getOrElse(super.launch(editor, mode))
      case _ => super.launch(editor, mode)
    }
  }

  private def element(struct: IStructuredSelection) = struct.toArray.headOption

  private def whenCompilationUnit[T, R](f: IJavaElement => R): PartialFunction[T, R] = {
    case selected: IJavaElement if IJavaElement.COMPILATION_UNIT == selected.getElementType =>
      f(selected)
  }

  private def isCompilationUnit(struct: IStructuredSelection): Boolean =
    element(struct) collect whenCompilationUnit { _ => true } getOrElse (false)

  private val testsInContainer: IJavaElement => Option[IType] = {
    import scala.collection.mutable
    val testFinder = new JUnit4TestFinder
    val progressMonitor = new NullProgressMonitor
    (selected: IJavaElement) => {
      val found = mutable.Set.empty[IType]
      testFinder.findTestsInContainer(selected, found, progressMonitor)
      found.headOption
    }
  }

  private def launch[T](element: Option[T], mode: String): Option[Unit] =
    (element collect whenCompilationUnit { testsInContainer } flatten) map {
      performLaunch(_, mode)
    } orElse {
      None
    }

  private def performLaunch(element: IJavaElement, mode: String) = {
    val temporary = createLaunchConfiguration(element)
    val config = findExistingLaunchConfiguration(temporary, mode)
    DebugUITools.launch(config.getOrElse(temporary.doSave()), mode)
  }

  private def findExistingLaunchConfiguration(temporary: ILaunchConfigurationWorkingCopy, mode: String): Option[ILaunchConfiguration] =
    findExistingLaunchConfigurations(temporary) match {
      case Nil => None
      case conf :: Nil => Option(conf)
      case configs @ _ :: _ => chooseConfiguration(configs, mode)
    }

  private def findExistingLaunchConfigurations(temporary: ILaunchConfigurationWorkingCopy): List[ILaunchConfiguration] = {
    val configType = temporary.getType()
    val configs = getLaunchManager.getLaunchConfigurations(configType).toList
    val attributeToCompare = getAttributeNamesToCompare
    configs.filter { config =>
      hasSameAttributes(config, temporary, attributeToCompare)
    }
  }

  private def hasSameAttributes(config1: ILaunchConfiguration, config2: ILaunchConfiguration, attributeToCompare: Array[String]) = {
    val EMPTY_STRING = ""
    try {
      attributeToCompare.forall { element =>
        config1.getAttribute(element, EMPTY_STRING) == config2.getAttribute(element, EMPTY_STRING)
      }
    } catch {
      case _: CoreException =>
        // ignore access problems here, return false
        false
    }
  }

  private def chooseConfiguration(configList: List[ILaunchConfiguration], mode: String) = {
    val labelProvider = DebugUITools.newDebugModelPresentation()
    val dialog = new ElementListSelectionDialog(JUnitPlugin.getActiveWorkbenchShell, labelProvider)
    dialog.setElements(configList.toArray)
    dialog.setTitle(JUnitMessages.JUnitLaunchShortcut_message_selectConfiguration)
    if (mode.equals(ILaunchManager.DEBUG_MODE)) {
      dialog.setMessage(JUnitMessages.JUnitLaunchShortcut_message_selectDebugConfiguration)
    } else {
      dialog.setMessage(JUnitMessages.JUnitLaunchShortcut_message_selectRunConfiguration)
    }
    dialog.setMultipleSelection(false)
    dialog.open() match {
      case Window.OK =>
        Option(dialog.getFirstResult.asInstanceOf[ILaunchConfiguration])
      case _ =>
        None
    }
  }

  private def getLaunchManager = {
    DebugPlugin.getDefault.getLaunchManager
  }
}

object ScalaJUnitLaunchShortcut {
  final val SCALA_JUNIT_TEST_KIND = "org.scala-ide.sdt.core.junit"
}
