package org.scalaide.util.internal

import scala.tools.nsc.settings.ScalaVersion
import scala.tools.nsc.settings.SpecificScalaVersion

object CompilerUtils {
  object ShortScalaVersion {
    def unapply(v: ScalaVersion): Option[(Int, Int)] = v match {
      case SpecificScalaVersion(major, minor, _, _) => Some((major, minor))
      case _                                        => None
    }
  }

  def isBinarySame: (ScalaVersion, ScalaVersion) => Boolean = {
    case (ShortScalaVersion(major, minor), ShortScalaVersion(thatMajor, thatMinor)) => major == thatMajor && minor == thatMinor
    case _ => false
  }

  def isBinaryPrevious: (ScalaVersion, ScalaVersion) => Boolean = {
    case (ShortScalaVersion(major, minor), ShortScalaVersion(thatMajor, thatMinor)) => major == thatMajor && minor > thatMinor
    case _ => false
  }

  def isBinarySubsequent: (ScalaVersion, ScalaVersion) => Boolean = {
    case (ShortScalaVersion(major, minor), ShortScalaVersion(thatMajor, thatMinor)) => major == thatMajor && minor < thatMinor
    case _ => false
  }

  /**
   * String form of either full or binary ScalaVersion with an offset on the last significant position.
   */
  def versionString(s: ScalaVersion, full: Boolean, offset: Int) = s match {
    case SpecificScalaVersion(major, minor, rev, _) if full =>
      s"${major}.${minor}.${rev + offset}"
    case SpecificScalaVersion(major, minor, _, _) =>
      s"${major}.${minor + offset}"
    case _ =>
      "none"
  }

  /**
   * Short string version of the given version.
   */
  def shortString(s: ScalaVersion) = versionString(s, full = false, offset = 0)

  /**
   * Short string for the previous version of the given version.
   */
  def previousShortString(s: ScalaVersion) = versionString(s, full = false, offset = -1)

  /**
   * Short string for the subsequent version of the given version.
   */
  def subsequentShortString(s: ScalaVersion) = versionString(s, full = false, offset = +1)
}
