package org.scalaide.core.internal.lexical

import org.eclipse.jface.text.rules._
import org.scalaide.ui.syntax.ScalaSyntaxClasses._
import org.eclipse.jface.util.PropertyChangeEvent
import org.eclipse.jface.preference.IPreferenceStore

class XmlPIScanner(val preferenceStore: IPreferenceStore) extends RuleBasedScanner with AbstractScalaScanner {

  setRules(Array(new MultiLineRule("<?", "?>", getToken(XML_PI))))

}
