package scala.tools.eclipse.sourcefileprovider

import java.util.concurrent.{ConcurrentMap, ConcurrentHashMap}
import org.eclipse.core.runtime.CoreException
import org.eclipse.core.runtime.IConfigurationElement
import org.eclipse.core.runtime.IExtension
import org.eclipse.core.runtime.IExtensionPoint
import org.eclipse.core.runtime.Platform
import scala.tools.eclipse.logging.HasLogger
import org.eclipse.core.runtime.IPath

object SourceFileProviderRegistry extends HasLogger {
  private val EXTENSION_POINT = "org.scala-ide.sdt.core.sourcefileprovider"

  private object FileExtension {
    def apply(path: IPath): FileExtension = new FileExtension(path.getFileExtension())
  }  
    
  private case class FileExtension(extension: String)

  // Note: The map has to be thread-safe, since it can potentially be accessed by different threads at the same time  
  private val registry: ConcurrentMap[FileExtension, SourceFileProvider] = new ConcurrentHashMap 

  registerProviders()

  def getProvider(path: IPath): Option[SourceFileProvider] = getProvider(FileExtension(path))
  
  private def getProvider(extension: FileExtension): Option[SourceFileProvider] = Option(registry get extension)

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
    val extension = FileExtension(fileExtension)
    if(registry containsKey extension) eclipseLog.warn("Source file provider for file extension `%s` already exists. Registration of `%s` will hence be ignored.".format(fileExtension, provider))
    else registry put (extension, provider)
  }
  
  // Note: we may need to implement the `IRegistryEventListener` if we want to support plugins that are started on the fly. This can be easily done 
  //       via `Platform.getExtensionRegistry().addListener(...)`
}
