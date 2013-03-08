/*
 *
 */
package scala.tools.eclipse.templates

import org.eclipse.ui.IWorkbenchPreferencePage;
import org.eclipse.ui.texteditor.templates.TemplatePreferencePage;

import scala.tools.eclipse.ScalaPlugin

/**
 * @author david.bernard
 */
//TODO Provide a custom editor ?
//TODO support formatter
class TemplatePreferences extends TemplatePreferencePage with IWorkbenchPreferencePage {

  override def isShowFormatterSetting() = false

  setPreferenceStore(ScalaPlugin.prefStore)
  setTemplateStore(ScalaPlugin.plugin.templateManager.templateStore)
  setContextTypeRegistry(ScalaPlugin.plugin.templateManager.contextTypeRegistry)
}
