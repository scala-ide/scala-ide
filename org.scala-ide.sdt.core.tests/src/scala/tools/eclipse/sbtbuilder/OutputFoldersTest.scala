package scala.tools.eclipse.sbtbuilder
import org.junit.Test
import scala.tools.eclipse.EclipseUserSimulator
import org.junit.Assert
import org.eclipse.core.runtime.Path
import org.eclipse.jdt.core.JavaCore

/** Test simple operations involving the source and output
 *  folders for a project.
 */
class OutputFoldersTest {
  val simulator = new EclipseUserSimulator
  
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
    
    val project = simulator.createProjectInWorkspace(projectName)

    val srcMainEntry = JavaCore.newSourceEntry(srcMain, null, targetMain)
    val srcTestEntry = JavaCore.newSourceEntry(srcTest, null, targetTest)
    
    project.javaProject.setRawClasspath(Array(srcMainEntry, srcTestEntry), null);

    val sourcesOutputs = project.sourceOutputFolders
    
    val sources = sourcesOutputs.map(_._1.getFullPath())
    val outputs = sourcesOutputs.map(_._2.getFullPath())

    Assert.assertEquals("Sources", Seq(srcMain, srcTest), sources)
    Assert.assertEquals("Sources", Seq(targetMain, targetTest), outputs)
  }

}
