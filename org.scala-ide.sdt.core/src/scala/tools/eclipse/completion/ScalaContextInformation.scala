package scala.tools.eclipse.completion

import org.eclipse.jface.text.contentassist.{IContextInformation,IContextInformationExtension}
import org.eclipse.swt.graphics.Image

private[completion] class ScalaContextInformation(
    display: String, info: String, image: Image) 
    extends IContextInformation 
    with IContextInformationExtension {
  
  def getContextDisplayString() = display
  def getImage() = image
  def getInformationDisplayString() = info
  def getContextInformationPosition(): Int = 0
}