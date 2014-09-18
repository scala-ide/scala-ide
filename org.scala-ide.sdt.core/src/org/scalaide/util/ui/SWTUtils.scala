package org.scalaide.util.ui

import org.eclipse.swt.SWT
import org.eclipse.swt.layout.GridData

object SWTUtils {

  /** Returns a [[GridData]] configuration, with the given properties.
   *
   *  The possible values for alignment are: [[SWT.BEGINNING]], [[SWT.CENTER]], [[SWT.END]], [[SWT.FILL]]
   */
  def gridData(
    horizontalAlignment: Int = SWT.BEGINNING,
    verticalAlignment: Int = SWT.CENTER,
    grabExcessHorizontalSpace: Boolean = false,
    grabExcessVerticalSpace: Boolean = false,
    horizontalSpan: Int = 1,
    verticalSpan: Int = 1): GridData =
      new GridData(horizontalAlignment, verticalAlignment, grabExcessHorizontalSpace, grabExcessVerticalSpace, horizontalSpan, verticalSpan)

}
