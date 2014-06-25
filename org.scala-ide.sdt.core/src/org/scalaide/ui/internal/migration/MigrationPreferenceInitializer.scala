package org.scalaide.ui.internal.migration

import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer
import org.eclipse.jface.bindings.Binding
import org.eclipse.jface.bindings.keys.KeyBinding
import org.eclipse.ui.PlatformUI
import org.eclipse.ui.keys.IBindingService
import org.scalaide.core.ScalaPlugin

/**
 * The purpose of this class is to keep user defined preferences when they are
 * replaced with another preference due to future advancements of the Scala IDE.
 */
class MigrationPreferenceInitializer extends AbstractPreferenceInitializer {

  override def initializeDefaultPreferences(): Unit = {
    // do not run in an UI less environment
    if (!ScalaPlugin.plugin.headlessMode) {
      copyKeyBindings()
    }
  }

  private def copyKeyBindings() = {
    val service = PlatformUI.getWorkbench().getAdapter(classOf[IBindingService]).asInstanceOf[IBindingService]

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
          Option(b.getParameterizedCommand()).map(_.getId() == id).getOrElse(false)

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