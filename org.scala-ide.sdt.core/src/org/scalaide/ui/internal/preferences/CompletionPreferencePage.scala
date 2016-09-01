package org.scalaide.ui.internal.preferences

import scala.util.control.NonFatal
import scala.util.matching.Regex

import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer
import org.eclipse.jface.dialogs.IInputValidator
import org.eclipse.jface.dialogs.InputDialog
import org.eclipse.jface.preference.ListEditor
import org.eclipse.jface.window.Window
import org.eclipse.swt.widgets.Composite
import org.scalaide.core.IScalaPlugin
import org.scalaide.core.completion.DefaultProposalRelevanceCfg
import org.eclipse.swt.events.SelectionListener
import org.eclipse.swt.events.SelectionEvent
import org.scalaide.core.completion.ProposalRelevanceCfg

object CompletionPreferencePage {
  final val PFavoritePackages = "scala.tools.eclipse.ui.preferences.completions.favoritePackages"
  final val PPreferredPackages = "scala.tools.eclipse.ui.preferences.completions.preferredPackages"
  final val PUnpopularPackages = "scala.tools.eclipse.ui.preferences.completions.unpopularPackages"
  final val PShunnedPackages = "scala.tools.eclipse.ui.preferences.completions.shunnedPackages"

  object ProposalRelevanceCfg extends ProposalRelevanceCfg {
    private def prefStore = IScalaPlugin().getPreferenceStore
    private def strFromPrefStore(key: String) = prefStore.getString(key)

    private def rxSeqFromPrefStore(key: String) = {
      StringListMapper.decode(strFromPrefStore(key)).map(_.r)
    }

    def favoritePackages = rxSeqFromPrefStore(PFavoritePackages)
    def preferredPackages = rxSeqFromPrefStore(PPreferredPackages)
    def unpopularPackages = rxSeqFromPrefStore(PUnpopularPackages)
    def shunnedPackages = rxSeqFromPrefStore(PShunnedPackages)
  }

  private class PackageGroupListEditor(name: String, groupTitle: String, dialogTitle: String, parent: Composite)
      extends ListEditor(name, groupTitle, parent) {

    private object EditListItemOnDoubleClickListener extends SelectionListener {
      override def widgetDefaultSelected(evt: SelectionEvent): Unit = {
        val listCtrl = evt.getSource.asInstanceOf[org.eclipse.swt.widgets.List]
        val selectionInd = listCtrl.getSelectionIndex
        val newValue = getNewInputObject(listCtrl.getItem(selectionInd))

        if (newValue != null) {
          listCtrl.setItem(selectionInd, newValue)
        }
      }

      override def widgetSelected(evt: SelectionEvent) = ()
    }

    getUpButton.setVisible(false)
    getDownButton.setVisible(false)
    getList().addSelectionListener(EditListItemOnDoubleClickListener)

    protected def createList(arr: Array[String]): String = {
      StringListMapper.encode(arr)
    }

    protected def parseString(str: String): Array[String] = {
      StringListMapper.decode(str).toArray
    }

    def getNewInputObject(): String = {
      getNewInputObject("")
    }

    def getNewInputObject(initalValue: String): String = {
      val dialog = new InputDialog(getShell, dialogTitle, "Enter a regular expression", initalValue, PackageRxValidator)

      if (dialog.open() != Window.OK) {
        null
      } else {
        dialog.getValue
      }
    }
  }

  private object PackageRxValidator extends IInputValidator {
    def isValid(str: String): String = {
      if (str == null || str.trim().isEmpty()) {
        "Empty string is not allowed"
      } else {
        try {
          str.r
          null
        } catch {
          case NonFatal(e) =>
            s"Cannot compile regular expression: ${e.getMessage}"
        }
      }
    }
  }

  private class FavoritePackagesListEditor(parent: Composite)
      extends PackageGroupListEditor(PFavoritePackages, "Favorite Packages", "Favorite package", parent)

  private class PreferedPackagesListEditor(parent: Composite)
      extends PackageGroupListEditor(PPreferredPackages, "Preferred Packages", "Preferred package", parent)

  private class UnpopularPackagesListEditor(parent: Composite)
      extends PackageGroupListEditor(PUnpopularPackages, "Unpopular Packages", "Unpopular package", parent)

  private class ShunnedPackagesListEditor(parent: Composite)
      extends PackageGroupListEditor(PShunnedPackages, "Shunned Packages", "Shunned package", parent)
}

class CompletionPreferencePage extends BasicFieldEditorPreferencePage("Configure Scala Code Completion") {
  import CompletionPreferencePage._

  def createFieldEditors(): Unit = {
    addField(new FavoritePackagesListEditor(getFieldEditorParent))
    addField(new PreferedPackagesListEditor(getFieldEditorParent))
    addField(new UnpopularPackagesListEditor(getFieldEditorParent))
    addField(new ShunnedPackagesListEditor(getFieldEditorParent))
  }
}

class CompletionPreferenceInitializer extends AbstractPreferenceInitializer {
  def initializeDefaultPreferences(): Unit = {
    val store = IScalaPlugin().getPreferenceStore
    def setDefault(key: String, regexes: Seq[Regex]): Unit = {
      store.setDefault(key, StringListMapper.encode(regexes.map(_.regex)))
    }

    import CompletionPreferencePage._
    setDefault(PFavoritePackages, DefaultProposalRelevanceCfg.favoritePackages)
    setDefault(PPreferredPackages, DefaultProposalRelevanceCfg.preferredPackages)
    setDefault(PUnpopularPackages, DefaultProposalRelevanceCfg.unpopularPackages)
    setDefault(PShunnedPackages, DefaultProposalRelevanceCfg.shunnedPackages)
  }
}


