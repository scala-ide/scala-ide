/*
 * Copyright 2005-2009 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse.util

import scala.tools.nsc.Settings

object IDESettings {
  def shownSettings(s : Settings) : List[Settings.Setting]= {
    import s._
    List(
      deprecation, debuginfo, XO, target, unchecked,
      checkInit, noassertions, Xexperimental, future, XlogImplicits, nouescape, XnoVarargsConversion, pluginsDir, 
      Xcloselim, Xdce, Xdetach, inline, Xlinearizer, Ynogenericsig, noimports, nopredefs, selfInAnnots, Xsqueeze, specialize, Ytailrec,
      Xwarninit, Xchecknull, Xwarndeadcode, YwarnShadow, YwarnCatches, Xwarnings)
  }
}
