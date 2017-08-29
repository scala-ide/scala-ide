package org.scalaide.core
package testsetup

import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.util.concurrent.TimeoutException

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

import org.eclipse.core.resources.IContainer
import org.eclipse.core.resources.IFile
import org.eclipse.core.resources.IMarker
import org.eclipse.core.resources.IProject
import org.eclipse.core.resources.IResource
import org.eclipse.core.resources.IncrementalProjectBuilder
import org.eclipse.core.resources.ResourcesPlugin
import org.eclipse.core.runtime.IPath
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.core.runtime.NullProgressMonitor
import org.eclipse.core.runtime.Path
import org.eclipse.core.runtime.Platform
import org.eclipse.core.runtime.preferences.ConfigurationScope
import org.eclipse.core.runtime.preferences.InstanceScope
import org.eclipse.jdt.core.IClasspathEntry
import org.eclipse.jdt.core.ICompilationUnit
import org.eclipse.jdt.core.IJavaModelMarker
import org.eclipse.jdt.core.IJavaProject
import org.eclipse.jdt.core.IPackageFragment
import org.eclipse.jdt.core.JavaCore
import org.eclipse.jdt.debug.core.JDIDebugModel
import org.eclipse.jdt.launching.JavaRuntime
import org.scalaide.core.IScalaProject
import org.scalaide.core.compiler.IScalaPresentationCompiler
import org.scalaide.core.internal.ScalaPlugin
import org.scalaide.core.internal.project.ScalaProject
import org.scalaide.logging.HasLogger
import org.scalaide.util.eclipse.EclipseUtils
import org.scalaide.util.eclipse.OSGiUtils

/**
 * Utility functions for setting up test projects.
 *
 */
object SDTTestUtils extends HasLogger {

  enableAutoBuild(false)
  // Be nice to Mac users and use a default encoding other than MacRoman
  InstanceScope.INSTANCE.getNode(SdtConstants.PluginId).put(ResourcesPlugin.PREF_ENCODING, "UTF-8")

  lazy val workspace = ResourcesPlugin.getWorkspace

  def sourceWorkspaceLoc(bundleName: String): IPath = {
    val bundle = Platform.getBundle(bundleName)
    OSGiUtils.pathInBundle(bundle, File.separatorChar + "test-workspace").get
  }

  def setJdiRequestTimeout(timeout: Int): Int = {
    val debugSettings = ConfigurationScope.INSTANCE.getNode(JDIDebugModel.getPluginIdentifier())
    val previousRequestTimeout = debugSettings.getInt(JDIDebugModel.PREF_REQUEST_TIMEOUT, JDIDebugModel.DEF_REQUEST_TIMEOUT)
    debugSettings.putInt(JDIDebugModel.PREF_REQUEST_TIMEOUT, timeout)
    previousRequestTimeout
  }

  /** Enable workspace auto-building */
  def enableAutoBuild(enable: Boolean): Unit = {
    // auto-building is on
    val desc = workspace.getDescription
    desc.setAutoBuilding(enable)
    workspace.setDescription(desc)
  }

  /** Return the Java problem markers corresponding to the given compilation unit. */
  def findProblemMarkers(unit: ICompilationUnit): Array[IMarker] =
    unit.getUnderlyingResource().findMarkers(IJavaModelMarker.JAVA_MODEL_PROBLEM_MARKER, true, IResource.DEPTH_INFINITE)

  def findProjectProblemMarkers(project: IProject, types: String*): Seq[IMarker] =
    for {
      typ <- types
      markers <- project.findMarkers(typ, false, IResource.DEPTH_INFINITE)
    } yield markers

  def markersMessages(markers: List[IMarker]): List[String] =
    markers.map(_.getAttribute(IMarker.MESSAGE).asInstanceOf[String])

  /**
   * Setup the project in the target workspace. The 'name' project should
   *  exist in the source workspace.
   */
  def setupProject(name: String, bundleName: String): IScalaProject =
    internalSetupProject(name, bundleName)

  private[core] def internalSetupProject(name: String, bundleName: String)(implicit progressMonitor: IProgressMonitor = new NullProgressMonitor): ScalaProject = {
    EclipseUtils.workspaceRunnableIn(workspace) { monitor =>
      val wspaceLoc = workspace.getRoot.getLocation
      val src = new File(sourceWorkspaceLoc(bundleName).toFile().getAbsolutePath + File.separatorChar + name)
      val dst = new File(wspaceLoc.toFile().getAbsolutePath + File.separatorChar + name)
      logger.debug("copying %s to %s".format(src, dst))
      FileUtils.copyDirectory(src, dst)
      val project = workspace.getRoot.getProject(name)
      project.create(progressMonitor)
      project.open(progressMonitor)
      project.setDefaultCharset("UTF-8", progressMonitor)
      JavaCore.create(project)
    }
    ScalaPlugin().getScalaProject(workspace.getRoot.getProject(name))
  }

  /**
   * Return all positions (offsets) of the given str in the given source file.
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

  /**
   * Return all positions and the number in the given marker. The marker is
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

  def deleteRecursive(d: File): Unit = {
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

  def deleteTempDirs(): Unit = {
    val userHome = new File(System.getProperty("user.home")).getAbsolutePath
    val rootDir = new File(userHome, "SDTCoreTestTempDir")
    if (rootDir.exists)
      deleteRecursive(rootDir)
  }

  /**
   * Add a new file to the given project. The given path is relative to the
   *  project.
   *
   *  The file must not exist.
   */
  def addFileToProject(project: IProject, path: String, content: String): IFile =
    addFileToProject(project, path, content.getBytes(project.getDefaultCharset()))

  def addFileToProject(project: IProject, path: String, content: Array[Byte]): IFile = {
    val filePath = new Path(path)
    val dirNames = filePath.segments.init // last segment is the file
    dirNames.foldLeft(project: IContainer) { (container, segment) =>
      val folder = container.getFolder(new Path(segment))
      if (!folder.exists())
        folder.create(false, true, null)
      folder
    }
    val file = project.getFile(filePath);
    file.create(new ByteArrayInputStream(content), true, null)
    file
  }

  def changeContentOfFile(file: IFile, newContent: String, encoding: String = workspace.getRoot.getDefaultCharset()): IFile = {
    file.setContents(new ByteArrayInputStream(newContent.getBytes(encoding)), 0, null)
    file
  }

  def getProblemMarkers(units: ICompilationUnit*): List[IMarker] = {
    units.flatMap(findProblemMarkers).toList
  }

  def getErrorMessages(project: IProject): Seq[(Int, String)] = {
    for (m <- project.findMarkers(IJavaModelMarker.JAVA_MODEL_PROBLEM_MARKER, true, IResource.DEPTH_INFINITE))
      yield (m.getAttribute(IMarker.SEVERITY).asInstanceOf[Int], m.getAttribute(IMarker.MESSAGE).toString)
  }

  def getErrorMessages(units: ICompilationUnit*): List[String] =
    for (p <- getProblemMarkers(units: _*)) yield p.getAttribute(IMarker.MESSAGE).toString

  def buildWith(resource: IResource, contents: String, unitsToWatch: Seq[ICompilationUnit]): List[String] = {
    SDTTestUtils.changeContentOfFile(resource.asInstanceOf[IFile], contents)

    logger.debug("=== Rebuilding workspace === ")
    SDTTestUtils.workspace.build(IncrementalProjectBuilder.INCREMENTAL_BUILD, null)

    val problems = getProblemMarkers(unitsToWatch: _*)

    for (p <- problems) yield p.getAttribute(IMarker.MESSAGE).toString
  }

  def createProjectInLocalFileSystem(parentFile: File, projectName: String): IProject = {
    val project = workspace.getRoot.getProject(projectName)
    if (project.exists)
      project.delete(true, null)
    val testFile = new File(parentFile, projectName)
    if (testFile.exists)
      deleteRecursive(testFile)

    val desc = workspace.newProjectDescription(projectName)
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

  def findMarker(marker: String) = new {
    import org.eclipse.jdt.internal.compiler.env.ICompilationUnit
    def in(unit: ICompilationUnit): Seq[Int] = {
      val contents = unit.getContents()
      SDTTestUtils.positionsOf(contents, marker)
    }
  }

  def createSourcePackage(name: String)(project: IScalaProject): IPackageFragment =
    project.javaProject.getPackageFragmentRoot(project.underlying.getFolder("/src")).createPackageFragment(name, true, null)

  def createCompilationUnit(pack: IPackageFragment, name: String, sourceCode: String, force: Boolean = false): ICompilationUnit = {
    BlockingProgressMonitor.waitUntilDone(pack.createCompilationUnit(name, sourceCode, force, _))
  }

  def addToClasspath(prj: IScalaProject, entries: IClasspathEntry*): Unit = {
    val existing = prj.javaProject.getRawClasspath
    prj.javaProject.setRawClasspath(existing ++ entries, null)
  }

  /** Create Scala projects, equiped with the Scala nature, Scala library container and a '/src' folder. */
  def createProjects(names: String*): Seq[IScalaProject] =
    names map (n => createProjectInWorkspace(n, true))

  private[core] def internalCreateProjects(names: String*): Seq[ScalaProject] =
    names map (n => internalCreateProjectInWorkspace(n, withSourceRootOnly))

  def deleteProjects(projects: IScalaProject*)(implicit progressMonitor: IProgressMonitor = new NullProgressMonitor): Unit = {
    EclipseUtils.workspaceRunnableIn(EclipseUtils.workspaceRoot.getWorkspace, progressMonitor) { _ =>
      projects foreach (_.underlying.delete(true, progressMonitor))
    }
  }

  /** Wait until `pred` is true, or timeout (in ms). */
  def waitUntil(timeout: Int, withTimeoutException: Boolean = false)(pred: => Boolean): Unit = {
    val start = System.currentTimeMillis()
    var cond = pred
    while ((System.currentTimeMillis() < start + timeout) && !cond) {
      Thread.sleep(100)
      cond = pred
    }
    if (!cond && withTimeoutException)
      throw new TimeoutException(s"Predicate is not fulfiled after declared time limit ($timeout millis).")
  }

  /**
   * Allows to run code that can access the presentation compiler. The code is
   * executed in a separate project inside of the workspace. The project is created
   * when this method is called and will be removed when it is left.
   *
   * @param testProjectName
   *        The name of the test project the code should be executed in
   * @param f
   *        the function executed inside of the presentation compiler
   *
   * @example {{{
   * testWithCompiler("testproject") { compiler =>
   *   import compiler._
   *   // use compiler member
   * }
   * }}}
   */
  def testWithCompiler[A](testProjectName: String)(f: IScalaPresentationCompiler => A): Unit = {
    var projectSetup: TestProjectSetup = null

    try {
      val scalaProject = createProjectInWorkspace(testProjectName, withSourceRoot = true)

      projectSetup = new TestProjectSetup(testProjectName) {
        override lazy val project = scalaProject
      }
      projectSetup.project.presentationCompiler { c => f(c) }
    } finally deleteProjects(projectSetup.project)
  }

  /**
   * Create a project in the current workspace. If `withSourceRoot` is true,
   *  it creates a source folder called `src`.
   */
  def createProjectInWorkspace(projectName: String, withSourceRoot: Boolean = true): IScalaProject =
    internalCreateProjectInWorkspace(projectName, if (withSourceRoot) withSourceRootOnly else withNoSourceRoot)

  def createProjectInWorkspace(projectName: String, withSrcOutputStructure: SrcPathOutputEntry): IScalaProject =
    internalCreateProjectInWorkspace(projectName, withSrcOutputStructure)

  type SrcPathOutputEntry = (IProject, IJavaProject) => Seq[IClasspathEntry]

  private def withNoSourceRoot: SrcPathOutputEntry = (_, _) => Seq.empty[IClasspathEntry]

  private def withSourceRootOnly: SrcPathOutputEntry = (thisProject, correspondingJavaProject) => {
    val sourceFolder = thisProject.getFolder("/src")
    sourceFolder.create( /* force = */ false, /* local = */ true, /* monitor = */ null)
    val root = correspondingJavaProject.getPackageFragmentRoot(sourceFolder)
    Seq(JavaCore.newSourceEntry(root.getPath()))
  }

  private[core] def internalCreateProjectInWorkspace(projectName: String, withSourceRoot: Boolean): ScalaProject =
    internalCreateProjectInWorkspace(projectName, if (withSourceRoot) withSourceRootOnly else withNoSourceRoot)

  final def createJavaProjectInWorkspace(projectName: String, withSourceFolders: SrcPathOutputEntry): IJavaProject = {
    val workspaceRoot = workspace.getRoot()
    val project = workspaceRoot.getProject(projectName)
    project.create(null)
    project.open(null)

    val description = project.getDescription()
    description.setNatureIds(Array(JavaCore.NATURE_ID))
    project.setDescription(description, null)

    val javaProject = JavaCore.create(project)
    javaProject.setOutputLocation(new Path("/" + projectName + "/bin"), null)

    val entries = new ArrayBuffer[IClasspathEntry]()
    entries += JavaRuntime.getDefaultJREContainerEntry()

    entries ++= withSourceFolders(project, javaProject)

    javaProject.setRawClasspath(entries.toArray[IClasspathEntry], null)
    javaProject
  }

  private[core] def internalCreateProjectInWorkspace(projectName: String, withSourceFolders: SrcPathOutputEntry): ScalaProject = {
    def withScalaFolders(project: IProject, jProject: IJavaProject) =
      withSourceFolders(project, jProject) ++ Seq(JavaCore.newContainerEntry(Path.fromPortableString(SdtConstants.ScalaLibContId)))
    def addScalaNature(project: IProject) = {
      val description = project.getDescription
      description.setNatureIds(SdtConstants.NatureId +: description.getNatureIds)
      project.setDescription(description, null)
      project
    }

    ScalaPlugin().getScalaProject(addScalaNature(createJavaProjectInWorkspace(projectName, withScalaFolders).getProject))
  }

  def withWorkspacePreference[A](name: String, value: Boolean)(thunk: => A): A = {
    val store = ScalaPlugin().getPreferenceStore
    val old = store.getBoolean(name)
    try {
      store.setValue(name, value)
      thunk
    } finally
      store.setValue(name, old)
  }

  def buildWorkspace(): Unit =
    workspace.build(IncrementalProjectBuilder.INCREMENTAL_BUILD, new NullProgressMonitor)
}
