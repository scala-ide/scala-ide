package org.scalaide.ui.internal.preferences

import org.scalaide.util.eclipse.SWTUtils.fnToSelectionAdapter
import org.eclipse.swt.widgets.Link
import org.eclipse.swt.widgets.Composite
import org.eclipse.swt.SWT
import org.eclipse.ui.dialogs.PreferencesUtil
import org.eclipse.swt.events.SelectionEvent

private object PrefPageUtils {
  def mkLink(parent: Composite, anchor: String, style: Int = SWT.None)(anchorToLinkText: String => String) = {
    val link = new Link(parent, style)
    link.setText(anchorToLinkText(anchor))
    link.addSelectionListener { e: SelectionEvent =>
      PreferencesUtil.createPreferenceDialogOn(parent.getShell, e.text, null, null)
    }
    link
  }

  def mkLinkToAnnotationsPref(parent: Composite, style: Int = SWT.None)(anchorToLinkText: String => String) = {
    mkLink(parent, """<a href="org.eclipse.ui.editors.preferencePages.Annotations">Text Editors/Annotations</a>""", style)(anchorToLinkText)
  }
}
