package org.scalaide.debug.internal.preferences

import scala.util.Try

import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer
import org.eclipse.jface.dialogs.IInputValidator
import org.eclipse.jface.dialogs.InputDialog
import org.eclipse.jface.preference.ColorFieldEditor
import org.eclipse.jface.preference.ListEditor
import org.eclipse.jface.window.Window
import org.eclipse.swt.widgets.Composite
import org.eclipse.swt.widgets.Control
import org.eclipse.swt.widgets.Display
import org.scalaide.debug.internal.ScalaDebugPlugin
import org.scalaide.ui.internal.preferences.FieldEditors

class AsyncDebuggerPreferencePage extends FieldEditors {
  import AsyncDebuggerPreferencePage._

  override def createContents(parent: Composite): Control = {
    initUnderlyingPreferenceStore(ScalaDebugPlugin.id, ScalaDebugPlugin.plugin.getPreferenceStore)
    mkMainControl(parent)(createEditors)
  }

  def createEditors(control: Composite): Unit = {
    fieldEditors += addNewFieldEditorWrappedInComposite(parent = control) { parent ⇒
      new ListEditor(FadingPackages, "Define all package names that should be faded:", parent) {

        getDownButton.setVisible(false)
        getUpButton.setVisible(false)

        allEnableDisableControls += getListControl(parent)
        allEnableDisableControls += getButtonBoxControl(parent)

        override def createList(items: Array[String]) = items.mkString(DataDelimiter)

        override def parseString(stringList: String) = stringList.split(DataDelimiter)

        override def getNewInputObject(): String = {

          val dlg = new InputDialog(
              Display.getCurrent().getActiveShell(),
              "",
              "Enter a new package name:",
              "",
              new IInputValidator {
                override def isValid(text: String) =
                  if (!text.contains(DataDelimiter))
                    null
                  else
                    "Text contains invalid characters."
              })
          if (dlg.open() == Window.OK)
            dlg.getValue()
          else
            null
        }
      }
    }

    fieldEditors += addNewFieldEditorWrappedInComposite(parent = control) { parent ⇒
      new ColorFieldEditor(FadingColor, "Color of faded packages:", parent) {
        allEnableDisableControls += getColorSelector.getButton
      }
    }

    fieldEditors += addNewFieldEditorWrappedInComposite(parent = control) { parent ⇒
      new ListEditor(AsyncProgramPoints, "Define the Async Program Points (entry points for the \"jump to next message\" functionality):", parent) {

        getDownButton.setVisible(false)
        getUpButton.setVisible(false)

        allEnableDisableControls += getListControl(parent)
        allEnableDisableControls += getButtonBoxControl(parent)

        override def createList(items: Array[String]) = items.mkString(DataDelimiter)

        override def parseString(stringList: String) = stringList.split(DataDelimiter)

        override def getNewInputObject(): String = {
          val dlg = new InputDialog(
              Display.getCurrent().getActiveShell(),
              "",
              "Enter a new Async Program Point. It consists of a comma separated" +
              " three tuple, where the first element is the class name, the second" +
              " element is the name of the method and the last element is the index" +
              " of the parameter:",
              "",
              new IInputValidator {
                override def isValid(text: String) = {
                  val elems = text.split(DataDelimiter, -1)
                  if (elems.size == 3 && elems.forall(_.nonEmpty) && !text.contains(DataDelimiter) && Try(elems(2).toInt).isSuccess)
                    null
                  else
                    "Entered value is not a three tuple or contains invalid characters."
                }
              })
          if (dlg.open() == Window.OK)
            dlg.getValue()
          else
            null
        }
      }
    }
  }

  override def useProjectSpecifcSettingsKey = UseProjectSpecificSettingsKey

  override def pageId = PageId
}

object AsyncDebuggerPreferencePage {
  val FadingPackages = "org.scalaide.debug.async.fadingPackages"
  val FadingColor = "org.scalaide.debug.async.fadingColor"
  val AsyncProgramPoints = "org.scalaide.debug.async.programPoints"
  /** The delimiter that separates the data that is entered in the UI. */
  val DataDelimiter = ";"
  val UseProjectSpecificSettingsKey = "org.scalaide.debug.async.useProjectSpecificSettings"
  val PageId = "org.scalaide.ui.preferences.debug.async"
}

class AsyncDebuggerPreferencesInitializer extends AbstractPreferenceInitializer {
  import AsyncDebuggerPreferencePage._

  override def initializeDefaultPreferences(): Unit = {
    val store = ScalaDebugPlugin.plugin.getPreferenceStore

    store.setDefault(FadingPackages, Seq("scala.", "akka.", "play.").mkString(DataDelimiter))
    store.setDefault(FadingColor, "191,191,191")

    val app = Seq(
      "scala.concurrent.Future$,apply,0",
      "scala.concurrent.package$,future,0",
      "play.api.libs.iteratee.Cont$,apply,0",
      "akka.actor.LocalActorRef,$bang,0",
      "akka.actor.RepointableActorRef,$bang,0",
      "scala.actors.InternalReplyReactor$class,$bang,1"
    ).mkString(DataDelimiter)
    store.setDefault(AsyncProgramPoints, app)
  }

}
