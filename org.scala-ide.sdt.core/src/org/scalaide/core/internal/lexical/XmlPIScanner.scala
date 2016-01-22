package org.scalaide.core.internal.lexical

import org.eclipse.jface.text.rules._
import org.scalaide.ui.syntax.ScalaSyntaxClasses._
import org.eclipse.jface.preference.IPreferenceStore
import org.scalaide.core.lexical.AbstractScalaScanner

class XmlPIScanner(val preferenceStore: IPreferenceStore) extends RuleBasedScanner with AbstractScalaScanner {

  setRules(Array(new MultiLineRule("<?", "?>", getToken(XML_PI))))

}
