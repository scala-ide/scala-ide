package scala.tools.eclipse.javaelements

import org.eclipse.contribution.jdt.cuprovider.ICompilationUnitProvider
import org.eclipse.jdt.core.WorkingCopyOwner
import org.eclipse.jdt.internal.core.PackageFragment

class ScalaCompilationUnitProvider extends ICompilationUnitProvider {
  println("ScalaCompilationUnitProvider")
  
  def create(parent : PackageFragment, name : String, owner : WorkingCopyOwner) =
    new ScalaCompilationUnit(parent, name, owner)
}
