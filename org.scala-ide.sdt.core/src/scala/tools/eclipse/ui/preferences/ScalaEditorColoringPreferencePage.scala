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
	setDescription("Set the highlighting for implicit conversions and implicit parameters.");
	
	
	/**
	 * Creates the field editors. Field editors are abstractions of
	 * the common GUI blocks needed to manipulate various types
	 * of preferences. Each field editor knows how to save and
	 * restore itself.
	 */
	override def createFieldEditors() {
		addField(
				new ColorFieldEditor(P_COLOR, 
				"Color:", getFieldEditorParent()));
		addField(
				new BooleanFieldEditor(P_BLOD,
				"Blod",
				getFieldEditorParent()));
		addField(
				new BooleanFieldEditor(P_ITALIC,
				"Italic",
				getFieldEditorParent()));
		val entryNamesAndValues = new Array[Array[String]](4, 2)
		entryNamesAndValues(0)(0)="Squiggle"
		entryNamesAndValues(0)(1)="1"
		entryNamesAndValues(1)(0)="Double"
		entryNamesAndValues(1)(1)="2"	
		entryNamesAndValues(2)(0)="Single"
		entryNamesAndValues(2)(1)="3"
		entryNamesAndValues(3)(0)="None"
		entryNamesAndValues(3)(1)="4"
		addField(
				new ComboFieldEditor(P_UNDERLINE,
				"UnderLine Style",
				entryNamesAndValues,
				getFieldEditorParent())); 
	}

	/* (non-Javadoc)
	 * @see org.eclipse.ui.IWorkbenchPreferencePage#init(org.eclipse.ui.IWorkbench)
	 */
	override def init(workbench: IWorkbench) {
	}
}


object ScalaEditorColoringPreferencePage {
	val P_COLOR = "colorPreference";
	val P_BLOD = "blodPreference"; 
	val P_ITALIC= "italicPreference";
	val P_UNDERLINE = "underLinePreference";
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
		val green = new RGB(0, 255, 0);
		PreferenceConverter.setDefault(store,P_COLOR, green); 
		store.setDefault(P_BLOD, true);
		store.setDefault(P_ITALIC, true);
		store.setDefault(P_UNDERLINE, 1);
	}

}

