package org.scalaide.core
package semantichighlighting.classifier

import scala.reflect.internal.util.BatchSourceFile
import org.junit.Before
import testsetup.TestProjectSetup
import org.scalaide.core.internal.project.ScalaProject
import org.scalaide.util.internal.eclipse.EclipseUtils
import org.scalaide.core.ScalaPlugin
import org.junit.After
import org.eclipse.core.runtime.NullProgressMonitor
import org.eclipse.jface.text.IRegion
import org.scalaide.core.internal.decorators.semantichighlighting.classifier._
import org.scalaide.core.internal.decorators.semantichighlighting.classifier.SymbolTypes._

class AbstractSymbolClassifierTest {
  import AbstractSymbolClassifierTest._

  protected val simulator = new EclipseUserSimulator

  private var project: ScalaProject = _

  @Before
  def createProject() {
    project = simulator.createProjectInWorkspace("symbols-classification", true)
  }

  @After
  def deleteProject() {
    EclipseUtils.workspaceRunnableIn(ScalaPlugin.plugin.workspaceRoot.getWorkspace) { _ =>
      project.underlying.delete(true, null)
    }
  }

  protected def checkSymbolClassification(source: String, locationTemplate: String, regionTagToSymbolType: Map[String, SymbolType]) {
    checkSymbolInfoClassification(source, locationTemplate, regionTagToSymbolType.mapValues(symbolType => SymbolInfo(symbolType, Nil, deprecated = false, inInterpolatedString = false)))
  }

  protected def checkSymbolInfoClassification(source: String, locationTemplate: String, regionTagToSymbolInfo: Map[String, SymbolInfo], delimiter: Char = '$') {
    val expectedRegionToSymbolNameMap: Map[IRegion, String] = RegionParser.getRegions(locationTemplate, delimiter)
    val expectedRegionsAndSymbols: List[(IRegion, SymbolInfo)] =
      expectedRegionToSymbolNameMap.mapValues(regionTagToSymbolInfo).toList sortBy regionOffset
    val actualRegionsAndSymbols: List[(IRegion, SymbolInfo)] =
      classifySymbols(source, expectedRegionToSymbolNameMap.keySet).map{case (region, symbolInfo) => (region, symbolInfo.copy(regions = Nil))}.sortBy(regionOffset).distinct
    if (expectedRegionsAndSymbols != actualRegionsAndSymbols) {
      val sb = new StringBuffer
      def displayRegions(regionToSymbolInfoMap: List[(IRegion, SymbolInfo)]) = {
        regionToSymbolInfoMap.sortBy(regionOffset) map {
          case (region, symbolInfo) =>
            "  " + region + " '" + region.of(source) + "' " + symbolInfo
        } mkString "\n"
      }
      sb.append("Actual != Expected.\n")
      sb.append("Expected:\n")
      sb.append(displayRegions(expectedRegionsAndSymbols))
      sb.append("\nActual:\n")
      sb.append(displayRegions(actualRegionsAndSymbols))
      throw new AssertionError(sb.toString)
    }
  }

  private def classifySymbols(source: String, restrictToRegions: Set[IRegion]): List[(IRegion, SymbolInfo)] = {
    val sourceFile = new BatchSourceFile("", source)
    project.presentationCompiler { compiler =>
      // first load the source
      val dummy = new compiler.Response[Unit]
      compiler.askReload(List(sourceFile), dummy)
      dummy.get

      // then run classification
      val symbolInfos: List[SymbolInfo] = new SymbolClassification(sourceFile, compiler, useSyntacticHints = true).classifySymbols(new NullProgressMonitor)
      for {
        symbolInfo <- symbolInfos
        region <- symbolInfo.regions
        if restrictToRegions exists region.intersects
      } yield (region, symbolInfo)
    } getOrElse Nil
  }.distinct sortBy regionOffset

  private def regionOffset(regionAndSymbolInfo: (IRegion, _)) = regionAndSymbolInfo._1.getOffset

}

object AbstractSymbolClassifierTest {
  private implicit class RegionOps(region: IRegion) {
    def intersects(other: IRegion): Boolean =
      !(other.getOffset >= region.getOffset + region.getLength || other.getOffset + other.getLength - 1 < region.getOffset)

    def of(s: String): String = s.slice(region.getOffset, region.getOffset + region.getLength)
  }
}