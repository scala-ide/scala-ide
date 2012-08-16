package scala.tools.eclipse.sourcefileprovider

import org.eclipse.core.runtime.IPath
import scala.tools.eclipse.InteractiveCompilationUnit

trait SourceFileProvider {
  /** Create a compilation unit for the passed workspace `path`.*/
  def createFrom(path: IPath): Option[InteractiveCompilationUnit]
}