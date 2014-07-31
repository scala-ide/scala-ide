package org.scalaide.core
package sbtbuilder

import org.junit.Test
import org.eclipse.jdt.core.IJavaProject
import org.eclipse.jdt.core.IClasspathEntry
import org.eclipse.jdt.core.JavaCore
import org.junit.Assert
import testsetup.SDTTestUtils
import org.eclipse.core.resources.IncrementalProjectBuilder
import org.scalaide.ui.internal.preferences.IDESettings
import org.scalaide.ui.internal.preferences.CompilerSettings
import org.eclipse.jdt.core.IPackageFragment
import org.scalaide.util.internal.SettingConverterUtil
import org.scalaide.ui.internal.preferences.ScalaPluginSettings

class ProjectDependenciesTest {

  val simulator = new EclipseUserSimulator
  import SDTTestUtils._

  @Test def transitive_dependencies_no_export() {
    val Seq(prjA, prjB, prjC) = createProjects("A", "B", "C")

    try {
      // A -> B -> C
      addToClasspath(prjB, JavaCore.newProjectEntry(prjA.underlying.getFullPath, false))
      addToClasspath(prjC, JavaCore.newProjectEntry(prjB.underlying.getFullPath, false))

      Assert.assertEquals("No dependencies for base project", Seq(), prjA.transitiveDependencies)
      Assert.assertEquals("One direct dependency for B", Seq(prjA.underlying), prjB.transitiveDependencies)
      Assert.assertEquals("One transitive dependency for C", Seq(prjB.underlying), prjC.transitiveDependencies)
    } finally {
      deleteProjects(prjA, prjB, prjC)
    }
  }

  @Test def transitive_dependencies_with_export() {
    val Seq(prjA, prjB, prjC) = createProjects("A", "B", "C")

    try {
      // A -> B -> C
      addToClasspath(prjB, JavaCore.newProjectEntry(prjA.underlying.getFullPath, true))
      addToClasspath(prjC, JavaCore.newProjectEntry(prjB.underlying.getFullPath, false))

      Assert.assertEquals("No dependencies for base project", Seq(), prjA.transitiveDependencies)
      Assert.assertEquals("One direct dependency for B", Seq(prjA.underlying), prjB.transitiveDependencies)
      Assert.assertEquals("Two transitive dependencies for C", Seq(prjB.underlying, prjA.underlying), prjC.transitiveDependencies)
    } finally {
      deleteProjects(prjA, prjB, prjC)
    }
  }

  @Test def transitive_dep_with_error_stops_build() {
    val Seq(prjA, prjB, prjC) = createProjects("A", "B", "C")

    try {
      // A -> B -> C
      addToClasspath(prjB, JavaCore.newProjectEntry(prjA.underlying.getFullPath, true))
      addToClasspath(prjC, JavaCore.newProjectEntry(prjB.underlying.getFullPath, false))

      val Seq(packA, packB, packC) = Seq(prjA, prjB, prjC).map(createSourcePackage("test"))

      val unitA = packA.createCompilationUnit("A.scala", "class A", true, null)
      val unitB = packB.createCompilationUnit("B.scala", "class B extends A", true, null)
      val unitC = packC.createCompilationUnit("C.scala", "class C(a: A, b: B)", true, null)

      // set stopOnBuild to true
      val stopBuildOnErrors = SettingConverterUtil.convertNameToProperty(ScalaPluginSettings.stopBuildOnErrors.name)
      ScalaPlugin.plugin.getPreferenceStore.setValue(stopBuildOnErrors, true)

      // no errors
      SDTTestUtils.workspace.build(IncrementalProjectBuilder.INCREMENTAL_BUILD, null)

      val unitsToWatch = Seq(unitA, unitB, unitC)

      val errors = SDTTestUtils.getErrorMessages(unitsToWatch: _*)
      Assert.assertEquals("No build errors", Seq(), errors)

      // one error in A, no cascading errors
      val errors1 = SDTTestUtils.buildWith(unitA.getResource, "klass A", unitsToWatch)
      Assert.assertEquals("One build error in A", Seq("expected class or object definition"), errors1)

      val errorsInBAndC = SDTTestUtils.getErrorMessages(unitB, unitC)
      Assert.assertEquals("No errors in dependent projects", Seq(), errorsInBAndC)

      // fix project A, error in B, no cascading errors in C
      SDTTestUtils.buildWith(unitA.getResource, "class A(x: Int)", unitsToWatch)
      Assert.assertEquals("No errors in A", Seq(), SDTTestUtils.getErrorMessages(unitA))
      Assert.assertEquals("No errors in C", Seq(), SDTTestUtils.getErrorMessages(unitC))
      Assert.assertEquals("One error in B", 1, SDTTestUtils.getErrorMessages(unitB).size)

      // fix all errors, everything built
      val errors3 = SDTTestUtils.buildWith(unitA.getResource, "class A", unitsToWatch)
      Assert.assertEquals("No errors in A, B or C", Seq(), errors3)
    } finally {
      deleteProjects(prjA, prjB, prjC)
    }
  }

  @Test def transitive_dep_indirect() {
    val Seq(prjA, prjB, prjC) = createProjects("A", "B", "C")

    try {
      // A -> B -> C
      addToClasspath(prjB, JavaCore.newProjectEntry(prjA.underlying.getFullPath, /* isExported = */ true))
      addToClasspath(prjC, JavaCore.newProjectEntry(prjB.underlying.getFullPath, false))

      val Seq(packA, _, packC) = Seq(prjA, prjB, prjC).map(createSourcePackage("test"))

      // C depends directly on A, through an exported dependency of B
      val unitA = packA.createCompilationUnit("A.scala", "class A", true, null)
      val unitC = packC.createCompilationUnit("C.scala", "class C(a: A)", true, null)

      // no errors
      SDTTestUtils.workspace.build(IncrementalProjectBuilder.INCREMENTAL_BUILD, null)

      val unitsToWatch = Seq(unitA, unitC)

      val errors = SDTTestUtils.getErrorMessages(unitsToWatch: _*)
      Assert.assertEquals("No build errors", Seq(), errors)

      // one error in C
      val errors1 = SDTTestUtils.buildWith(unitA.getResource, "class A2", unitsToWatch)
      Assert.assertEquals("One build error in C", Seq("not found: type A"), errors1)

      // fix project A, no errors
      SDTTestUtils.buildWith(unitA.getResource, "class A", unitsToWatch)
      Assert.assertEquals("No errors in A", Seq(), SDTTestUtils.getErrorMessages(unitA))
      Assert.assertEquals("No errors in C", Seq(), SDTTestUtils.getErrorMessages(unitC))
    } finally {
      deleteProjects(prjA, prjB, prjC)
    }
  }

}