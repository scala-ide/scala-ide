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
  def file_not_in_folder_is_valid() =
    validateSuccess("File")

  @Test
  def file_in_default_package_is_valid() =
    validateSuccess("src/File")

  @Test
  def file_not_in_source_folder_is_valid() =
    validateSuccess("folder/File")

  @Test
  def file_and_package_is_valid() = {
    validateSuccess("src/a.File")
    validateSuccess("src/a.b.File")
    validateSuccess("src/a.b.c.File")
  }

  @Test
  def file_and_subfolder_is_valid() = {
    validateSuccess("folder/a/File")
    validateSuccess("folder/a/b/File")
    validateSuccess("folder/a/b/c/File")
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
  def path_with_special_sign_is_invalid() = {
    validateFailure("src/a.b-b.c.File")
    validateFailure("src/a.b.c.File/")
  }

  @Test
  def file_ending_addition_is_treated_as_file_name() =
    validateSuccess("src/a.b.c.File.scala")

  @Test
  def file_ending_addition_is_supported_in_folder_notation() =
    validateSuccess("folder/a/b/c/File.scala")

  @Test
  def no_keywords_allowed_in_package_notation() = {
    validateFailure("src/trait.File")
    validateFailure("src/a.trait")
  }

  @Test
  def keyword_and_special_signs_allowed_as_source_folder_name() = {
    validateSuccess("trait/a.File")
    validateSuccess("a-b/a.File")
  }

  @Test
  def no_file_name_validation_in_folder_notation() =
    validateSuccess("fo?lder/a-bc/trait/a.b/class.")

  @Test
  def no_slash_allowed_in_package_notation() =
    validateFailure("src/a.b/c.File")
}