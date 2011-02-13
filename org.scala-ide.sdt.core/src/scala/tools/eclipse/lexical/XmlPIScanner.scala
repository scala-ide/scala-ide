package scala.tools.eclipse.lexical
import org.eclipse.jface.text._
import org.eclipse.jface.text.rules._
import org.eclipse.jdt.ui.text.IColorManager
import scala.tools.eclipse.lexical.XmlColours._

class XmlPIScanner(colorManager: IColorManager) extends RuleBasedScanner {

  val piToken = new Token(new TextAttribute(colorManager.getColor(XML_PI)))

  setRules(Array(new MultiLineRule("<?", "?>", piToken)))

}
