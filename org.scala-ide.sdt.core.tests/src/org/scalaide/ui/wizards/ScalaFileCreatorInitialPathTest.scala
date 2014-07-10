package org.scalaide.ui.wizards

import org.junit.ComparisonFailure
import org.junit.Test

class ScalaFileCreatorInitialPathTest extends ScalaFileCreator {

  implicit class Implicit_===(path: String) {
    def ===(expected: String): Unit = {
      val isDirectory = !path.endsWith(".scala")
      val actual = generateInitialPath(path.split("/"), Seq("src", "src2"), isDirectory)
      if (actual != expected)
        throw new ComparisonFailure("", expected, actual)
    }
  }

  @Test
  def source_folder_when_no_file_is_selected() =
    "P" === "src/"

  @Test
  def only_source_folder_name_when_file_is_in_default_package() =
    "P/src/file.scala" === "src/"

  @Test
  def source_folder_and_package_when_file_is_in_package() = {
    "P/src/a/file.scala" === "src/a."
    "P/src/a/b/file.scala" === "src/a.b."
    "P/src/a/b/c/file.scala" === "src/a.b.c."
  }

  @Test
  def initial_path_to_most_inner_package_when_no_file_is_selected_inside_of_source_folder() = {
    "P/src/a" === "src/a."
    "P/src/a/b" === "src/a.b."
    "P/src/a/b/c" === "src/a.b.c."
  }

  @Test
  def source_folder_when_selected_file_is_in_no_source_folder() =
    "P/folder/file.scala" === "src/"
}