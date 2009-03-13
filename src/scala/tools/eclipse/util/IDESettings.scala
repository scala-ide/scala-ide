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
      argfiles, dependenciesFile, debuginfo, make, XO, target,
      Xchecknull, checkInit, noassertions, Xexperimental, future, XlogImplicits, nouescape, XnoVarargsConversion, pluginsDir, Xwarninit,
      Xcloselim, Xdce, Xdetach, inline, Xlinearizer, Ynogenericsig, noimports, nopredefs, selfInAnnots, Xwarndeadcode)
  }
}
