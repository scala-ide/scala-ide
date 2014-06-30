package org.scalaide.ui.wizards

import org.junit.ComparisonFailure
import org.junit.Test

class EmptyFileCreatorInitialPathTest extends EmptyFileCreator {

  implicit class Implicit_===(path: String) {
    def ===(expected: String): Unit = {
      val isDirectory = !path.endsWith(".scala")
      val actual = generateInitialPath(path.split("/"), isDirectory)
      if (actual != expected)
        throw new ComparisonFailure("", expected, actual)
    }
  }

  @Test
  def no_initial_path_when_no_file_is_selected() =
    "P" === ""

  @Test
  def no_initial_path_when_file_is_not_in_a_folder() =
    "P/file.scala" === ""

  @Test
  def only_parent_folder_name_when_file_is_selected() =
    "P/folder/file.scala" === "folder/"

  @Test
  def no_package_path_when_file_is_in_subfolder_outside_of_source_folder() = {
    "P/folder/a/file.scala" === "folder/a/"
    "P/folder/a/b/file.scala" === "folder/a/b/"
    "P/folder/a/b/c/file.scala" === "folder/a/b/c/"
  }

  @Test
  def inital_path_to_inner_folder_when_no_file_is_selected_outside_of_source_folder() = {
    "P/folder/a" === "folder/a/"
    "P/folder/a/b" === "folder/a/b/"
    "P/folder/a/b/c" === "folder/a/b/c/"
  }
}