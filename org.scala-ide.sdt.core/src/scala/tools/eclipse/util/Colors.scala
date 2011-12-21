/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse.util

import scala.collection.mutable.LinkedHashMap

import org.eclipse.jface.preference.PreferenceConverter
import org.eclipse.swt.widgets.Display
import org.eclipse.swt.graphics.{ Color, RGB }

object Colors {
  val noColor = null
  
  val white = rgb(255,255,255)
  val black = rgb(0, 0, 0)
  val ocean = rgb(0,62,133)
  val salmon = rgb(255,94,94)
  val cayenne = rgb(148,0,0)
  val maroon = rgb(149, 0, 64)
  val eggplant = rgb(77, 0, 134)
  val blueberry = rgb(35, 0, 251)
  val iron = rgb(76, 76, 76)
  val mocha = rgb(142, 62, 0)

  object colorMap extends LinkedHashMap[RGB, Color] {
    override def default(rgb : RGB) = {
      val ret = new Color(Display.getDefault(), rgb)
      this(rgb) = ret
      ret
    }
  }
  
  def rgb(r : Int, g : Int, b : Int) = colorMap(new RGB(r,g,b))
}
