package org.scalaide.util.internal.eclipse

import org.eclipse.jface.text.IRegion

object EmptyRegion extends IRegion {
  override def getOffset: Int = 0
  override def getLength: Int = 0
}
