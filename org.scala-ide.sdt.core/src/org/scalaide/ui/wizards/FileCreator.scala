package org.scalaide.ui.wizards

import org.eclipse.core.resources.IFolder
import org.eclipse.core.resources.IResource
import org.eclipse.core.runtime.IPath

/**
 * Used by `FileCreator` to either model that an operation was invalid, which
 * means that it provides an error message, or that is was valid.
 */
sealed trait Validation {
  def isValid: Boolean
  def isInvalid: Boolean = !isValid
}
case object Valid extends Validation {
  def isValid = true
}
case class Invalid(errorMsg: String) extends Validation {
  def isValid = false
}

/**
 * Interface to the file creator extension point, that needs to be implemented
 * if an extension wants to provide their own file creator.
 */
trait FileCreator {

  /**
   * Returns all the varibles that need to be filled into the associated
   * template. The Map contains the name of the varibles that are associated
   * with their actual values.
   *
   * This method is called when a new file needs to be created and therefore the
   * template needs to be filled with the variables. Hence, `folder` is the
   * selected folder and `name` the file name that are filled in the wizard.
   */
  def templateVariables(folder: IFolder, name: String): Map[String, String]

  /**
   * Finds out if the file name that is inserted into the wizard is valid and
   * can be used to create a real file.
   *
   * If `name` is valid `Valid` needs to be returned otherwise `Invalid` with an
   * error message why it is invalid. The error message is used to be shown to
   * users therefore it should be self-explanatory.
   *
   * `folder` is the folder where the file should be created. If no folder is
   * selected yet, this method will not be called. Note that if this method
   * returns not `Valid` other methods of this interface, especially
   * [[templateVariables]] and [[createFilePath]] will not be called. Therefore
   * it is not safe to call these methods as long as the file name is invalid
   * because their implementations don't have to validate them and therefore can
   * crash in unexpected ways.
   */
  def validateName(folder: IFolder, name: String): Validation

  /**
   * Creates the path that points to the location where the wizard should create
   * a file.
   *
   * `folder` is the folder where the file should be created.
   *
   * `name` is the name of the file.
   *
   * This method is guaranteed to be called not before [[validateName]] returns
   * a valid state.
   *
   * '''Note:''' The returned path needs to be absolute to the root of the filesystem.
   */
  def createFilePath(folder: IFolder, name: String): IPath

  /**
   * Creates a path that is shown when a new file wizard is created. This should
   * usually be the path as near as possible to the location where an user wants
   * to create a new file. In order to detect this location `res` is the
   * resource that was selected right before the new file wizard was invoked.
   */
  def initialPath(res: IResource): String

  /**
   * This method is called whenever [[validateName]] returns a valid `Validation`.
   * It should return all the entries that are shown to the user in a code
   * completion component. The selected entry will replace everything the user
   * typed, therefore it needs to contain all the information an user expects to
   * see.
   */
  def completionEntries(folder: IFolder, name: String): Seq[String]

  /**
   * When the new file wizard is created this controls if in case of an invalid
   * initial path an error message is shown to users.
   */
  def showErrorMessageAtStartup: Boolean =
    false
}