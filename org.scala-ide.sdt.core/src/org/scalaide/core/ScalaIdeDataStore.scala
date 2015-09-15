package org.scalaide.core

import java.io.File
import java.io.File.{ separator => sep }
import java.io.IOException

import org.scalaide.util.eclipse.EclipseUtils

object ScalaIdeDataStore {

  private val userHome = System.getProperty("user.home")

  /**
   * Preference store id that points to the location of Scala IDEs own Eclipse
   * independent data store.
   */
  final val DataStoreId = "org.scalaide.core.dataStore"

  /**
   * The default location of the data store. Users can change it in the Scala
   * preference page. The data store can be shared between multiple Eclipse
   * instances.
   */
  final val DefaultDataStoreLocation = s"$userHome$sep.scalaide"

  /**
   * The absolute path to the data store.
   */
  def dataStoreLocation: String =
    IScalaPlugin().getPreferenceStore.getString(DataStoreId)

  def statisticsLocation: String =
    s"$dataStoreLocation${sep}statistics"

  def write[A](location: String)(f: File ⇒ A): Option[A] = {
    EclipseUtils.withSafeRunner(s"Error while writing to data store file '$location'") {
      f(validateFile(location))
    }
  }

  def read[A](location: String)(f: File ⇒ A): Option[A] = {
    EclipseUtils.withSafeRunner(s"Error while reading from data store file '$location'") {
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
