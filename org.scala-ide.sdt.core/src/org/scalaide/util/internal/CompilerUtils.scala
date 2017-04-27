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
   * Short string version of the given version.
   */
  def shortString(s: ScalaVersion) = s match {
    case ShortScalaVersion(major, minor) => f"$major%d.$minor%d"
    case _ => "none"
  }

  /**
   * Short string for the previous version of the given version.
   */
  def previousShortString(s: ScalaVersion) = s match {
    case ShortScalaVersion(major, minor) =>
      val lesserMinor = minor - 1
      f"$major%d.$lesserMinor%d"
    case _ =>
      "none"
  }

  /**
   * Short string for the subsequent version of the given version.
   */
  def subsequentShortString(s: ScalaVersion) = s match {
    case ShortScalaVersion(major, minor) =>
      val greaterMinor = minor + 1
      f"$major%d.$greaterMinor%d"
    case _ =>
      "none"
  }
}
