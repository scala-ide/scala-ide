package scala.tools.eclipse
package testsetup

import org.eclipse.jdt.core.IJavaProject
import org.eclipse.jdt.core.JavaCore
import org.eclipse.core.runtime.FileLocator
import org.eclipse.core.runtime.Platform
import java.io.{ ByteArrayInputStream, File, IOException, InputStream }
import org.eclipse.core.resources.{ IContainer, IFile, IFolder, IProject, IProjectDescription, ResourcesPlugin }
import org.eclipse.core.runtime.{ IPath, Path }
import scala.tools.eclipse.util.{ OSGiUtils, EclipseUtils }
import scala.tools.nsc.util.SourceFile
import scala.collection.mutable
import scala.util.matching.Regex
import org.eclipse.jdt.core.ICompilationUnit
import org.eclipse.jdt.core.IJavaModelMarker
import org.eclipse.core.resources.IResource
import org.eclipse.core.resources.IncrementalProjectBuilder
import org.eclipse.core.resources.IMarker
import org.eclipse.jdt.core.IPackageFragment
import org.eclipse.jdt.core.IClasspathEntry

/** Utility functions for setting up test projects.
 *
 *  @author Miles Sabin
 */
object SDTTestUtils {

  def sourceWorkspaceLoc(bundleName: String) = {
    val bundle = Platform.getBundle(bundleName) //"org.scala-ide.sdt.core.tests"
    OSGiUtils.pathInBundle(bundle, File.separatorChar + "test-workspace").get
  }

  /** Enable workspace auto-building */
  def enableAutoBuild(enable: Boolean) {
    // auto-building is on
    val desc = SDTTestUtils.workspace.getDescription
    desc.setAutoBuilding(enable)
    SDTTestUtils.workspace.setDescription(desc)
  }

  enableAutoBuild(false)

  /** Return the Java problem markers corresponding to the given compilation unit. */
  def findProblemMarkers(unit: ICompilationUnit) =
    unit.getUnderlyingResource().findMarkers(IJavaModelMarker.JAVA_MODEL_PROBLEM_MARKER, true, IResource.DEPTH_INFINITE)

  lazy val workspace = ResourcesPlugin.getWorkspace

  /** Setup the project in the target workspace. The 'name' project should
   *  exist in the source workspace.
   */
  def setupProject(name: String, bundleName: String): ScalaProject = {
    EclipseUtils.workspaceRunnableIn(workspace) { monitor =>
      val wspaceLoc = workspace.getRoot.getLocation
      val src = new File(sourceWorkspaceLoc(bundleName).toFile().getAbsolutePath + File.separatorChar + name)
      val dst = new File(wspaceLoc.toFile().getAbsolutePath + File.separatorChar + name)
      println("copying %s to %s".format(src, dst))
      FileUtils.copyDirectory(src, dst)
      val project = workspace.getRoot.getProject(name)
      project.create(null)
      project.open(null)
      JavaCore.create(project)
    }
    ScalaPlugin.plugin.getScalaProject(workspace.getRoot.getProject(name))
  }

  /** Return all positions (offsets) of the given str in the given source file.
   */
  def positionsOf(source: Array[Char], str: String): Seq[Int] = {
    val buf = new mutable.ListBuffer[Int]
    var pos = source.indexOfSlice(str)
    while (pos >= 0) {
      buf += pos - 1 // we need the position before the first character of this marker
      pos = source.indexOfSlice(str, pos + 1)
    }
    buf.toList
  }

  /** Return all positions and the number in the given marker. The marker is
   *  wrapped by /**/, and the method returns matches for /*[0-9]+*/, as a sequence
   *  of pairs (offset, parsedNumber)
   */
  def markersOf(source: Array[Char], prefix: String): Seq[(Int, Int)] = {
    val regex = """\/\*%s([0-9]+)\*/""".format(prefix).r
    val buf = new mutable.ListBuffer[(Int, Int)]
    val it = regex.findAllIn(source)
    for (m <- it) {
      buf += ((it.start, it.group(1).toInt))
    }

    buf.toSeq
  }

  def deleteRecursive(d: File) {
    if (d.exists) {
      val filesOpt = Option(d.listFiles)
      for (files <- filesOpt; file <- files)
        if (file.isDirectory)
          deleteRecursive(file)
        else
          file.delete
      d.delete
    }
  }

  def createTempDir(name: String): File = {
    val userHome = new File(System.getProperty("user.home")).getAbsolutePath
    val rootDir = new File(userHome, "SDTCoreTestTempDir")
    val result = new File(rootDir, name)
    if (result.exists)
      deleteRecursive(result)
    result
  }

  def deleteTempDirs() {
    val userHome = new File(System.getProperty("user.home")).getAbsolutePath
    val rootDir = new File(userHome, "SDTCoreTestTempDir")
    if (rootDir.exists)
      deleteRecursive(rootDir)
  }

  /** Add a new file to the given project. The given path is relative to the
   *  project.
   *
   *  The file must not exist.
   */
  def addFileToProject(project: IProject, path: String, content: String): IFile = {
    val filePath = new Path(path)
    val dirNames = filePath.segments.init // last segment is the file
    dirNames.foldLeft(project: IContainer) { (container, segment) =>
      val folder = container.getFolder(new Path(segment))
      if (!folder.exists())
        folder.create(false, true, null)
      folder
    }
    val file = project.getFile(filePath);
    file.create(new ByteArrayInputStream(content.getBytes(project.getDefaultCharset())), true, null);
    file
  }

  def changeContentOfFile(file: IFile, newContent: String, encoding: String = workspace.getRoot.getDefaultCharset()): IFile = {
    file.setContents(new ByteArrayInputStream(newContent.getBytes(encoding)), 0, null)
    file
  }

  def getProblemMarkers(units: ICompilationUnit*): List[IMarker] = {
    units.flatMap(findProblemMarkers).toList
  }

  def getErrorMessages(units: ICompilationUnit*): List[String] =
    for (p <- getProblemMarkers(units: _*)) yield p.getAttribute(IMarker.MESSAGE).toString

  def buildWith(resource: IResource, contents: String, unitsToWatch: Seq[ICompilationUnit]): List[String] = {
    SDTTestUtils.changeContentOfFile(resource.asInstanceOf[IFile], contents)

    println("=== Rebuilding workspace === ")
    SDTTestUtils.workspace.build(IncrementalProjectBuilder.INCREMENTAL_BUILD, null)

    val problems = getProblemMarkers(unitsToWatch: _*)

    for (p <- problems) yield p.getAttribute(IMarker.MESSAGE).toString
  }

  def createProjectInLocalFileSystem(parentFile: File, projectName: String): IProject = {
    val project = ResourcesPlugin.getWorkspace.getRoot.getProject(projectName)
    if (project.exists)
      project.delete(true, null)
    val testFile = new File(parentFile, projectName)
    if (testFile.exists)
      deleteRecursive(testFile)

    val desc = ResourcesPlugin.getWorkspace.newProjectDescription(projectName)
    desc.setLocation(new Path(new File(parentFile, projectName).getPath))
    project.create(desc, null)
    project.open(null)
    project
  }

  def slurpAndClose(inputStream: InputStream): String = {
    val stringBuilder = new StringBuilder
    try {
      var ch: Int = 0
      while ({ ch = inputStream.read; ch } != -1) {
        stringBuilder.append(ch.toChar)
      }
    } finally {
      inputStream.close
    }
    stringBuilder.toString
  }
  
  val simulator = new EclipseUserSimulator


  def createSourcePackage(name: String)(project: ScalaProject): IPackageFragment =
    project.javaProject.getPackageFragmentRoot(project.underlying.getFolder("/src")).createPackageFragment(name, true, null)

  def addToClasspath(prj: ScalaProject, entries: IClasspathEntry*) {
    val existing = prj.javaProject.getRawClasspath
    prj.javaProject.setRawClasspath(existing ++ entries, null)
  }

  def createProjects(names: String*): Seq[ScalaProject] =
    names map (n => simulator.createProjectInWorkspace(n, true))

  def deleteProjects(projects: ScalaProject*) {
    util.EclipseUtils.workspaceRunnableIn(ScalaPlugin.plugin.workspaceRoot.getWorkspace) { _ =>
      projects foreach (_.underlying.delete(true, null))
    }
  }
}
