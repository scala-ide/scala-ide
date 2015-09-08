package org.scalaide.core.internal.project

/**
 * Represents one Scala installation on its compatibility to another Scala
 * installation.
 *
 * For example if the Scala installation that is shipped with Scala IDE is 2.11
 * and this installation would be compared to the installation of a project, the
 * following should be true:
 *
 * - If the project installation is also 2.11 this fact needs to be represented
 *   by [[Same]].
 * - If the project installation is 2.10 this fact needs to be represented by
 *   [[Previous]].
 * - If the project installation is 2.12 this fact needs to be represented by
 *   [[Subsequent]].
 */
sealed trait CompatibilityMode
case object Same extends CompatibilityMode
case object Previous extends CompatibilityMode
case object Subsequent extends CompatibilityMode
