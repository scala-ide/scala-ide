package scala.tools.eclipse.ui.preferences;

import org.eclipse.jface.preference._;
import org.eclipse.ui.IWorkbenchPreferencePage;
import org.eclipse.ui.IWorkbench;

import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer;
import org.eclipse.jface.preference.IPreferenceStore;

import org.eclipse.swt.graphics.RGB;

import scala.tools.eclipse.ScalaPlugin;


class ScalaEditorColoringPreferencePage extends FieldEditorPreferencePage
	with IWorkbenchPreferencePage {
	import ScalaEditorColoringPreferencePage._

	setPreferenceStore(ScalaPlugin.plugin.getPreferenceStore());
	setDescription("""
	    Set the highlighting for implicit conversions and implicit parameters.
	    Underline configuration, Set at General > Editors > Text Editors > Annotations (Scala Implicit)
  """)
	
	
	/**
	 * Creates the field editors. Field editors are abstractions of
	 * the common GUI blocks needed to manipulate various types
	 * of preferences. Each field editor knows how to save and
	 * restore itself.
	 */
	override def createFieldEditors() {
		addField(
				new BooleanFieldEditor(P_BLOD,
				"Blod",
				getFieldEditorParent()));
		addField(
				new BooleanFieldEditor(P_ITALIC,
				"Italic",
				getFieldEditorParent()));
	}

	/* (non-Javadoc)
	 * @see org.eclipse.ui.IWorkbenchPreferencePage#init(org.eclipse.ui.IWorkbench)
	 */
	override def init(workbench: IWorkbench) {
	}
}


object ScalaEditorColoringPreferencePage {
  val BASE = "scala.tools.eclipse.ui.preferences.implicit."
	val P_BLOD = BASE + "text.blod"
	val P_ITALIC= BASE + "text.italic"
}



/**
 * Class used to initialize default preference values.
 */
class ScalaEditorColoringPagePreferenceInitializer extends AbstractPreferenceInitializer {
	
	import ScalaEditorColoringPreferencePage._

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer#initializeDefaultPreferences()
	 */
	override def initializeDefaultPreferences() {
		val store = ScalaPlugin.plugin.getPreferenceStore();
		store.setDefault(P_BLOD, true);
		store.setDefault(P_ITALIC, true);
	}

}

