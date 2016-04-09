package org.scalaide.core
package sbtbuilder

import org.eclipse.core.resources.IProject
import org.eclipse.core.resources.IProjectDescription
import org.eclipse.core.resources.IncrementalProjectBuilder
import org.junit.Assert
import org.junit.Test
import org.scalaide.core.SdtConstants
import org.scalaide.ui.internal.preferences.ScalaPluginSettings
import org.scalaide.util.internal.SettingConverterUtil

import testsetup.Implicits
import testsetup.SDTTestUtils

class NonScalaSourceErrorMarkersTest {
  import SDTTestUtils._
  import Implicits.TestableProject

  def addScalaNature(project: IProject) = natures(project) { description =>
    SdtConstants.NatureId +: description.getNatureIds
  }

  private def natures(project: IProject)(newNatures: IProjectDescription => Array[String]): Unit = {
    val description = project.getDescription
    description.setNatureIds(newNatures(description))
    project.setDescription(description, null)
  }

  def removeScalaNature(project: IProject) = natures(project) { description =>
    description.getNatureIds.filter { _ != SdtConstants.NatureId }
  }

  def soThen(assertThat: => Unit): Unit = assertThat

  @Test def shouldFindJavaErrorMarkerTooWhenScalaNatureOfAisOffAndOn(): Unit = {
    val Seq(prjA, prjB, prjC) = createProjects("A", "B", "C")

    try {
      prjB dependsOnAndExports prjA
      prjC onlyDependsOn prjB

      val Seq(packA, packB, packC) = Seq(prjA, prjB, prjC).map(createSourcePackage("test"))

      val unitA = packA.createCompilationUnit("A.scala", "trait A", true, null)
      val unitB = packB.createCompilationUnit("B.scala", "class B extends A", true, null)
      val unitC = packC.createCompilationUnit("C.scala", "class C extends B", true, null)

      // set stopOnBuildErrors to true
      val stopBuildOnErrors = SettingConverterUtil.convertNameToProperty(ScalaPluginSettings.stopBuildOnErrors.name)
      IScalaPlugin().getPreferenceStore.setValue(stopBuildOnErrors, true)

      // no errors
      SDTTestUtils.workspace.build(IncrementalProjectBuilder.INCREMENTAL_BUILD, null)
      val unitsToWatch = Seq(unitA, unitB, unitC)
      val errors = SDTTestUtils.getErrorMessages(unitsToWatch: _*)
      Assert.assertEquals("No build errors", Seq(), errors)

      def whenRemoveAndAddScalaNatureToA(): Unit = {
        removeScalaNature(prjA.underlying)
        SDTTestUtils.workspace.build(IncrementalProjectBuilder.INCREMENTAL_BUILD, null)
        addScalaNature(prjA.underlying)
        SDTTestUtils.workspace.build(IncrementalProjectBuilder.INCREMENTAL_BUILD, null)
      }
      whenRemoveAndAddScalaNatureToA()

      soThen {
        Assert.assertEquals("One error in A", Seq((2, "Syntax error on tokens, delete these tokens")), SDTTestUtils.getErrorMessages(prjA.underlying))
        Assert.assertEquals("One error in B", Seq((2, """Project: "B" in scope: "main" not built due to errors in dependent project(s): A. Root error(s): Syntax error on tokens, delete these tokens""")), SDTTestUtils.getErrorMessages(prjB.underlying))
        Assert.assertEquals("One error in C", Seq((2, """Project: "C" in scope: "main" not built due to errors in dependent project(s): B, A. Root error(s): Project: "B" in scope: "main" not built due to errors in dependent project(s): A. Root error(s): Syntax error on tokens, delete these tokens;Syntax error on tokens, delete these tokens""")),
          SDTTestUtils.getErrorMessages(prjC.underlying))
      }
    } finally {
      deleteProjects(prjA, prjB, prjC)
    }
  }
}
