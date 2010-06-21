/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse

import org.eclipse.jface.text.{ TextPresentation, ITypedRegion }
import org.eclipse.jface.text.rules.{ DefaultDamagerRepairer, ITokenScanner }

class ScalaDamagerRepairer(scanner : ITokenScanner) extends DefaultDamagerRepairer(scanner) {
  override def createPresentation(presentation : TextPresentation, damage : ITypedRegion) : Unit =
    super.createPresentation(presentation, damage);
}
