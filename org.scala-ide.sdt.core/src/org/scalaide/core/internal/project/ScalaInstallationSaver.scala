package org.scalaide.core.internal.project

import org.eclipse.core.runtime.IStatus
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import org.eclipse.core.runtime.CoreException
import java.io.File
import org.eclipse.core.runtime.Status
import org.scalaide.core.ScalaPlugin
import org.scalaide.logging.HasLogger
import scala.collection.mutable.Publisher
import scala.collection.mutable.Subscriber

case class ModifiedScalaInstallations()

trait LabeledScalaInstallationSerializer extends HasLogger{
  import org.scalaide.core.internal.jdt.util.LabeledScalaInstallationsSaveHelper._

  private def getInstallationsStateFile() = {
    new File(ScalaPlugin.plugin.getStateLocation().toFile(), "ScalaInstallations.back")
  }

  def saveInstallationsState(installations: List[LabeledScalaInstallation]): Unit = {
    val installationsStateFile = getInstallationsStateFile()
    val installationsStateFilePath = installationsStateFile.getPath()
    logger.debug(s"Trying to write Scala installations state to $installationsStateFilePath")
    var is: FileOutputStream = null
    try {
      is = new FileOutputStream(installationsStateFile)
      writeInstallations(installations, is)
    } catch {
      case ex: IOException =>
        logger.error("Can't save scala installations", ex)
    } finally {
      if (is != null) {
        try {
          is.close();
        } catch {
          case ex: IOException => logger.error("Can't close output stream for " + installationsStateFile.getAbsolutePath(), ex)
        } finally logger.debug(s"Successfully wrote installations state to $installationsStateFilePath")
      }

    }
  }

  def getSavedInstallations(): List[LabeledScalaInstallation] = {
    val installationsStateFile = getInstallationsStateFile()
    val installationsStateFilePath = installationsStateFile.getPath()
    //logger.debug(s"Trying to read classpath container state from $installationsStateFilePath")
    if (!installationsStateFile.exists()) Nil
    else {
      var is: FileInputStream = null
      try {
        is = new FileInputStream(installationsStateFile)
        readInstallations(is)
      } catch {
        case ex @ (_: IOException | _: ClassNotFoundException) =>
          throw new CoreException(new Status(IStatus.ERROR, ScalaPlugin.plugin.pluginId, -1,
            s"Can't read scala installations from $installationsStateFilePath", ex))
      } finally {
        if (is != null) {
          try {
            is.close();
          } catch {
            case ex: IOException => //logger.error("Can't close output stream for " + installationsStateFile.getAbsolutePath(), ex)
          } finally () //logger.debug(s"Successfully read scala installations from $installationsStateFilePath")
        }
      }
    }
  }

}

class ScalaInstallationSaver extends LabeledScalaInstallationSerializer with Subscriber[ModifiedScalaInstallations, Publisher[ModifiedScalaInstallations]] {

  override def notify(pub: Publisher[ModifiedScalaInstallations], event: ModifiedScalaInstallations): Unit = {
    saveInstallationsState(ScalaInstallation.availableInstallations)
  }

}