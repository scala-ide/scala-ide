package org.scalaide.ui.internal.migration

import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer
import org.eclipse.e4.ui.model.application.MApplication
import org.eclipse.e4.ui.model.application.ui.advanced.MPerspective
import org.eclipse.e4.ui.workbench.modeling.EModelService
import org.eclipse.jface.bindings.Binding
import org.eclipse.jface.bindings.keys.KeyBinding
import org.eclipse.ui.PlatformUI
import org.eclipse.ui.internal.e4.compatibility.ModeledPageLayout
import org.eclipse.ui.keys.IBindingService
import org.scalaide.core.ScalaPlugin
import org.scalaide.logging.HasLogger
import org.scalaide.util.internal.eclipse.EclipseUtils.RichWorkbench

/**
 * The purpose of this class is to keep user defined preferences when they are
 * replaced with another preference due to future advancements of the Scala IDE.
 */
class MigrationPreferenceInitializer extends AbstractPreferenceInitializer with HasLogger {

  private lazy val window = PlatformUI.getWorkbench().getActiveWorkbenchWindow()

  override def initializeDefaultPreferences(): Unit = {
    // do not run in an UI less environment
    if (!ScalaPlugin.plugin.headlessMode) {
      copyKeyBindings()
      activateNewWizardShortcut(ScalaPlugin.plugin.scalaPerspectiveId, ScalaPlugin.plugin.scalaFileCreator)
    }
  }

  /**
   * Activates a wizard entry in the "New" menu of Eclipse. There needs to be
   * passed `wizardId`, which is the ID of the wizard entry and `perspId`, which
   * is the ID of the perspective that contains the wizard entry.
   *
   * The `wizardId` is only enabled the very first time this method is called.
   * This is necessary because it is possible for users to disable wizard
   * entries manually and in such cases we don't want to enable the entry again
   * when this method is called.
   *
   * Activating a wizard entry is necessary whenever a new wizard is added to
   * the Scala IDE. Actually new wizard entries should be enabled automatically
   * by Eclipse but this doesn't work anymore in Kepler and Luna, the relevant
   * ticket is: https://bugs.eclipse.org/bugs/show_bug.cgi?id=191256
   */
  private def activateNewWizardShortcut(perspId: String, wizardId: String) = {
    val prefStore = ScalaPlugin.prefStore
    val prefId = s"org.scalaide.ui.wizardActivated_$wizardId"

    if (!prefStore.getBoolean(prefId)) {
      import collection.JavaConverters._

      val model = window.serviceOf[EModelService]
      val app = window.serviceOf[MApplication]
      val perspectives = model.findElements(app, perspId, classOf[MPerspective], null)

      perspectives.asScala.headOption foreach { persp =>
        val tags = persp.getTags().asScala
        val tag = ModeledPageLayout.NEW_WIZARD_TAG+wizardId
        val alreadyActivated = tags.contains(tag)

        if (!alreadyActivated) {
          val activated = persp.getTags().add(tag)
          if (activated) {
            prefStore.setValue(prefId, true)
          }
        }
      }
    }
  }

  private def copyKeyBindings() = {
    val service = window.serviceOf[IBindingService]

    /**
     * This copies the old preference value and set it as default value for the
     * new preference. This is only done when the new preference is not already
     * set, i.e exaclty once when a new version of the IDE is started and all
     * values are copied.
     *
     * Furthermore, the old preference value is set to an undefined state, which
     * has the effect that it isn't shown anymore in the key binding page (nor on
     * any other pages where it could be shown). Removing a command entirely
     * from the workbench seems not to be possible.
     *
     * At the moment only the migration of preferences that are related to key
     * bindings is supported.
     */
    def copyKeyBinding(oldCommandId: String, newCommandId: String): Unit = {
      def bindingsOf(id: String) = {
        def hasSameId(b: Binding) =
          Option(b.getParameterizedCommand()).exists(_.getId() == id)

        service.getBindings().filter(hasSameId)
      }

      val newBindings = bindingsOf(newCommandId)
      val userBindings = newBindings.filter(_.getType() == Binding.USER)
      val oldBindings = bindingsOf(oldCommandId).filter(_.getType() == Binding.USER)
      val allBindings = service.getBindings().filterNot(oldBindings contains _)

      val executeCopyOperation =
        newBindings.nonEmpty && userBindings.isEmpty && oldBindings.nonEmpty

      if (executeCopyOperation) {
        val migratedBindings =
          for (b <- oldBindings) yield new KeyBinding(
            b.asInstanceOf[KeyBinding].getKeySequence(),
            newBindings.head.getParameterizedCommand(),
            b.getSchemeId(),
            b.getContextId(),
            b.getLocale(),
            b.getPlatform(),
            null,
            b.getType())

        oldBindings foreach (_.getParameterizedCommand().getCommand().undefine())
        service.savePreferences(service.getActiveScheme(), allBindings ++ migratedBindings)
      }
    }

    // These values are added for the 4.0 release
    copyKeyBinding("scala.tools.eclipse.refactoring.method.command.SplitParameterLists", "org.scalaide.refactoring.SplitParameterLists")
    copyKeyBinding("scala.tools.eclipse.refactoring.method.command.MergeParameterLists", "org.scalaide.refactoring.MergeParameterLists")
    copyKeyBinding("scala.tools.eclipse.refactoring.method.command.ChangeParameterOrder", "org.scalaide.refactoring.ChangeParameterOrder")
    copyKeyBinding("scala.tools.eclipse.refactoring.command.MoveConstructorToCompanionObject", "org.scalaide.refactoring.MoveConstructorToCompanionObject")
    copyKeyBinding("scala.tools.eclipse.refactoring.command.ExtractTrait", "org.scalaide.refactoring.ExtractTrait")
    copyKeyBinding("scala.tools.eclipse.refactoring.command.MoveClass", "org.scalaide.refactoring.MoveClass")
    copyKeyBinding("scala.tools.eclipse.refactoring.command.ExtractMethod", "org.scalaide.refactoring.ExtractMethod")
    copyKeyBinding("scala.tools.eclipse.refactoring.command.ExtractLocal", "org.scalaide.refactoring.ExtractLocal")
    copyKeyBinding("scala.tools.eclipse.refactoring.command.InlineLocal", "org.scalaide.refactoring.InlineLocal")
    copyKeyBinding("scala.tools.eclipse.refactoring.command.OrganizeImports", "org.scalaide.refactoring.OrganizeImports")
    copyKeyBinding("scala.tools.eclipse.refactoring.command.Rename", "org.scalaide.refactoring.Rename")
    copyKeyBinding("scala.tools.eclipse.refactoring.command.ExpandCaseClassBinding", "org.scalaide.refactoring.ExpandCaseClassBinding")
    copyKeyBinding("scala.tools.eclipse.refactoring.commands.quickMenu", "org.scalaide.ui.menu.quickMenu")
    copyKeyBinding("scala.tools.eclipse.interpreter.RunSelection", "org.scalaide.core.handler.RunSelection")
  }
}