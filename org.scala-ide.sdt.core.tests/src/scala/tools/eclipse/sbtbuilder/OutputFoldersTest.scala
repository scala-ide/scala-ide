package scala.tools.eclipse.sbtbuilder
import org.junit.Test
import scala.tools.eclipse.EclipseUserSimulator
import org.junit.Assert
import org.eclipse.core.runtime.Path
import org.eclipse.jdt.core.JavaCore
import org.eclipse.core.runtime.IPath
import scala.tools.eclipse.ScalaProject
import scala.tools.eclipse.testsetup.SDTTestUtils
import scala.tools.eclipse.ScalaPlugin
import org.junit.Before

/** Test simple operations involving the source and output
 *  folders for a project.
 */
class OutputFoldersTest {
  val simulator = new EclipseUserSimulator

  implicit def stringsArePaths(str: String): Path = new Path(str)

  @Before def setupBuild {
    SDTTestUtils.enableAutoBuild(false)
  }
  
  
  @Test def defaultOutputDirectory() {
    val projectName = "test-simple-output-projects"
    val project = simulator.createProjectInWorkspace(projectName)
    Assert.assertEquals("Default output directory", Seq(new Path("/%s/bin".format(projectName))), project.outputFolders)
    project.javaProject.setOutputLocation(new Path("/%s/other-bin".format(projectName)), null)
    Assert.assertEquals("Default output directory", Seq(new Path("/%s/other-bin".format(projectName))), project.outputFolders)

    val Seq((srcPath, outputPath)) = project.sourceOutputFolders
    Assert.assertEquals("Source path", new Path("/%s/src".format(projectName)), srcPath.getFullPath())
  }
  
  @Test def multipleOutputDirs() {
    val projectName = "test-simple-output-projects-2"
    val srcMain = new Path("/" +projectName + "/src/main/scala")
    val targetMain = new Path("/" +projectName + "/target/classes")
    val srcTest = new Path("/" +projectName + "/src/test/scala")
    val targetTest = new Path("/" +projectName + "/target/test-classes")

    val project = makeDefaultLayoutProject(projectName)

    val sourcesOutputs = project.sourceOutputFolders
    
    val sources = sourcesOutputs.map(_._1.getFullPath())
    val outputs = sourcesOutputs.map(_._2.getFullPath())

    Assert.assertEquals("Sources", Seq(srcMain, srcTest), sources)
    Assert.assertEquals("Sources", Seq(targetMain, targetTest), outputs)
  }
  

  /** Create a project with the specified source folders, inclusion and exclusion patterns */
  private def makeProject(name: String, sourceFolders: (IPath, Array[IPath], Array[IPath], IPath)*): ScalaProject = {
    
    val project = simulator.createProjectInWorkspace(name, sourceFolders.isEmpty)

    val srcEntries = 
      for ((dirPath, inclPats, exclPats, binPath) <- sourceFolders) 
        yield JavaCore.newSourceEntry(dirPath, inclPats, exclPats, binPath)
    
    project.javaProject.setRawClasspath((project.javaProject.getRawClasspath() ++ srcEntries).toArray, null);
    project
  }

  
  /** Create a project with src/main/scala and src/test/scala, without any source filters. */
  private def makeDefaultLayoutProject(projectName: String) = {
    val srcMain = new Path("/" +projectName + "/src/main/scala")
    val targetMain = new Path("/" +projectName + "/target/classes")
    val srcTest = new Path("/" +projectName + "/src/test/scala")
    val targetTest = new Path("/" +projectName + "/target/test-classes")

    makeProject(projectName, (srcMain, Array(), Array(), targetMain), (srcTest, Array(), Array(), targetTest))
  }
  
  
  @Test def multipleSourceDirsWithoutFilters() {
    
    val project = makeDefaultLayoutProject("allSources1")
    
    val sources = List("src/main/scala/A.scala", "src/main/scala/B.scala", "src/test/scala/C.scala")
    val srcPaths = (for (path <- sources) yield SDTTestUtils.addFileToProject(project.underlying, path, "")).toSet
    
    val allSources = project.allSourceFiles()
    Assert.assertEquals("All sources without filters", srcPaths, allSources)
  }

  @Test def multipleSourceDirsWithInclusionFilters() {
    val project = makeProject("allSources2", 
        ("/allSources2/src/main/scala", Array[IPath]("included/*"), Array[IPath](), null), 
        ("/allSources2/src/test/scala", Array[IPath]("included2/*"), Array[IPath](), null)
        ) 
        
    val included = List("src/main/scala/included/A.scala", "src/test/scala/included2/B.scala")
    val notIncluded = List("src/main/scala/Z.scala", "src/test/scala/non-included/Y.scala")
    val srcPathsIn = (for (path <- included) yield SDTTestUtils.addFileToProject(project.underlying, path, "")).toSet
    
    for (path <- notIncluded) SDTTestUtils.addFileToProject(project.underlying, path, "")
    
    val allSources = project.allSourceFiles()
    Assert.assertEquals("All sources with inclusion filters", srcPathsIn, allSources)
  }
  
  @Test def multipleSourceDirsWithExclusionFilters() {
    val project = makeProject("allSources3", 
        ("/allSources3/src/main/scala", Array[IPath](), Array[IPath]("excluded/*.scala", "excluded2/*"), null), 
        ("/allSources3/src/test/scala", Array[IPath](), Array[IPath]("excluded/*"), null)
        ) 
        
    val included = List("src/main/scala/included/A.scala", "src/test/scala/included2/B.scala", "src/main/scala/excluded/included-again/C.scala")
    val notIncluded = List("src/main/scala/excluded/Z.scala", "src/main/scala/excluded2/X.scala", "src/test/scala/excluded/Y.scala")
    val srcPathsIn = (for (path <- included) yield SDTTestUtils.addFileToProject(project.underlying, path, "")).toSet
    
    for (path <- notIncluded) SDTTestUtils.addFileToProject(project.underlying, path, "")
    
    val allSources = project.allSourceFiles()
    Assert.assertEquals("All sources with exclusion filters", srcPathsIn, allSources)
  }
  
  @Test def multipleSourceDirsWithInclusionAndExclusionFilters() {
    val project = makeProject("allSources4", 
        ("/allSources4/src/main/scala", Array[IPath]("included/*"), Array[IPath]("included/excluded-again/*.scala", "excluded2/*"), null), 
        ("/allSources4/src/test/scala", Array[IPath](), Array[IPath]("excluded/*"), null)
        ) 
        
    val included = List("src/main/scala/included/A.scala", "src/test/scala/included/included-again/B.scala")
    val notIncluded = List("src/main/scala/included/excluded-again/Z.scala", "src/main/scala/excluded2/X.scala")
    val srcPathsIn = (for (path <- included) yield SDTTestUtils.addFileToProject(project.underlying, path, "")).toSet
    
    for (path <- notIncluded) SDTTestUtils.addFileToProject(project.underlying, path, "")
    
    val allSources = project.allSourceFiles()
    Assert.assertEquals("All sources with exclusion filters", srcPathsIn, allSources)
  }

  @Test def overlappingRootAndSourceFolder() {
    val project = makeProject("allSources5",
      ("/allSources5", Array[IPath](), Array[IPath]("src/", "included/excluded/*.scala", "excluded2/*"), "/allSources5/bin"),
      ("/allSources5/src/test/scala", Array[IPath](), Array[IPath](), null))

    val included = List("included/A.scala", "B.scala", "included-again/C.scala", "src/test/scala/D.scala")
    val notIncluded = List("bin/Z.scala", "included/excluded/X.scala")
    val srcPathsIn = (for (path <- included) yield SDTTestUtils.addFileToProject(project.underlying, path, "")).toSet

    for (path <- notIncluded) SDTTestUtils.addFileToProject(project.underlying, path, "")

    val allSources = project.allSourceFiles()
    Assert.assertEquals("All sources with overlapping root and source folders filters", srcPathsIn, allSources)
  }
}
