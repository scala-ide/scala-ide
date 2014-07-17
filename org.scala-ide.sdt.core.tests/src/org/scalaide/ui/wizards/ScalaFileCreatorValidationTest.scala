package org.scalaide.ui.wizards

import org.junit.Test

class ScalaFileCreatorValidationTest extends ScalaFileCreator {

  def validateSuccess(name: String): Unit = {
    val actual = doValidation(name)
    actual match {
      case Left(v) =>
        throw new AssertionError(s"'$name' is invalid: $v")
      case _ =>
    }
  }

  def validateFailure(name: String): Unit = {
    val actual = doValidation(name)
    if (actual.isRight)
      throw new AssertionError(s"'$name' is valid")
  }

  @Test
  def empty_file_name_is_invalid() =
    validateFailure("")

  @Test
  def file_in_default_package_is_valid() =
    validateSuccess("File")

  @Test
  def file_and_package_is_valid() = {
    validateSuccess("a.File")
    validateSuccess("a.b.File")
    validateSuccess("a.b.c.File")
  }

  @Test
  def path_with_special_sign_in_file_name_is_invalid() =
    validateFailure("Fil-e")

  @Test
  def path_that_ends_with_dot_is_invalid() = {
    validateFailure("a.")
    validateFailure("a.b.")
  }

  @Test
  def package_with_special_sign_is_invalid() = {
    validateFailure("a.b-b.c.File")
    validateFailure("a.b.c.File/")
  }

  @Test
  def special_signs_in_file_names_are_valid_if_they_are_part_of_a_Scala_identifier() = {
    validateSuccess("***")
    validateSuccess("a.b.c.***")
    validateSuccess("a.b.c.File_***")
  }

  @Test
  def special_signs_in_file_names_are_invalid_if_they_are_not_part_of_a_Scala_identifier() = {
    validateFailure("A***")
    validateFailure("a.b.c.A***")
  }

  @Test
  def file_ending_addition_is_treated_as_file_name() =
    validateSuccess("a.b.c.File.scala")

  @Test
  def no_scala_keywords_allowed_in_package_notation() = {
    validateFailure("trait.File")
    validateFailure("a.trait")
    validateFailure("=>")
    validateFailure("a.>:")
  }

  @Test
  def java_keywords_are_not_allowed_as_package_name() =
    validateFailure("int.File")

  @Test
  def java_keywords_are_allowed_as_file_name() =
    validateSuccess("a.int")
}