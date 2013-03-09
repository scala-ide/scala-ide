package scala.tools.eclipse.semantichighlighting.classifier

import scala.tools.nsc.util.BatchSourceFile
import org.junit.Before
import scala.tools.eclipse.testsetup.TestProjectSetup
import scala.tools.eclipse.EclipseUserSimulator
import scala.tools.eclipse.ScalaProject
import scala.tools.eclipse.util.EclipseUtils
import scala.tools.eclipse.ScalaPlugin
import org.junit.After
import org.eclipse.core.runtime.NullProgressMonitor
import org.eclipse.jface.text.IRegion
import scala.tools.eclipse.semantichighlighting.classifier.SymbolTypes.SymbolType

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
    val expectedRegionToSymbolNameMap: Map[IRegion, String] = RegionParser.getRegions(locationTemplate)
    val expectedRegionsAndSymbols: List[(IRegion, SymbolType)] =
      expectedRegionToSymbolNameMap.mapValues(regionTagToSymbolType).toList sortBy regionOffset
    val actualRegionsAndSymbols: List[(IRegion, SymbolType)] =
      classifySymbols(source, expectedRegionToSymbolNameMap.keySet) sortBy regionOffset

    if (expectedRegionsAndSymbols != actualRegionsAndSymbols) {
      val sb = new StringBuffer
      def displayRegions(regionToSymbolInfoMap: List[(IRegion, SymbolType)]) = {
        regionToSymbolInfoMap.toList.sortBy(regionOffset) map {
          case (region, symbolType) =>
            "  " + region + " '" + region.of(source) + "' " + symbolType
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

  private def classifySymbols(source: String, restrictToRegions: Set[IRegion]): List[(IRegion, SymbolType)] = {
    val sourceFile = new BatchSourceFile("", source)
    project.withPresentationCompiler { compiler =>
      // first load the source
      val dummy = new compiler.Response[Unit]
      compiler.askReload(List(sourceFile), dummy)
      dummy.get

      // then run classification
      val symbolInfos: List[SymbolInfo] = new SymbolClassification(sourceFile, compiler, useSyntacticHints = true).classifySymbols(new NullProgressMonitor)
      for {
        SymbolInfo(symbolType, regions, deprecated) <- symbolInfos
        region <- regions
        if restrictToRegions exists region.intersects
      } yield (region, symbolType)
    }(orElse = Nil)
  }.distinct sortBy regionOffset

  private def regionOffset(regionAndSymbolType: (IRegion, SymbolType)) = regionAndSymbolType._1.getOffset
}

object AbstractSymbolClassifierTest {
  private class RegionOps(region: IRegion) {
    def intersects(other: IRegion): Boolean =
      !(other.getOffset >= region.getOffset + region.getLength || other.getOffset + other.getLength - 1 < region.getOffset)

    def of(s: String): String = s.slice(region.getOffset, region.getOffset + region.getLength)
  }

  private implicit def region2regionOps(region: IRegion): RegionOps = new RegionOps(region)
}