/*
 * Copyright (c) 2014 Contributor. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Scala License which accompanies this distribution, and
 * is available at http://www.scala-lang.org/license.html
 */
package org.scalaide.ui.internal.preferences

import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer
import org.eclipse.jface.preference.FieldEditorPreferencePage
import org.eclipse.jface.preference.IntegerFieldEditor
import org.eclipse.swt.SWT
import org.eclipse.swt.widgets.Group
import org.eclipse.ui.IWorkbench
import org.eclipse.ui.IWorkbenchPreferencePage
import org.scalaide.core.ScalaPlugin
import org.eclipse.swt.layout.GridLayout
import org.eclipse.swt.layout.GridData
import org.eclipse.jface.preference.BooleanFieldEditor

class ScalaPreferences extends FieldEditorPreferencePage(FieldEditorPreferencePage.GRID) with IWorkbenchPreferencePage {
  import ScalaPreferences._

  setPreferenceStore(ScalaPlugin.prefStore)

  override def init(wb: IWorkbench) {}

  override def createFieldEditors(): Unit = {
    val group = new Group(getFieldEditorParent, SWT.NONE)
    group.setText("Resource Saving")
    group.setLayout(new GridLayout(1, true))
    group.setLayoutData(new GridData(SWT.FILL, SWT.DEFAULT, true, false))

    val presCompMaxIdlenessLength = new IntegerFieldEditor(PRES_COMP_MAX_IDLENESS_LENGTH, "Close unused ScalaPresentationCompiler after given amount of minutes. Set 0 to disable.", group)
    presCompMaxIdlenessLength.setValidRange(0, Integer.MAX_VALUE)
    addField(presCompMaxIdlenessLength)
    addField(new BooleanFieldEditor(PRES_COMP_CLOSE_REGARDLESS_OF_EDITORS, "Try to close unused ScalaPresentationCompiler regardless of open editors.", group))
  }

}

object ScalaPreferences {

  private val BASE = ScalaPlugin.id + "."
  private val PRESENTATION_COMPILER_BASE = BASE + "presentationCompiler."
  val PRES_COMP_MAX_IDLENESS_LENGTH = PRESENTATION_COMPILER_BASE + "maxIdlenessLength"

  /**
   * If it's set to true, unused presentation compiler's thread will be stopped for sure. But if there are still some open editors,
   * then memory could be not freed because of some still existing references.
   * Such situation occurs quite often e.g. when ImplicitHighlightingPresenter is used (implicit highlighting is enabled).
   * Looking at heap dump in profiler we can come to the conclusion that problem is here:
   * ImplicitConversionAnnotation gets in constructor certain function using HyperlinkFactory (it holds SCP instance) which is used lazily.
   * In addition in this case also ScalaProject could be not freed due to the same reason when closed.
   *
   * When working directly with SPC instances it's important to check, whether made changes don't disturb GC.
   */
  val PRES_COMP_CLOSE_REGARDLESS_OF_EDITORS = PRESENTATION_COMPILER_BASE + "closeRegardlessOfOpenEditors"
}

class ScalaPreferencesInitializer extends AbstractPreferenceInitializer {
  import ScalaPreferences._

  override def initializeDefaultPreferences() {
    val store = ScalaPlugin.prefStore
    store.setDefault(PRES_COMP_MAX_IDLENESS_LENGTH, 2)
    store.setDefault(PRES_COMP_CLOSE_REGARDLESS_OF_EDITORS, false)
  }
}