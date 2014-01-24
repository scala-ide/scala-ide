/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse.launching

import scala.tools.eclipse.javaelements.ScalaSourceFile
import scala.tools.eclipse.util.EclipseUtils.PimpedAdaptable

import org.eclipse.core.resources.IResource
import org.eclipse.core.runtime.IAdaptable
import org.eclipse.debug.core.DebugPlugin
import org.eclipse.debug.core.ILaunchConfiguration
import org.eclipse.debug.core.ILaunchConfigurationType
import org.eclipse.debug.ui.DebugUITools
import org.eclipse.jdt.core.IJavaElement
import org.eclipse.jdt.core.IType
import org.eclipse.jdt.debug.ui.launchConfigurations.JavaLaunchShortcut
import org.eclipse.jdt.internal.debug.ui.launcher.LauncherMessages
import org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants
import org.eclipse.jface.operation.IRunnableContext
import org.eclipse.jface.window.Window
import org.eclipse.ui.dialogs.ElementListSelectionDialog

/* This class can be eliminated in favour of JavaApplicationLaunch shortcut as soon as
 * the JDTs method search works correctly for Scala.
 */

/**
 * This class extends the Java Launch functionality and overrides the sections that
 * require finding the main method as well as finding and creating new launch
 * configurations.
 */
class ScalaLaunchShortcut extends JavaLaunchShortcut {

  /**
   * findTypes is the entry method that is used to find main types within a given class.
   */
  override def findTypes(elements: Array[AnyRef], context: IRunnableContext): Array[IType] = {
    if (elements == null || elements.isEmpty)
      null
    else {
      elements.flatMap(ScalaLaunchShortcut.getMainMethods).toArray
    }
  }

  /**
   * Given that the element name for a Scala module appends a $ the java based method can not be re-used.  It is
   * largely re-implemented here with the appropriate element name made available.
   */
  override def createConfiguration(t: IType): ILaunchConfiguration = {
    val launchInstanceName = t.getElementName
    val configType: ILaunchConfigurationType = getConfigurationType
    val wc = configType.newInstance(null, getLaunchManager.generateLaunchConfigurationName(launchInstanceName))
    wc.setAttribute(IJavaLaunchConfigurationConstants.ATTR_MAIN_TYPE_NAME, fullyQualifiedName(t))
    wc.setAttribute(IJavaLaunchConfigurationConstants.ATTR_PROJECT_NAME, t.getJavaProject.getElementName)
    wc.setMappedResources(Array[IResource](t.getUnderlyingResource))
    wc.doSave
  }

  /**
   * Finds and returns an <b>existing</b> configuration to re-launch for the given type,
   * or <code>null</code> if there is no existing configuration.
   *
   * @return a configuration to use for launching the given type or <code>null</code> if none
   */
  override def findLaunchConfiguration(t: IType, configType: ILaunchConfigurationType): ILaunchConfiguration = {
    findCandidates(t, configType) match {
      case Nil =>
        null
      case c :: Nil =>
        c
      case cs =>
        chooseConfiguration(cs)
    }
  }

  /** Find or create, and launch a launch configuration for the given type, in
   *  the specified mode.
   */
  override protected def launch(t: IType, mode: String) {
    val configuration = findCandidates(t, getConfigurationType()) match {
      case Nil =>
        createConfiguration(t)
      case c :: Nil =>
        c
      case cs =>
        chooseConfiguration(cs)
    }

    DebugUITools.launch(configuration, mode)
  }

  /** Find existing launch configurations which match the launch configuration type and the
   *  given class.
   */
  private def findCandidates(t: IType, configType: ILaunchConfigurationType): List[ILaunchConfiguration] = {
    // Launch configurations for the type
    val configs = DebugPlugin.getDefault.getLaunchManager.getLaunchConfigurations(configType).toList

    val projectName = t.getJavaProject.getElementName

    // filter for the class and the project
    configs.filter { launchConfig =>
      val lcTypeName: String = launchConfig.getAttribute(IJavaLaunchConfigurationConstants.ATTR_MAIN_TYPE_NAME, "")
      val lcProjectName: String = launchConfig.getAttribute(IJavaLaunchConfigurationConstants.ATTR_PROJECT_NAME, "")
      lcTypeName == fullyQualifiedName(t) && lcProjectName == projectName
    }
  }

  /**
   * Returns a configuration from the given collection of configurations that should be launched,
   * or <code>null</code> to cancel. Default implementation opens a selection dialog that allows
   * the user to choose one of the specified launch configurations.  Returns the chosen configuration,
   * or <code>null</code> if the user cancels.
   *
   * @param configList list of configurations to choose from
   * @return configuration to launch or <code>null</code> to cancel
   */
  private def chooseConfiguration(configList: List[ILaunchConfiguration]): ILaunchConfiguration = {
    import org.eclipse.ui.dialogs.ElementListSelectionDialog
    import org.eclipse.jface.window.Window
    import org.eclipse.jdt.internal.debug.ui.launcher.LauncherMessages

    val labelProvider = DebugUITools.newDebugModelPresentation
    val dialog = new ElementListSelectionDialog(getShell, labelProvider)

    dialog.setElements(configList.toArray[Object])
    dialog.setTitle(getTypeSelectionTitle)
    dialog.setMessage(LauncherMessages.JavaLaunchShortcut_2)
    dialog.setMultipleSelection(false)
    val result = dialog.open
    labelProvider.dispose

    if (result == Window.OK)
      dialog.getFirstResult.asInstanceOf[ILaunchConfiguration]
    else
      null
  }

  /**
   * Utility method to shorten the retrieval of the launch manager
   */
  private def getLaunchManager =
    DebugPlugin.getDefault.getLaunchManager

  private def fullyQualifiedName(t: IType) = {
    val nm = t.getFullyQualifiedName
    if (nm.endsWith("$"))
      nm.substring(0, nm.length - 1)
    else
      nm
  }

  /**
   * Required to have the new launch configuration be placed as a Scala Launch configuration instead of a Java
   * Launch Configuration
   */
  override def getConfigurationType =
    getLaunchManager.getLaunchConfigurationType("scala.application")

  override def getSelectionEmptyMessage =
    LauncherMessages.JavaApplicationLaunchShortcut_2

  override def getEditorEmptyMessage =
    LauncherMessages.JavaApplicationLaunchShortcut_1

  override def getTypeSelectionTitle =
    LauncherMessages.JavaApplicationLaunchShortcut_0
}

object ScalaLaunchShortcut {

  /** Return all classes that contain @Test methods. */
  def getJunitTestClasses(element: AnyRef): List[IType] = {
    val je = element.asInstanceOf[IAdaptable].getAdapter(classOf[IJavaElement]).asInstanceOf[IJavaElement]
    je.getOpenable match {
      case scu: ScalaSourceFile => JUnit4TestFinder.findTestClasses(scu)
      case _                    => Nil
    }
  }

  /** Return all objects that have an executable main method. */
  def getMainMethods(element: AnyRef): List[IType] = {
    (for {
      je <- element.asInstanceOf[IAdaptable].adaptToSafe[IJavaElement]
    } yield je.getOpenable match {
      case scu: ScalaSourceFile =>

        scu.withSourceFile { (source, comp) =>
          import comp._
          import definitions._

          def isTopLevelModule(cdef: Tree) = comp.askOption ( () =>
             cdef.isInstanceOf[ModuleDef] &&
             cdef.symbol.isModule         &&
             cdef.symbol.owner.isPackageClass
          ).getOrElse(false)

          def hasMainMethod(cdef: Tree): Boolean =
            comp.askOption { () => hasJavaMainMethod(cdef.symbol) } getOrElse false

          val response = new Response[Tree]
          comp.askParsedEntered(source, keepLoaded = false, response)

          response.get match {
            case Left(trees) =>
              for {
                cdef <- trees
                if isTopLevelModule(cdef) && hasMainMethod(cdef)
                javaElement <- comp.getJavaElement(cdef.symbol, scu.getJavaProject)
              } yield javaElement.asInstanceOf[IType]

            case Right(ex) =>
              Nil
          }

        } getOrElse Nil

      case _ => Nil
    }) getOrElse Nil
  }
}
