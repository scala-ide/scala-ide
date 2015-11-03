package org.scalaide.core

import java.io.File
import java.io.File.{ separator => sep }
import java.io.IOException

import org.scalaide.util.eclipse.EclipseUtils

/**
 * Contains definitions that provide access to the Scala IDE data store. The
 * data store is persisted on disk at any location the user has defined and can
 * be used by every feature in the IDE to persist data that should be available
 * between multiple startups of the IDE or even between multiple instances of
 * the IDE running at the same time.
 */
object ScalaIdeDataStore {

  private val userHome = System.getProperty("user.home")

  /**
   * Preference store id that points to the location of Scala IDEs own Eclipse
   * independent data store.
   */
  val DataStoreId = "org.scalaide.core.dataStore"

  /**
   * The default location of the data store. Users can change it in the Scala
   * preference page. The data store can be shared between multiple Eclipse
   * instances.
   */
  val DefaultDataStoreLocation = s"$userHome$sep.scalaide"

  /**
   * The absolute path to the data store.
   */
  def dataStoreLocation: String =
    IScalaPlugin().getPreferenceStore.getString(DataStoreId)

  /**
   * The location of the statistics file.
   */
  def statisticsLocation: String =
    s"$dataStoreLocation${sep}statistics"

  /**
   * The location of the class file cache for
   * [[org.scalaide.core.internal.extensions.ExtensionCompiler]].
   */
  def extensionsOutputDirectory: String =
    s"$dataStoreLocation${sep}classes"

  /**
   * Takes an absolute path as `location` and calls `f` with a file that
   * represents `location` if the path is valid. The return value of `f` is
   * returned in this case, otherwise `f` is not called and `None` is returned.
   */
  def validate[A](location: String)(f: File ⇒ A): Option[A] = {
    EclipseUtils.withSafeRunner(s"Error while trying to access data store file '$location'.") {
      f(validateFile(location))
    }
  }

  private def validateFile(location: String): File = {
    val file = new File(location)
    if (!file.exists()) {
      file.getParentFile.mkdirs()
      file.createNewFile()
    }
    if (file.isDirectory())
      throw new IOException(s"Path '$location' is not a valid file location.")
    file
  }
}
