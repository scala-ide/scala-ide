/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse.util

import scala.tools.nsc.Settings

object IDESettings {
  def shownSettings(s : Settings) : List[Settings#Setting]= {
    import s._
    List(
      deprecation, g, optimise, target, unchecked,
      checkInit, noassertions, elidebelow, Xexperimental, future, XlogImplicits, Xmigration28, nouescape, plugin, disable, require, pluginsDir, pluginOptions,
      Xcloselim, Xdce, Xdetach, inline, Xlinearizer, Ynogenericsig, noimports, nopredefs, Yrecursion, selfInAnnots, Xsqueeze, refinementMethodDispatch, specialize, Ytailrec, Yjenkins,
      Ywarnfatal, Xwarninit, Xchecknull, Xwarndeadcode, YwarnShadow, YwarnCatches, Xwarnings)
  }
}
