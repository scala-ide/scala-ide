package scala.tools.eclipse.semantichighlighting

import org.eclipse.swt.graphics.Color
import org.eclipse.swt.graphics.RGB
import org.eclipse.swt.widgets.Display
import scala.collection.mutable

class ColorManager private () {

  private val colorTable = mutable.Map.empty[RGB, Color]

  def getColor(rgb: RGB): Color =
    colorTable.get(rgb).getOrElse(new Color(Display.getCurrent, rgb))

  def dispose() {
    for (c <- colorTable.values) c.dispose()
  }
  
}

object ColorManager {
  
  lazy val colorManager: ColorManager = new ColorManager

}