package org.scalaide.core.resources

object ScalaMarkers {

  /**
   * Represents the full error message found by the typechecker or by the
   * builder. The full error message needs to be treated separately to a
   * truncated error message because the Problems view in Eclipse can't handle
   * multi line error messages.
   */
  final val FullErrorMessage = "fullErrorMessage"
}