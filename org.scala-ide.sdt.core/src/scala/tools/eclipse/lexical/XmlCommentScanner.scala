package scala.tools.eclipse.lexical
import org.eclipse.jface.text._
import org.eclipse.jface.text.rules._
import org.eclipse.jdt.ui.text.IColorManager
import scala.tools.eclipse.lexical.XmlColours._

class XmlCommentScanner(colorManager: IColorManager) extends RuleBasedScanner {

  val commentToken = new Token(new TextAttribute(colorManager.getColor(XML_COMMENT)))

  setRules(Array(new MultiLineRule("<!--", "-->", commentToken)))

}
