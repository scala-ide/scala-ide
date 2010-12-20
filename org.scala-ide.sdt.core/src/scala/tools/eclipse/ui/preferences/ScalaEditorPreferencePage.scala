package scala.tools.eclipse.ui.preferences;

import org.eclipse.jface.preference._;
import org.eclipse.ui.IWorkbenchPreferencePage;
import org.eclipse.ui.IWorkbench;

import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer;
import org.eclipse.jface.preference.IPreferenceStore;

import scala.tools.eclipse.ScalaPlugin;

class ScalaEditorPreferencePage
	extends FieldEditorPreferencePage
	with IWorkbenchPreferencePage {

	setPreferenceStore(ScalaPlugin.plugin.getPreferenceStore());
	setDescription("Editor preference page");
	
	
	/**
	 * Creates the field editors. Field editors are abstractions of
	 * the common GUI blocks needed to manipulate various types
	 * of preferences. Each field editor knows how to save and
	 * restore itself.
	 */
	override def createFieldEditors() {
	}

	/* (non-Javadoc)
	 * @see org.eclipse.ui.IWorkbenchPreferencePage#init(org.eclipse.ui.IWorkbench)
	 */
	override def init(workbench: IWorkbench) {
	}
	
}

/**
 * Class used to initialize default preference values.
 */
class ScalaEditorPagePreferenceInitializer extends AbstractPreferenceInitializer {

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer#initializeDefaultPreferences()
	 */
	override def initializeDefaultPreferences() {
		val store = ScalaPlugin.plugin.getPreferenceStore();
	}

}

object ScalaEditorPagePreferenceConstants {
}