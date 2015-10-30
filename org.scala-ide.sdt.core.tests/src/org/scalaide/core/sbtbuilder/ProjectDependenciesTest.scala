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
import org.scalaide.core.IScalaPlugin
import org.eclipse.core.resources.IProject

class ProjectDependenciesTest {

  import SDTTestUtils._

  private implicit class TestableProject(from: IScalaProject) {
    def dependsOnAndExports(dep: IScalaProject, exported: Boolean = true): Unit =
      addToClasspath(from, JavaCore.newProjectEntry(dep.underlying.getFullPath, exported))

    def onlyDependsOn(dep: IScalaProject) = dependsOnAndExports(dep, false)

    def shouldDependOn(msg: String, deps: IScalaProject*): Unit = {
      val expected = deps.map(_.underlying).sortBy(_.getName)
      val computed = from.transitiveDependencies.sortBy(_.getName)
      Assert.assertEquals(s"${msg.capitalize} for ${from.underlying.getName}", expected, computed)
    }
  }


  @Test def transitive_dependencies_more_complicated_tree(): Unit = {
    val allProj @ Seq(prjA, prjB, prjC, prjD, prjE, prjF, prjG) = createProjects("A", "B", "C", "D", "E", "F", "G")
    try {
      prjB dependsOnAndExports prjA

      prjD dependsOnAndExports prjC

      prjE onlyDependsOn prjB

      prjE dependsOnAndExports prjD

      prjF dependsOnAndExports prjA
      prjF dependsOnAndExports prjE

      prjG dependsOnAndExports prjE
      prjG dependsOnAndExports prjC


      prjB.shouldDependOn("Only A", prjA)
      prjD.shouldDependOn("Only C", prjC)
      prjE.shouldDependOn("A, B, C,D", prjB, prjD, prjA, prjC)
      prjG.shouldDependOn("C, D, E - C should not be added twice, B and A should be excluded", prjC, prjE, prjD)

    } finally {
      deleteProjects(allProj: _*)
    }
  }

  @Test def transitive_dependencies_no_export(): Unit = {
    val Seq(prjA, prjB, prjC, prjD) = createProjects("A", "B", "C", "D")

    try {
      prjB onlyDependsOn prjA
      prjC onlyDependsOn prjB
      prjD onlyDependsOn prjC


      prjA.shouldDependOn("Nothing - base project")
      prjB.shouldDependOn("Only A", prjA)
      prjC.shouldDependOn("Only B", prjB)
      prjD.shouldDependOn("Only C", prjC)
    } finally {
      deleteProjects(prjA, prjB, prjC, prjD)
    }
  }

  @Test def transitive_dependencies_with_export(): Unit = {
    val Seq(prjA, prjB, prjC, prjD) = createProjects("A", "B", "C", "D")

    try {
      prjB dependsOnAndExports prjA
      prjC dependsOnAndExports prjB
      prjD dependsOnAndExports prjC


      prjA.shouldDependOn("Nothing - base project")
      prjB.shouldDependOn("Only A", prjA)
      prjC.shouldDependOn("A and B", prjA, prjB)
      prjD.shouldDependOn("A, B and C", prjA, prjB, prjC)
    } finally {
      deleteProjects(prjA, prjB, prjC, prjD)
    }
  }

  @Test def transitive_dep_with_error_stops_build(): Unit = {
    val Seq(prjA, prjB, prjC) = createProjects("A", "B", "C")

    try {
      prjB dependsOnAndExports prjA
      prjC onlyDependsOn prjB

      val Seq(packA, packB, packC) = Seq(prjA, prjB, prjC).map(createSourcePackage("test"))

      val unitA = packA.createCompilationUnit("A.scala", "class A", true, null)
      val unitB = packB.createCompilationUnit("B.scala", "class B extends A", true, null)
      val unitC = packC.createCompilationUnit("C.scala", "class C(a: A, b: B)", true, null)

      // set stopOnBuild to true
      val stopBuildOnErrors = SettingConverterUtil.convertNameToProperty(ScalaPluginSettings.stopBuildOnErrors.name)
      IScalaPlugin().getPreferenceStore.setValue(stopBuildOnErrors, true)

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

  @Test def transitive_dep_indirect(): Unit = {
    val Seq(prjA, prjB, prjC) = createProjects("A", "B", "C")

    try {
      // A -> B -> C
      prjB dependsOnAndExports prjA
      prjC onlyDependsOn prjB

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
