package scala.tools.eclipse.lexical

import org.eclipse.jface.text._
import org.eclipse.jface.text.rules._
import org.eclipse.jdt.ui.text.IColorManager
import scala.tools.eclipse.properties.syntaxcolouring.ScalaSyntaxClasses._
import org.eclipse.jface.util.PropertyChangeEvent
import org.eclipse.jface.preference.IPreferenceStore

class XmlPIScanner(val colorManager: IColorManager, val preferenceStore: IPreferenceStore) extends RuleBasedScanner with AbstractScalaScanner {

  setRules(Array(new MultiLineRule("<?", "?>", getToken(XML_PI))))

}
