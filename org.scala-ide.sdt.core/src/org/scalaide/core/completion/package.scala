package org.scalaide.core

import org.eclipse.jdt.core.compiler.CharOperation

package object completion {
  def prefixMatches(name: Array[Char], prefix: Array[Char]) =
    CharOperation.prefixEquals(prefix, name, false) || CharOperation.camelCaseMatch(prefix, name)

  def exactMatches(candidate: Array[Char], name: Array[Char]) = CharOperation.equals(candidate, name)
}
