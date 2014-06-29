package org.scalaide.ui.wizards

import org.junit.Test
import org.junit.Assert
import org.junit.ComparisonFailure

class ScalaFileCreatorValidationTest extends ScalaFileCreator {

  val srcDirs = Seq("src", "src2")

  def validateSuccess(name: String): Unit = {
    val actual = doValidation(srcDirs, name)
    actual match {
      case Left(v) =>
        throw new AssertionError(s"'$name' is invalid: $v")
      case _ =>
    }
  }

  def validateFailure(name: String): Unit = {
    val actual = doValidation(srcDirs, name)
    if (actual.isRight)
      throw new AssertionError(s"'$name' is valid")
  }

  @Test
  def empty_file_name_is_invalid() =
    validateFailure("")

  @Test
  def file_in_default_package_is_valid() =
    validateSuccess("src/File")

  @Test
  def file_and_package_is_valid() = {
    validateSuccess("src/a.File")
    validateSuccess("src/a.b.File")
    validateSuccess("src/a.b.c.File")
  }

  @Test
  def path_with_special_sign_in_file_name_is_invalid() =
    validateFailure("src/Fil-e")

  @Test
  def path_that_ends_with_dot_is_invalid() = {
    validateFailure("src/a.")
    validateFailure("src/a.b.")
  }

  @Test
  def path_that_ends_with_slash_is_invalid() = {
    validateFailure("folder/a/")
    validateFailure("folder/a/b/")
  }

  @Test
  def package_with_special_sign_is_invalid() = {
    validateFailure("src/a.b-b.c.File")
    validateFailure("src/a.b.c.File/")
  }

  @Test
  def special_signs_in_file_names_are_valid_if_they_are_part_of_a_Scala_identifier() = {
    validateSuccess("src/***")
    validateSuccess("src/a.b.c.***")
    validateSuccess("src/a.b.c.File_***")
  }

  @Test
  def special_signs_in_file_names_are_invalid_if_they_are_not_part_of_a_Scala_identifier() = {
    validateFailure("src/A***")
    validateFailure("src/a.b.c.A***")
  }

  @Test
  def file_ending_addition_is_treated_as_file_name() =
    validateSuccess("src/a.b.c.File.scala")

  @Test
  def no_scala_keywords_allowed_in_package_notation() = {
    validateFailure("src/trait.File")
    validateFailure("src/a.trait")
    validateFailure("src/=>")
    validateFailure("src/a.>:")
  }

  @Test
  def java_keywords_are_not_allowed_as_package_name() =
    validateFailure("src/int.File")

  @Test
  def java_keywords_are_allowed_as_file_name() =
    validateSuccess("src/a.int")

  @Test
  def no_slash_allowed_in_package_notation() =
    validateFailure("src/a.b/c.File")
}