package org.scalaide.core
package sbtbuilder

import org.junit.Test
import org.junit.Assert
import org.eclipse.core.resources.IContainer
import org.eclipse.core.runtime.Path
import org.eclipse.jdt.core.JavaCore
import org.eclipse.jdt.core.IClasspathEntry
import org.eclipse.core.runtime.IPath
import org.scalaide.core.IScalaProject
import testsetup.SDTTestUtils
import org.junit.Before

/** Test simple operations involving the source and output
 *  folders for a project.
 */
class OutputFoldersTest {
  val NoContent: String = ""

  implicit def stringsArePaths(str: String): Path = new Path(str)

  @Before def setupBuild(): Unit = {
    SDTTestUtils.enableAutoBuild(false)
  }

  @Test def defaultOutputDirectory(): Unit = {
    val projectName = "test-simple-output-projects"
    val project = SDTTestUtils.createProjectInWorkspace(projectName)
    Assert.assertEquals("Default output directory", Seq(new Path("/%s/bin".format(projectName))), project.outputFolders)
    project.javaProject.setOutputLocation(new Path("/%s/other-bin".format(projectName)), null)
    Assert.assertEquals("Default output directory", Seq(new Path("/%s/other-bin".format(projectName))), project.outputFolders)

    val Seq((srcPath, _)) = project.sourceOutputFolders.toSeq
    Assert.assertEquals("Source path", new Path("/%s/src".format(projectName)), srcPath.getFullPath())
  }

  @Test def multipleOutputDirs(): Unit = {
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

  @Test def missingSourceDirectory(): Unit = {
    val projectName = "missingSources"
    val srcMain = new Path("/" +projectName + "/src/main/scala")
    val targetMain = new Path("/" +projectName + "/target/classes")
    val srcTest = new Path("/" +projectName + "/src/test/scala")

    val project = makeDefaultLayoutProject(projectName)

    project.underlying.getWorkspace().getRoot().getFolder(srcTest).delete(true, null)

    // both source directories are defined in Eclipse classpath ...
    val cpes = project.javaProject.getResolvedClasspath(true)
    val srcEntryPaths = cpes.filter(_.getEntryKind == IClasspathEntry.CPE_SOURCE).map(_.getPath)

    Assert.assertEquals("Source CPEs", Seq(srcMain, srcTest), srcEntryPaths.toSeq)

    // ... but only the one that exists in the workspace  is passed to Scala compiler classpath
    val sourcesOutputs = project.sourceOutputFolders

    val sources = sourcesOutputs.map(_._1.getFullPath())
    val outputs = sourcesOutputs.map(_._2.getFullPath())

    Assert.assertEquals("Sources", Seq(srcMain), sources)
    Assert.assertEquals("Outputs", Seq(targetMain), outputs)
  }

  /** Create a project with the specified source folders, inclusion and exclusion patterns */
  private def makeProject(name: String, sourceFolders: (IPath, Array[IPath], Array[IPath], IPath)*): IScalaProject = {

    val project = SDTTestUtils.createProjectInWorkspace(name, sourceFolders.isEmpty)

    val srcEntries =
      for ((dirPath, inclPats, exclPats, binPath) <- sourceFolders) yield {
        for (path <- Seq(Some(dirPath), Option(binPath)))
          path foreach { (path) => ensureFolderExists(project.underlying, path) }

        JavaCore.newSourceEntry(dirPath, inclPats, exclPats, binPath)
      }

    project.javaProject.setRawClasspath((project.javaProject.getRawClasspath() ++ srcEntries).toArray, null);
    project
  }

  /** Ensure that given folder exists in Eclipse workspace */
  private def ensureFolderExists(top: IContainer, path: IPath): Unit = {
    def ensureFolderExists(container: IContainer, segments: List[String]): Unit = {
      if (!container.exists())
        container.getParent().getFolder(container.getName()).create(false, true, null)
      segments match {
        case Nil =>
        case segment :: rest =>
          ensureFolderExists(container.getFolder(segment), rest)
      }
    }
    ensureFolderExists(top, path.segments.toList.drop(1))
  }

  /** Create a project with src/main/scala and src/test/scala, without any source filters. */
  private def makeDefaultLayoutProject(projectName: String) = {
    val srcMain = new Path("/" +projectName + "/src/main/scala")
    val targetMain = new Path("/" +projectName + "/target/classes")
    val srcTest = new Path("/" +projectName + "/src/test/scala")
    val targetTest = new Path("/" +projectName + "/target/test-classes")

    makeProject(projectName, (srcMain, Array(), Array(), targetMain), (srcTest, Array(), Array(), targetTest))
  }

  @Test def multipleSourceDirsWithoutFilters(): Unit = {

    val project = makeDefaultLayoutProject("allSources1")

    val sources = List("src/main/scala/A.scala", "src/main/scala/B.scala", "src/test/scala/C.scala")
    val srcPaths = (for (path <- sources) yield SDTTestUtils.addFileToProject(project.underlying, path, NoContent)).toSet

    val allSources = project.allSourceFiles()
    Assert.assertEquals("All sources without filters", srcPaths, allSources)
  }

  @Test def multipleSourceDirsWithInclusionFilters(): Unit = {
    val project = makeProject("allSources2",
        ("/allSources2/src/main/scala", Array[IPath]("included/*"), Array[IPath](), null),
        ("/allSources2/src/test/scala", Array[IPath]("included2/*"), Array[IPath](), null)
        )

    val included = List("src/main/scala/included/A.scala", "src/test/scala/included2/B.scala")
    val notIncluded = List("src/main/scala/Z.scala", "src/test/scala/non-included/Y.scala")
    val srcPathsIn = (for (path <- included) yield SDTTestUtils.addFileToProject(project.underlying, path, NoContent)).toSet

    for (path <- notIncluded) SDTTestUtils.addFileToProject(project.underlying, path, NoContent)

    val allSources = project.allSourceFiles()
    Assert.assertEquals("All sources with inclusion filters", srcPathsIn, allSources)
  }

  @Test def multipleSourceDirsWithExclusionFilters(): Unit = {
    val project = makeProject("allSources3",
        ("/allSources3/src/main/scala", Array[IPath](), Array[IPath]("excluded/*.scala", "excluded2/*"), null),
        ("/allSources3/src/test/scala", Array[IPath](), Array[IPath]("excluded/*"), null)
        )

    val included = List("src/main/scala/included/A.scala", "src/test/scala/included2/B.scala", "src/main/scala/excluded/included-again/C.scala")
    val notIncluded = List("src/main/scala/excluded/Z.scala", "src/main/scala/excluded2/X.scala", "src/test/scala/excluded/Y.scala")
    val srcPathsIn = (for (path <- included) yield SDTTestUtils.addFileToProject(project.underlying, path, NoContent)).toSet

    for (path <- notIncluded) SDTTestUtils.addFileToProject(project.underlying, path, NoContent)

    val allSources = project.allSourceFiles()
    Assert.assertEquals("All sources with exclusion filters", srcPathsIn, allSources)
  }

  @Test def multipleSourceDirsWithInclusionAndExclusionFilters(): Unit = {
    val project = makeProject("allSources4",
        ("/allSources4/src/main/scala", Array[IPath]("included/*"), Array[IPath]("included/excluded-again/*.scala", "excluded2/*"), null),
        ("/allSources4/src/test/scala", Array[IPath](), Array[IPath]("excluded/*"), null)
        )

    val included = List("src/main/scala/included/A.scala", "src/test/scala/included/included-again/B.scala")
    val notIncluded = List("src/main/scala/included/excluded-again/Z.scala", "src/main/scala/excluded2/X.scala")
    val srcPathsIn = (for (path <- included) yield SDTTestUtils.addFileToProject(project.underlying, path, NoContent)).toSet

    for (path <- notIncluded) SDTTestUtils.addFileToProject(project.underlying, path, NoContent)

    val allSources = project.allSourceFiles()
    Assert.assertEquals("All sources with exclusion filters", srcPathsIn, allSources)
  }

  @Test def overlappingRootAndSourceFolder(): Unit = {
    val project = makeProject("allSources5",
      ("/allSources5", Array[IPath](), Array[IPath]("src/", "included/excluded/*.scala", "excluded2/*"), "/allSources5/bin"),
      ("/allSources5/src/test/scala", Array[IPath](), Array[IPath](), null))

    val included = List("included/A.scala", "B.scala", "included-again/C.scala", "src/test/scala/D.scala")
    val notIncluded = List("bin/Z.scala", "included/excluded/X.scala")
    val srcPathsIn = (for (path <- included) yield SDTTestUtils.addFileToProject(project.underlying, path, NoContent)).toSet

    for (path <- notIncluded) SDTTestUtils.addFileToProject(project.underlying, path, NoContent)

    val allSources = project.allSourceFiles()
    Assert.assertEquals("All sources with overlapping root and source folders filters", srcPathsIn, allSources)
  }
}
