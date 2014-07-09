package org.scalaide.ui.internal.templates

import org.eclipse.ui.IWorkbenchPreferencePage
import org.eclipse.ui.texteditor.templates.TemplatePreferencePage
import org.scalaide.core.internal.ScalaPlugin

/**
 */
//TODO Provide a custom editor ?
//TODO support formatter
class TemplatePreferences extends TemplatePreferencePage with IWorkbenchPreferencePage {

  override def isShowFormatterSetting() = false

  setPreferenceStore(ScalaPlugin().getPreferenceStore())
  setTemplateStore(ScalaPlugin().templateManager.templateStore)
  setContextTypeRegistry(ScalaPlugin().templateManager.contextTypeRegistry)
}
