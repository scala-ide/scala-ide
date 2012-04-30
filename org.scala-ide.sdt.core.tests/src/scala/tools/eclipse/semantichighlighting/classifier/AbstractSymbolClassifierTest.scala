package scala.tools.eclipse.semantichighlighting.classifier

import scala.tools.nsc.util.BatchSourceFile
import org.junit.Before
import scala.tools.eclipse.testsetup.TestProjectSetup
import scala.tools.eclipse.EclipseUserSimulator
import scala.tools.eclipse.ScalaProject
import scala.tools.eclipse.util.EclipseUtils
import scala.tools.eclipse.ScalaPlugin
import org.junit.After

class AbstractSymbolClassifierTest {

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
    val expectedRegionToSymbolNameMap: Map[Region, String] = RegionParser.getRegions(locationTemplate)
    val expectedRegionsAndSymbols: List[(Region, SymbolType)] =
      expectedRegionToSymbolNameMap.mapValues(regionTagToSymbolType).toList sortBy regionOffset
    val actualRegionsAndSymbols: List[(Region, SymbolType)] =
      classifySymbols(source, expectedRegionToSymbolNameMap.keySet) sortBy regionOffset

    if (expectedRegionsAndSymbols != actualRegionsAndSymbols) {
      val sb = new StringBuffer
      def displayRegions(regionToSymbolInfoMap: List[(Region, SymbolType)]) = {
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

  private def classifySymbols(source: String, restrictToRegions: Set[Region]): List[(Region, SymbolType)] = {
    val sourceFile = new BatchSourceFile("", source)
    project.withPresentationCompiler { compiler =>
      // first load the source
      val dummy = new compiler.Response[Unit]
      compiler.askReload(List(sourceFile), dummy)
      dummy.get
    
      // then run classification
      val symbolInfos: List[SymbolInfo] = SymbolClassifier.classifySymbols(sourceFile, compiler, useSyntacticHints = true)
      for {
        SymbolInfo(symbolType, regions, deprecated) <- symbolInfos
        region <- regions
        if restrictToRegions exists region.intersects
      } yield (region, symbolType)
    }(orElse = Nil)
  }.distinct sortBy regionOffset

  private def regionOffset(regionAndSymbolType: (Region, SymbolType)) = regionAndSymbolType._1.offset

}