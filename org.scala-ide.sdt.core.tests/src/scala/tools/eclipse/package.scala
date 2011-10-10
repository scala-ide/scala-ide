package scala.tools

import org.eclipse.jdt.core.ICompilationUnit
import scala.tools.eclipse.javaelements.ScalaCompilationUnit

package object eclipse {
  implicit def iCompUnit2ScalaCompUnit(unit: ICompilationUnit): ScalaCompilationUnit = {
    unit.asInstanceOf[ScalaCompilationUnit]
  }
}