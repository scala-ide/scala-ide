package org.scalaide.core.extensions

import java.util.concurrent.ConcurrentMap
import java.util.concurrent.ConcurrentHashMap
import org.eclipse.core.runtime.CoreException
import org.eclipse.core.runtime.IConfigurationElement
import org.eclipse.core.runtime.IExtension
import org.eclipse.core.runtime.IExtensionPoint
import org.eclipse.core.runtime.Platform
import org.scalaide.logging.HasLogger
import org.eclipse.core.runtime.IPath

object SourceFileProviderRegistry extends HasLogger {
  private val EXTENSION_POINT = "org.scala-ide.sdt.core.sourcefileprovider"

  // Note: The map has to be thread-safe, since it can potentially be accessed by different threads at the same time
  private val registry: ConcurrentMap[String, SourceFileProvider] = new ConcurrentHashMap

  registerProviders()

  /** Return the source file provider for the given path.
   *
   *  @return A registered `SourceFileProvider` or `null` if not found.
   */
  def getProvider(path: IPath): SourceFileProvider = {
    import scala.collection.JavaConverters._
    val fullName = path.toPortableString()
    val record = registry.asScala find { case (k, v) => fullName.endsWith(k) }
    record.map(_._2).getOrElse(null)
  }

  private def getProvider(extension: String): SourceFileProvider = registry get extension

  private def registerProviders() {
    val extensionPoint = Platform.getExtensionRegistry().getExtensionPoint(EXTENSION_POINT)
    if (extensionPoint != null) {
      val extensions = extensionPoint.getExtensions()
      for {
        extension <- extensions
        config <- extension.getConfigurationElements
        if config.isValid
      } try {
        val provider = config.createExecutableExtension("class").asInstanceOf[SourceFileProvider]
        registerProvider(config.getAttribute("file_extension"), provider)
      } catch {
        case e: CoreException =>
          eclipseLog.error("Failed to register source file provider for extension point: " + extension, e)
      }
    }
  }

  private def registerProvider(fileExtension: String, provider: SourceFileProvider): Unit = {
    if(registry containsKey fileExtension) eclipseLog.warn("Source file provider for file extension `%s` already exists. Registration of `%s` will hence be ignored.".format(fileExtension, provider))
    else registry put (fileExtension, provider)
  }

  // Note: we may need to implement the `IRegistryEventListener` if we want to support plugins that are started on the fly. This can be easily done
  //       via `Platform.getExtensionRegistry().addListener(...)`
}
