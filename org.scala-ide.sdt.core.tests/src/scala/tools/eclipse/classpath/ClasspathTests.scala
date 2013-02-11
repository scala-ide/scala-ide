package scala.tools.eclipse.classpath

import scala.tools.eclipse.testsetup.TestProjectSetup
import org.junit.Assert._
import org.junit.Test
import org.eclipse.jdt.core.JavaCore
import org.eclipse.core.resources.IResource
import org.eclipse.core.resources.IncrementalProjectBuilder
import org.eclipse.core.runtime.NullProgressMonitor
import org.eclipse.core.runtime.Path
import org.junit.Before
import org.eclipse.jdt.core.IClasspathEntry
import org.eclipse.core.resources.IMarker
import scala.tools.eclipse.ScalaPlugin
import org.junit.After
import org.junit.Ignore
import scala.tools.eclipse.EclipseUserSimulator
import scala.tools.eclipse.ScalaProject
import scala.tools.eclipse.properties.CompilerSettings
import scala.tools.eclipse.testsetup.SDTTestUtils
import scala.tools.eclipse.util.EclipseUtils
import org.eclipse.core.runtime.Status
import org.eclipse.core.runtime.jobs.IJobChangeEvent
import org.eclipse.core.runtime.jobs.JobChangeAdapter

object ClasspathTests extends TestProjectSetup("classpath")

/** This test class relies on JARs located in "test-workspace/classpath/lib/${Scala.shortVersion}.x". 
 *  If you need to support a new Scala major version, you'll have to:
 * 
 *  - Add a new folder under "test-workspace/classpath/lib/". Name the folder "${Scala.shortVersion}.x".
 *  - In the freshly created folder:
 *     + Copy "binary-scala-library" from on of the existing "test-workspace/classpath/lib/${Scala.shortVersion}.x/binary-scala-library"
 *     + Inside the copied "binary-scala-library", update the "version.number" in the library.properties.
 *     + Create a JAR of "binary-scala-library" and call it "scala-library.jar"
 *     + Create a copy of the freshly created "scala-library.jar" and name it "my-scala-library.jar"
 *  - Update the logic in `ClasspathTests.createIncompatibleScalaLibraryEntry`
 */
class ClasspathTests {
  
  import ClasspathTests._
  val classpathMarkerId = ScalaPlugin.plugin.classpathProblemMarkerId

  
  val simulator = new EclipseUserSimulator
  
  /**
   * The default classpath, with the eclipse scala container.
   */
  val baseRawClasspath= project.javaProject.getRawClasspath()
  
  /**
   * The classpath, with the eclipse scala container removed.
   */
  def cleanRawClasspath= for (classpathEntry <- baseRawClasspath
        if classpathEntry.getPath().toPortableString() != "org.scala-ide.sdt.launching.SCALA_CONTAINER")
      yield classpathEntry
      
  @After
  def resetClasspath() {
    setRawClasspathAndCheckMarkers(baseRawClasspath, 0, 0)
  }
  
  /**
   * The scala library is defined as part of the eclipse container in the classpath (default case)
   */
  @Test
  def eclipseContainerScalaLibrary() {
    setRawClasspathAndCheckMarkers(baseRawClasspath, 0, 0)
  }

  /** Major binary-incompatible library on the classpath
   */
  @Test
  def binaryIncompatibleLibrary() {
    val majorMinor = getIncompatibleScalaVersion
    setRawClasspathAndCheckMarkers(baseRawClasspath :+ newLibraryEntry("specs2_%s.2-0.12.3.jar".format(majorMinor)), expectedWarnings = 0, expectedErrors = 1)
  }

  /** Major binary-incompatible library on the classpath, with short version in the name
   */
  @Test
  def shortBinaryIncompatibleLibrary() {
    val majorMinor = getIncompatibleScalaVersion
    setRawClasspathAndCheckMarkers(baseRawClasspath :+ newLibraryEntry("specs2_%s-0.12.3.jar".format(majorMinor)), expectedWarnings = 0, expectedErrors = 1)
  }

  /** Major binary-incompatible library on the classpath, with short version in the name
   *  and without a version of its own
   */
  @Test
  def shortBinaryIncompatibleLibraryWithoutSuffix() {
    val majorMinor = getIncompatibleScalaVersion
    setRawClasspathAndCheckMarkers(baseRawClasspath :+ newLibraryEntry("specs2_%s.jar".format(majorMinor)), expectedWarnings = 0, expectedErrors = 1)
  }

  /** Major binary-incompatible library on the classpath
   */
  @Test
  def shortBinaryIncompatibleLibraries() {
    val majorMinor = getIncompatibleScalaVersion
    setRawClasspathAndCheckMarkers(baseRawClasspath
      :+ newLibraryEntry("specs2_%s.0-0.12.3.jar".format(ScalaPlugin.plugin.shortScalaVer)) // this one is compatible
      :+ newLibraryEntry("somlib_%s-0.1.0-SNAPSHOT.jar".format(majorMinor)), // this one is not
      expectedWarnings = 0, expectedErrors = 1)
  }

  /** Major binary-incompatible library on the classpath
   */
  @Test
  def shortBinaryIncompatibleLibraries2() {
    val majorMinor = getIncompatibleScalaVersion
    setRawClasspathAndCheckMarkers(baseRawClasspath
      :+ newLibraryEntry("specs2_%s.0-0.12.3.jar".format(majorMinor)) // this one is not compatible
      :+ newLibraryEntry("somlib_%s-0.1.0-SNAPSHOT.jar".format(majorMinor)), // neither is this one
      expectedWarnings = 0, expectedErrors = 2)
  }

  /** Major binary-incompatible library on the classpath, with and without version suffix of their own.
   */
  @Test
  def shortBinaryIncompatibleLibraries3() {
    val majorMinor = getIncompatibleScalaVersion
    setRawClasspathAndCheckMarkers(baseRawClasspath
      :+ newLibraryEntry("specs2_%s.0-0.12.3.jar".format(majorMinor)) // this one is not compatible
      :+ newLibraryEntry("somlib_%s.jar".format(majorMinor)) // neither is this one
      :+ newLibraryEntry("somlib_%s-0.1.0-SNAPSHOT.jar".format(majorMinor)), // neither is this one
      expectedWarnings = 0, expectedErrors = 3)
  }

  /** Major binary-compatible library on the classpath
   */
  @Test
  def binaryCompatibleLibrary() {
    setRawClasspathAndCheckMarkers(baseRawClasspath :+ newLibraryEntry("specs2_%s.2-0.12.3.jar".format(ScalaPlugin.plugin.shortScalaVer)), 0, 0)
  }

  /** Major binary-compatible library on the classpath, Eclipse style
   */
  @Test
  def binaryCompatibleLibraryEclipseNaming() {
    setRawClasspathAndCheckMarkers(baseRawClasspath :+ newLibraryEntry("org.scala-ide.sdt.aspects_2.1.0.nightly-2_10-201301251404-6e75290.jar".format(ScalaPlugin.plugin.shortScalaVer)), 0, 0)
  }
  /** Multiple binary-compatible libraries on the classpath
   */
  @Test
  def binaryCompatibleLibraries() {
    setRawClasspathAndCheckMarkers(baseRawClasspath
      :+ newLibraryEntry("specs2_%s.0-0.12.3.jar".format(ScalaPlugin.plugin.shortScalaVer))
      :+ newLibraryEntry("somelib_%s-0.1.0-SNAPSHOT.jar".format(ScalaPlugin.plugin.shortScalaVer)),
      0, 0)
  }

  /** Multiple binary-compatible libraries on the classpath, one is not cross-built.
   */
  @Test
  def binaryCompatibleLibrariesNonCrossBuilt() {
    setRawClasspathAndCheckMarkers(baseRawClasspath
      :+ newLibraryEntry("specs2_%s.0-0.12.3.jar".format(ScalaPlugin.plugin.shortScalaVer))
      :+ newLibraryEntry("somelib_%s-0.1.0-SNAPSHOT.jar".format(ScalaPlugin.plugin.shortScalaVer))
      :+ newLibraryEntry("somelib-0.1.0-SNAPSHOT.jar"),
      0, 0)
  }

  /** Major binary-incompatible library on the classpath, fixing removes error markers
   */
  @Test
  def fixBinaryIncompatibleLibrary() {
    val majorMinor = getIncompatibleScalaVersion
    setRawClasspathAndCheckMarkers(baseRawClasspath :+ newLibraryEntry("specs2_%s.2-0.12.3.jar".format(majorMinor)), expectedWarnings = 0, expectedErrors = 1)
    // this should fix it
    setRawClasspathAndCheckMarkers(baseRawClasspath :+ newLibraryEntry("specs2_%s.2-0.12.3.jar".format(ScalaPlugin.plugin.shortScalaVer)), expectedWarnings = 0, expectedErrors = 0)
  }
  
  /**
   * No scala library defined in the classpath
   */
  @Test
  def noScalaLibrary() {
    setRawClasspathAndCheckMarkers(cleanRawClasspath, 0, 1)
  }
  
  /**
   * Two scala library defined in the classpath, the eclipse container one, and one from the lib folder
   */
  @Test
  def twoScalaLibraries() {
    setRawClasspathAndCheckMarkers(baseRawClasspath :+  newLibraryEntry("scala-library.jar"), 1, 0)
  }
  
  /**
   * Two scala library defined in the classpath, the eclipse container one, and one with a different name.
   */
  @Test
  def twoScalaLibrariesWithDifferentName() {
    setRawClasspathAndCheckMarkers(baseRawClasspath :+  newLibraryEntry("my-scala-library.jar"), 1, 0)
  }
  
  /**
   * Two scala library defined in the classpath, the eclipse container one, and one incompatible.
   */
  @Test
  def twoScalaLibrariesWithOneIncompatbile() {
    setRawClasspathAndCheckMarkers(baseRawClasspath :+ createIncompatibleScalaLibraryEntry(), 0, 1)
  }
  
  /**
   * The scala library is defined using a classpath variable, with a different but compatible version
   */
  @Test
  def usingClasspathVariable() {
    // create a classpath variable
    JavaCore.setClasspathVariable("CLASSPATH_TEST_LIB", new Path(project.underlying.getLocation().toOSString()).append("/lib/" + ScalaPlugin.plugin.shortScalaVer + ".x/"), new NullProgressMonitor)
    setRawClasspathAndCheckMarkers(cleanRawClasspath :+ JavaCore.newVariableEntry(new Path("CLASSPATH_TEST_LIB/scala-library.jar"), null, null), 1, 0)
  }
  
  /**
   * Two projects are setup with the scala library is defined using a classpath variable.
   * First the variable points to a different but compatible version, then it points to a bad library.
   */
  @Test
  def changeImpactsMultipleProjects() {
    // create a classpath variable
    JavaCore.setClasspathVariable("CLASSPATH_TEST_LIB", new Path(project.underlying.getLocation().toOSString()).append("/lib/" + ScalaPlugin.plugin.shortScalaVer + ".x/"), new NullProgressMonitor)
    
    // set the classpath of the 'default' project
    setRawClasspathAndCheckMarkers(cleanRawClasspath :+ JavaCore.newVariableEntry(new Path("CLASSPATH_TEST_LIB/scala-library.jar"), null, null), 1, 0)
    
    // create a second project
    val secondProject= simulator.createProjectInWorkspace("classpathMultipleProject")
    
    val secondProjectCleanRawClasspath= for (classpathEntry <- secondProject.javaProject.getRawClasspath()
        if classpathEntry.getPath().toPortableString() != "org.scala-ide.sdt.launching.SCALA_CONTAINER")
      yield classpathEntry
      
    // set the classpath of the second project
    setRawClasspathAndCheckMarkers(secondProjectCleanRawClasspath :+ JavaCore.newVariableEntry(new Path("CLASSPATH_TEST_LIB/scala-library.jar"), null, null), 1, 0, secondProject)
    
    // change the classpath variable value to a bad scala library
    JavaCore.setClasspathVariable("CLASSPATH_TEST_LIB", new Path(project.underlying.getLocation().toOSString()).append("/lib/noproperties/"), new NullProgressMonitor)
    
    // check the markers (no warning, one error)
    checkMarkers(0, 1)
    checkMarkers(0, 1, secondProject)
    
  }

  /**
   * The scala-library.jar from the lib folder is marked as being a different version, but compatible
   */
  @Test
  def differentButCompatibleVersion() {
    setRawClasspathAndCheckMarkers(cleanRawClasspath :+ newLibraryEntry("scala-library.jar"), 1, 0)
  }
  
  /**
   * The scala-library.jar is marked as being a different, incompatible version
   */
  @Test
  def differentAndIncompatibleVersion() {
    val newRawClasspath= cleanRawClasspath :+ createIncompatibleScalaLibraryEntry()
      
        
    setRawClasspathAndCheckMarkers(newRawClasspath, 0, 1)
  }
  
  /**
   * The properties file in scala-library.jar doesn't contain the version information
   */
  @Test
  def noVersionInPropertiesFile() {
    setRawClasspathAndCheckMarkers(cleanRawClasspath :+ JavaCore.newLibraryEntry(new Path("/classpath/lib/noversion/scala-library.jar"), null, null), 0, 1)
  }

  /**
   * The scala-library.jar doesn't contain a properties file.
   */
  @Test
  def noPropertiesFile() {
    setRawClasspathAndCheckMarkers(cleanRawClasspath :+ JavaCore.newLibraryEntry(new Path("/classpath/lib/noproperties/scala-library.jar"), null, null), 0, 1)
  }

  /**
   * The library has a different name, but with a compatible version and contains scala.Predef
   */
  @Test
  def differentNameWithCompatibleVersion() {
    setRawClasspathAndCheckMarkers(cleanRawClasspath :+ newLibraryEntry("my-scala-library.jar"), 1, 0)
  }
  
  /**
   * The library has a different name, but with a compatible version and contains scala.Predef
   */
  @Test
  def differentNameWithIncompatibleVersion() {
    val newRawClasspath= cleanRawClasspath :+ createIncompatibleScalaLibraryEntry()
        
    setRawClasspathAndCheckMarkers(newRawClasspath, 0, 1)
  }
  
  /**
   * One scala library defined in the classpath, as a binary class folder
   */
  @Test
  def binaryClassFolderLibrary() {
    setRawClasspathAndCheckMarkers(cleanRawClasspath :+  newLibraryEntry("binary-scala-library"), 1, 0)
  }
  
  @Test
  def dependentProjectLibrary() {
    import SDTTestUtils._

    val Seq(scalaLibProject) = createProjects("scala-library")
    try {
      val packScala = createSourcePackage("scala")(scalaLibProject)

      val unitA = packScala.createCompilationUnit("Predef.scala", "class Predef", true, null)

      setRawClasspathAndCheckMarkers(cleanRawClasspath :+ JavaCore.newProjectEntry(scalaLibProject.underlying.getFullPath, true), 0, 0)
    } finally {
      deleteProjects(scalaLibProject)
    }
  }

  /**
   * check that the error marker is kept even after a clean
   */
  @Test
  def errorKeptAfterClean() {
    setRawClasspathAndCheckMarkers(cleanRawClasspath, 0, 1)
    
    project.underlying.build(IncrementalProjectBuilder.CLEAN_BUILD, new NullProgressMonitor)
    project.underlying.build(IncrementalProjectBuilder.FULL_BUILD, new NullProgressMonitor)
    
    checkMarkers(0, 1)
  }
  
  def projectErrors(markerId: String*): List[String] = {
    
    val markers = markerId.flatMap(id => project.underlying.findMarkers(id, false, IResource.DEPTH_INFINITE))
    
    for (m <- markers.toList) yield m.getAttribute(IMarker.MESSAGE).toString
  }
  
  @Test
  def settingErrorsInProjectAreKeptAfterClasspathCheck() {
    // illegal option
    project.storage.setValue(CompilerSettings.ADDITIONAL_PARAMS, "-Xi_dont_know")

    project.underlying.build(IncrementalProjectBuilder.CLEAN_BUILD, new NullProgressMonitor)
    project.underlying.build(IncrementalProjectBuilder.FULL_BUILD, new NullProgressMonitor)
    
    project.classpathHasChanged() // trick to make the check happen

    val errors = projectErrors(ScalaPlugin.plugin.settingProblemMarkerId)
    
    // on 2.8 an invalid setting is reported twice, so the total number of errors is 3 or 4
    assertTrue("unexpected number of scala problems in project: " + errors, errors.length >= 1)
    
    // back to normal
    project.storage.setValue(CompilerSettings.ADDITIONAL_PARAMS, "")

    project.underlying.build(IncrementalProjectBuilder.CLEAN_BUILD, new NullProgressMonitor)
    project.underlying.build(IncrementalProjectBuilder.FULL_BUILD, new NullProgressMonitor)
    
    project.classpathHasChanged() // trick to make the check happen

    val errors1 = projectErrors(ScalaPlugin.plugin.problemMarkerId, ScalaPlugin.plugin.settingProblemMarkerId)
    
    assertEquals("unexpected number of scala problems in project: " + errors1, 2, errors1.length)
  }

  @Test
  def buildErrorsInProjectAreKeptAfterClasspathCheck() {
    // illegal option
    project.storage.setValue(CompilerSettings.ADDITIONAL_PARAMS, "-P:unknown:error")

    project.underlying.build(IncrementalProjectBuilder.CLEAN_BUILD, new NullProgressMonitor)
    project.underlying.build(IncrementalProjectBuilder.FULL_BUILD, new NullProgressMonitor)
    
    project.classpathHasChanged() // trick to make the check happen

    val errors = projectErrors(ScalaPlugin.plugin.problemMarkerId, ScalaPlugin.plugin.settingProblemMarkerId)
    
    assertEquals("unexpected number of scala problems in project: " + errors, 1, errors.length)
    
    project.storage.setValue(CompilerSettings.ADDITIONAL_PARAMS, "")
  }

  
  /**
   * check the code is not compiled if the classpath is not right (no error reported in scala files)
   */
  @Test
  def errorInClasspathStopBuild() {
    project.underlying.build(IncrementalProjectBuilder.CLEAN_BUILD, new NullProgressMonitor)
    project.underlying.build(IncrementalProjectBuilder.INCREMENTAL_BUILD, new NullProgressMonitor)

    // no error on the project itself
    checkMarkers(0, 0)
    
    // two excepted code errors
    var markers= project.underlying.findMarkers(ScalaPlugin.plugin.problemMarkerId, true, IResource.DEPTH_INFINITE)
    assertEquals("Unexpected number of scala problems in project", 2, markers.length)
    
    // switch to an invalid classpath
    setRawClasspathAndCheckMarkers(cleanRawClasspath, 0, 1)
    
    // no code errors visible anymore
    markers= project.underlying.findMarkers(ScalaPlugin.plugin.problemMarkerId, true, IResource.DEPTH_INFINITE)
    assertEquals("Unexpected number of scala problems in project", 0, markers.length)
  }
  
  /**
   * Generate library entry for an incompatible scala library
   */
  private def createIncompatibleScalaLibraryEntry(): IClasspathEntry =
    JavaCore.newLibraryEntry(new Path("/classpath/lib/" +
        getIncompatibleScalaVersion + ".x/scala-library.jar"), null, null)

  private def getIncompatibleScalaVersion: String = {
    if (ScalaPlugin.plugin.shortScalaVer == "2.10") "2.9" else "2.10"
  }

  /**
   * Set the new classpath and check the number of errors and warnings attached to the project.
   */
  private def setRawClasspathAndCheckMarkers(newRawClasspath: Array[IClasspathEntry], expectedWarnings: Int, expectedErrors: Int, scalaProject: ScalaProject = project) {
    scalaProject.javaProject.setRawClasspath(newRawClasspath, new NullProgressMonitor)
    checkMarkers(expectedWarnings, expectedErrors, scalaProject)
  }

  /** Check the number of classpath errors and warnings attached to the project. It does *not* look for normal Scala problem markers,
   *  only for classpath markers.
   */
  private def checkMarkers(expectedNbOfWarningMarker: Int, expectedNbOfErrorMarker: Int, scalaProject: ScalaProject = project) {
    // check the classpathValid state
    assertEquals("Unexpected classpath validity state", expectedNbOfErrorMarker == 0, scalaProject.isClasspathValid())

    var actualMarkers = (0, 0)
    SDTTestUtils.waitUntil(10000) {
      actualMarkers = collectMarkers(scalaProject)
      actualMarkers == (expectedNbOfErrorMarker, expectedNbOfWarningMarker)
    }

    val (nbOfErrorMarker, nbOfWarningMarker) = actualMarkers
    // after TIMEOUT, we didn't get the expected value
    assertEquals("Unexpected nb of warning markers", expectedNbOfWarningMarker, nbOfWarningMarker)
    assertEquals("Unexpected nb of error markers", expectedNbOfErrorMarker, nbOfErrorMarker)
  }

  private def collectMarkers(scalaProject: ScalaProject): (Int, Int) = {
    @volatile var actualMarkers: (Int, Int) = (0, 0)

    // We need to use a job when counting markers because classpath markers are themselves added in a job
    // By using the project as a scheduling rule, we are forced to wait until the classpath marker job has
    // finished. Otherwise, there's a race condition between the classpath validator job (that removes old
    // markers and adds new ones) and this thread, that might read between the delete and the add
    def countMarkersJob() = EclipseUtils.prepareJob("CheckMarkersJob", project.underlying) { monitor =>
      // count the markers on the project
      var nbOfWarningMarker = 0
      var nbOfErrorMarker = 0

      for (marker <- scalaProject.underlying.findMarkers(classpathMarkerId, false, IResource.DEPTH_ZERO))
        marker.getAttribute(IMarker.SEVERITY, 0) match {
          case IMarker.SEVERITY_ERROR   => nbOfErrorMarker += 1
          case IMarker.SEVERITY_WARNING => nbOfWarningMarker += 1
          case _                        =>
        }
      actualMarkers = (nbOfErrorMarker, nbOfWarningMarker)
      Status.OK_STATUS
    }

    @volatile var jobDone = false
    object jobListener extends JobChangeAdapter {
      override def done(event: IJobChangeEvent) {
        jobDone = true
      }
    }

    val job = countMarkersJob()
    job.addJobChangeListener(jobListener)
    job.schedule()

    SDTTestUtils.waitUntil(10000) { jobDone }
    actualMarkers
  }

  private def newLibraryEntry(name: String, shortScalaVersion: String = ScalaPlugin.plugin.shortScalaVer): IClasspathEntry = {
    JavaCore.newLibraryEntry(new Path("/classpath/lib/" + shortScalaVersion + ".x/" + name), null, null)
  }
}
