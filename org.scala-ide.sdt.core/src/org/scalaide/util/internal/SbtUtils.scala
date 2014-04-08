package org.scalaide.util.internal

import xsbti.Maybe

object SbtUtils {
  def m2o[S](opt: Maybe[S]): Option[S] = if(opt.isEmpty) None else Some(opt.get)
}