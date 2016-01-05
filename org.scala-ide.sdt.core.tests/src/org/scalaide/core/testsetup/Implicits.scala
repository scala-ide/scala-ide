package org.scalaide.core
package testsetup

import org.eclipse.jdt.core.JavaCore
import org.junit.Assert
import org.scalaide.core.IScalaProject

object Implicits {
  import SDTTestUtils._

  implicit class TestableProject(from: IScalaProject) {
    def dependsOnAndExports(dep: IScalaProject, exported: Boolean = true): Unit =
      addToClasspath(from, JavaCore.newProjectEntry(dep.underlying.getFullPath, exported))

    def onlyDependsOn(dep: IScalaProject) = dependsOnAndExports(dep, false)

    def shouldDependOn(msg: String, deps: IScalaProject*): Unit = {
      val expected = deps.map(_.underlying).sortBy(_.getName)
      val computed = from.transitiveDependencies.sortBy(_.getName)
      Assert.assertEquals(s"${msg.capitalize} for ${from.underlying.getName}", expected, computed)
    }
  }
}
