package scala.tools.eclipse.compiler.settings

import scala.tools.eclipse.testsetup.TestProjectSetup
import org.junit.Test
import org.junit.Assert.assertTrue
import scala.tools.eclipse.ScalaPlugin

object CompilerSettingsTest extends TestProjectSetup("compiler-settings")

class CompilerSettingsTest {
  import CompilerSettingsTest._
  
  @Test
  def failingToBuildSourceThatRequiresContinuationPlugin() {
    val unit = scalaCompilationUnit("cps/CPS.scala")
    
    cleanProject()
    fullProjectBuild()
    
    val errors = allBuildErrorsOf(unit)
    
    if(ScalaPlugin.plugin.shortScalaVer == "2.9") 
      assertTrue(errors.nonEmpty)
    else 
      assertTrue(errors.isEmpty) // continuations plugin is enabled by default in 2.10+
  }
  
  @Test
  def successfullyBuildingSourceRequiringContinuationPluginEnabled() {
    withContinuationPluginEnabled {
      val unit = scalaCompilationUnit("cps/CPS.scala")
    
      cleanProject()
      fullProjectBuild()
    
      val errors = allBuildErrorsOf(unit)
    
      assertTrue(errors.isEmpty)
    }
  }
  
  private def withContinuationPluginEnabled(body: => Unit) {
    val value = project.storage.getString("P")
    try {
      project.storage.setValue("P", "continuations:enable")
      body
    }
    finally {
      project.storage.setValue("P", value)
    }
  }
}