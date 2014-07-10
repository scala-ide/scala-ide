package org.scalaide.ui.internal.migration

import scala.util.Failure
import scala.util.Try

import org.eclipse.core.runtime.IExtensionPoint
import org.eclipse.core.runtime.dynamichelpers.ExtensionTracker
import org.eclipse.ui.PlatformUI
import org.eclipse.ui.internal.WorkbenchPlugin
import org.eclipse.ui.internal.registry.WizardsRegistryReader
import org.eclipse.ui.internal.wizards.AbstractExtensionWizardRegistry
import org.eclipse.ui.wizards.IWizardRegistry
import org.scalaide.logging.HasLogger
import org.scalaide.ui.internal.wizards.FileCreatorMapping
import org.scalaide.ui.internal.wizards.ScalaWizardElement
import org.scalaide.util.internal.ReflectAccess
import org.scalaide.util.internal.eclipse.EclipseUtils

/**
 * Performs tasks that need to extend the Eclipse based registries. Normally the
 * registry is extended by the configuration defined in the `plugin.xml` files
 * of the loaded bundles. But sometimes it is necessary to intervene this
 * process, which should be done in this class if needed. This may be
 * necessary in cases when XML configuration files are not enough to define
 * the behavior of the IDE or when behavior defined by other bundles needs to
 * be touched in order to allow a seamless interoperability with the IDE.
 *
 * The `perform` method of this class needs to be called as fast as possible, in
 * the best case directly after the registry is created. If it is calld too late
 * it may be possible that other bundles already loaded some values from the
 * registry and therefore may not notice changes afterwards done to the
 * registry.
 *
 * '''Note''': Because the changed registry entries may be used by UI
 * components, the `perform` method should only be called in a non headless
 * environment.
 */
class RegistryExtender extends AnyRef with HasLogger {

  val ScalaWizardCategory = "org.scala-ide.sdt.core.wizards"

  /**
   * Performs any changes to the registry. This method is guaranteed to not
   * throw any exceptions.
   */
  def perform(): Unit = {
    try unsafePerform() catch {
      case e: Exception =>
        logger.error("Exception occured while performing registry extender", e)
    }
  }

  private def unsafePerform(): Unit = {
    def extendWizardRegistry(w: IWizardRegistry) = w match {
      case reg: AbstractExtensionWizardRegistry =>
        injectWizardElements(reg) match {
          case Failure(f) => logger.error(f)
          case _ =>
        }
      case reg =>
        logger.error(s"Internal representation of wizard registry has changed to '${reg.getClass().getName()}'")
    }

    extendWizardRegistry(WorkbenchPlugin.getDefault().getNewWizardRegistry())
  }

  /**
   * Creates wizard elements and injects them into the given wizard registry.
   * The created elements contain on the one side all wizards of type
   * [[org.eclipse.ui.INewWizard]] that are defined in the plugin.xml files of
   * the loaded bundles and on the other side all additional wizards of the
   * Scala IDE, which are of type [[org.scalaide.ui.wizards.FileCreator]].
   *
   * The latter are not compatible to the `INewWizard` interface, therefore they
   * are wrapped by an adapter, which makes sure that they are correctly called.
   *
   * This method is entirely based on reflection accesses because there is no
   * public API available for the wizard registry. If a reflection call goes
   * wrong this method throws an exception.
   *
   * The implementation of this method is meant to replace a call to
   * [[org.eclipse.ui.internal.wizards.AbstractExtensionWizardRegistry#doInitialize]].
   * If this method succeeds correctly the `doInitialize` method must not be
   * called, otherwise the injected Scala wizards may be overwritten. In
   * contrast, if an exception is thrown the `doInitialize` method needs to be
   * called on the regular way, otherwise we risk unusable wizards after the
   * startup of the platform.
   */
  private def injectWizardElements(reg: AbstractExtensionWizardRegistry): Try[Unit] =
    ReflectAccess(reg) apply { ra =>
      val filter = ra.getExtensionPointFilter[IExtensionPoint]()
      val plugin = ra.getPlugin[String]()
      val ext = ra.getExtensionPoint[String]()

      PlatformUI.getWorkbench().getExtensionTracker().registerHandler(
          ra.obj,
          ExtensionTracker.createExtensionPointFilter(filter))

      val reader = new WizardsRegistryReader(plugin, ext)
      val elems = reader.getWizardElements()

      Option(elems.findCategory(ScalaWizardCategory)) foreach { category =>
        val configElems = EclipseUtils.configElementsForExtension(FileCreatorMapping.FileCreatorId)
        val configElem = configElems.find(_.getAttribute("id") == "org.scalaide.ui.wizards.scalaCreator")
        configElem foreach (category add new ScalaWizardElement(_))
      }

      ra.setWizardElements[Unit](elems)
      ra.setPrimaryWizards[Unit](reader.getPrimaryWizards())
      ra.registerWizards[Unit](elems)
      ra.initialized = true

      logger.debug("Injection of Scala wizard elements was successful")
    }

}