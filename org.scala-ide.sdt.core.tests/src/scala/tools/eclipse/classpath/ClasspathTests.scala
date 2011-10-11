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

object ClasspathTests extends TestProjectSetup("classpath")

class ClasspathTests {
  
  import ClasspathTests._
  
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
    setRawClasspathAndCheckMarkers(baseRawClasspath :+ JavaCore.newLibraryEntry(new Path("/classpath/lib/2.10.x/scala-library.jar"), null, null), 0, 1)
  }
  
  /**
   * Two scala library defined in the classpath, the eclipse container one, and one with a different name.
   */
  @Test
  def twoScalaLibrariesWithDifferentName() {
    setRawClasspathAndCheckMarkers(baseRawClasspath :+ JavaCore.newLibraryEntry(new Path("/classpath/lib/2.10.x/my-scala-library.jar"), null, null), 0, 1)
  }
  
  /**
   * The scala library is defined using a classpath variable, with a different but compatible version
   */
  @Test
  def usingClasspathVariable() {
    // create a classpath variable
    JavaCore.setClasspathVariable("CLASSPATH_TEST_LIB", new Path("/classpath/lib/" + ScalaPlugin.plugin.shortScalaVer + ".x/"), new NullProgressMonitor)
    setRawClasspathAndCheckMarkers(cleanRawClasspath :+ JavaCore.newVariableEntry(new Path("CLASSPATH_TEST_LIB/scala-library.jar"), null, null), 1, 0)
  }

  /**
   * The scala-library.jar from the lib folder is marked as being a different version, but compatible
   */
  @Test
  def differentButCompatibleVersion() {
    setRawClasspathAndCheckMarkers(cleanRawClasspath :+ JavaCore.newLibraryEntry(new Path("/classpath/lib/" + ScalaPlugin.plugin.shortScalaVer + ".x/scala-library.jar"), null, null), 1, 0)
  }
  
  /**
   * The scala-library.jar is marked as being a different, incompatible version
   */
  @Test
  def differentAndIncompatibleVersion() {
    val newRawClasspath= cleanRawClasspath :+
      JavaCore.newLibraryEntry(new Path("/classpath/lib/" +
        (ScalaPlugin.plugin.shortScalaVer match {
          case "2.8" => "2.9"
          case "2.9" => "2.10"
          case "2.10" => "2.8"
          case _ =>
            fail("Unsupported embedded scala library version " + ScalaPlugin.plugin.scalaVer +". Please update the test.")
            ""
        }) + ".x/scala-library.jar"), null, null)
        
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
    setRawClasspathAndCheckMarkers(cleanRawClasspath :+ JavaCore.newLibraryEntry(new Path("/classpath/lib/" + ScalaPlugin.plugin.shortScalaVer + ".x/my-scala-library.jar"), null, null), 1, 0)
  }
  
  /**
   * The library has a different name, but with a compatible version and contains scala.Predef
   */
  @Test
  def differentNameWithIncompatibleVersion() {
    val newRawClasspath= cleanRawClasspath :+
      JavaCore.newLibraryEntry(new Path("/classpath/lib/" +
        (ScalaPlugin.plugin.shortScalaVer match {
          case "2.8" => "2.9"
          case "2.9" => "2.10"
          case "2.10" => "2.8"
          case _ =>
            fail("Unsupported embedded scala library version " + ScalaPlugin.plugin.scalaVer +". Please update the test.")
            ""
        }) + ".x/my-scala-library.jar"), null, null)
        
    setRawClasspathAndCheckMarkers(newRawClasspath, 0, 1)
  }
  
  /**
   * 
   */
  
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
    var markers= project.underlying.findMarkers("org.scala-ide.sdt.core.problem", false, IResource.DEPTH_INFINITE)
    assertEquals("Unexpected number of scala problems in project", 2, markers.length)
    
    // switch to an invalid classpath
    setRawClasspathAndCheckMarkers(cleanRawClasspath, 0, 1)
    
    project.underlying.build(IncrementalProjectBuilder.CLEAN_BUILD, new NullProgressMonitor)
    project.underlying.build(IncrementalProjectBuilder.INCREMENTAL_BUILD, new NullProgressMonitor)
    
    // one error on the project
    checkMarkers(0, 1)
    
    // no additional code errors
    markers= project.underlying.findMarkers("org.scala-ide.sdt.core.problem", false, IResource.DEPTH_INFINITE)
    assertEquals("Unexpected number of scala problems in project", 1, markers.length)
  }
  
  /**
   * Set the new classpath and check the number of errors and warnings attached to the project.
   */
  private def setRawClasspathAndCheckMarkers(newRawClasspath: Array[IClasspathEntry], expectedNbOfWarningMarker: Int, expectedNbOfErrorMarker: Int) {
    project.javaProject.setRawClasspath(newRawClasspath, new NullProgressMonitor)
    checkMarkers(expectedNbOfWarningMarker, expectedNbOfErrorMarker)
  }
  
  /**
   * Check the number of errors and warnings attached to the project.
   */
  private def checkMarkers(expectedNbOfWarningMarker: Int, expectedNbOfErrorMarker: Int) {
    val TIMEOUT= 5000
    
    // check the classpathValid state
    assertEquals("Unexpected classpath validity state", expectedNbOfErrorMarker == 0, project.isClasspathValid())
    
    var nbOfWarningMarker= 0
    var nbOfErrorMarker= 0
    
    for (i <- 1 to (TIMEOUT / 200)) {
      // count the markers on the project
      nbOfWarningMarker= 0
      nbOfErrorMarker= 0
      for (marker <- project.underlying.findMarkers("org.scala-ide.sdt.core.problem", false, IResource.DEPTH_ZERO))
        marker.getAttribute(IMarker.SEVERITY, 0) match {
        case IMarker.SEVERITY_ERROR => nbOfErrorMarker+=1
        case IMarker.SEVERITY_WARNING => nbOfWarningMarker+=1
        case _ =>
      }
    
      if (nbOfWarningMarker == expectedNbOfWarningMarker && nbOfErrorMarker == expectedNbOfErrorMarker) {
        // markers are fine, we're done
        return
      }
      
      // wait a bit before trying again
      Thread.sleep(200)
    }
    
    // after TIMEOUT, we didn't get the expected value
    assertEquals("Unexpected nb of warning markers", expectedNbOfWarningMarker, nbOfWarningMarker)
    assertEquals("Unexpected nb of error markers", expectedNbOfErrorMarker, nbOfErrorMarker)
  }
  
}