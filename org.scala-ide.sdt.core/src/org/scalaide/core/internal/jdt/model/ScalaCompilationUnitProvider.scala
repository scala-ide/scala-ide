package scala.tools.eclipse.javaelements

import scala.tools.eclipse.contribution.weaving.jdt.cuprovider.ICompilationUnitProvider
import org.eclipse.jdt.core.WorkingCopyOwner
import org.eclipse.jdt.internal.core.PackageFragment

class ScalaCompilationUnitProvider extends ICompilationUnitProvider {
  def create(parent : PackageFragment, name : String, owner : WorkingCopyOwner) =
    new ScalaSourceFile(parent, name, owner)
}
