package scala.tools.eclipse

import org.eclipse.jdt.core.compiler.CharOperation

package object completion {
  def prefixMatches(name: Array[Char], prefix: Array[Char]) =
    CharOperation.prefixEquals(prefix, name, false) || CharOperation.camelCaseMatch(prefix, name)
}