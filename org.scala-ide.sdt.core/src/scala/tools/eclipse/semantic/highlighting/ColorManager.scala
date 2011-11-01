package scala.tools.eclipse
package semantic.highlighting

import org.eclipse.swt.graphics.Color
import org.eclipse.swt.graphics.RGB
import org.eclipse.swt.widgets.Display

class ColorManager private () {

  private val fColorTable = scala.collection.mutable.Map.empty[RGB, Color];

  def getColor(rgb: RGB): Color = {
    def defaultColor = new Color(Display.getCurrent(), rgb)
    fColorTable.get(rgb).getOrElse(defaultColor)
  }

  // XXX Who calls this? --Mirko
  def dispose() {
    for (c <- fColorTable.values) c.dispose();
  }
}

object ColorManager {
  private val fgColorManager: ColorManager = new ColorManager
  def getDefault(): ColorManager = fgColorManager
}