package scala.tools.eclipse

import scala.collection.mutable.ListBuffer
import org.eclipse.jdt.core.IPackageFragmentRoot
import org.eclipse.jdt.core.IJavaProject
import org.eclipse.core.resources.IMarker
import org.eclipse.jdt.core.IJarEntryResource
import java.util.Properties
import org.eclipse.core.resources.IProject
import org.eclipse.jdt.core.IJavaModelMarker
import org.eclipse.core.runtime.IStatus
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.core.runtime.jobs.Job
import org.eclipse.core.resources.IResource
import org.eclipse.core.runtime.Status
import ScalaPlugin.plugin
import org.eclipse.core.runtime.IPath
import org.eclipse.jdt.core.IClasspathEntry
import org.eclipse.jdt.launching.JavaRuntime
import org.eclipse.jdt.core.JavaCore
import org.eclipse.core.resources.IFile
import org.eclipse.core.resources.IStorage
import java.io.IOException
import org.eclipse.core.resources.IFolder
import org.eclipse.jdt.internal.core.JavaModelManager
import org.eclipse.core.runtime.Path
import scala.util.control.Exception._
import org.eclipse.jdt.core.JavaModelException
import scala.tools.eclipse.logging.HasLogger

/** The Scala classpath broken down in the JDK, Scala library and user library.
 *
 *  The Scala compiler needs these entries to be separated for proper setup.
 */
class ScalaClasspath(val jdkPaths: Seq[IPath], // JDK classpath
  val scalaLib: Option[IPath], // scala library
  val userCp: Seq[IPath], // user classpath, excluding the Scala library and JDK
  val scalaVersion: Option[String]) {
  override def toString =
    """
    jdkPaths: %s
    scalaLib: %s
    usercp: %s
    scalaVersion: %s
    
    """.format(jdkPaths, scalaLib, userCp, scalaVersion)
}

object ScalaClasspath {
  def fromProject(project: ScalaProject): ScalaClasspath = null
}

/** Scala project classpath management. This class is responsible for breaking down the classpath in
 *  JDK entries, Scala library entries, and user entries. It also validates the classpath and
 *  manages the classpath error markers for the given Scala project.
 */
trait ClasspathManagement extends HasLogger { self: ScalaProject =>

  /** Return the Scala classpath breakdown for the managed project. */
  def scalaClasspath: ScalaClasspath = {
    val jdkEntries = jdkPaths
    val cp = classpath.filterNot(jdkEntries.toSet)

    scalaPackageFragments match {
      case Seq((pf, version), _*) => new ScalaClasspath(jdkEntries, Some(pf.getPath()), cp.filterNot(_ == pf.getPath), version)
      case _                      => new ScalaClasspath(jdkEntries, None, cp, None)
    }
  }

  /** Return the classpath entries coming from the JDK.  */
  def jdkPaths: Seq[IPath] = {
    val rawClasspath = javaProject.getRawClasspath()

    rawClasspath.toSeq.flatMap(cp =>
      cp.getEntryKind match {
        case IClasspathEntry.CPE_CONTAINER =>
          val path0 = cp.getPath
          if (!path0.isEmpty && path0.segment(0) == JavaRuntime.JRE_CONTAINER) {
            val container = JavaCore.getClasspathContainer(path0, javaProject)
            Some(container.getClasspathEntries.toSeq.map(_.getPath))
          } else None

        case _ => None

      }).flatten
  }

  private var classpathCheckLock = new Object
  private var classpathHasBeenChecked = false
  private var classpathValid = false;

  /** Return <code>true</code> if the classpath is deemed valid.
   *  Check the classpath if it has not been checked yet.
   */
  def isClasspathValid(): Boolean = {
    classpathCheckLock.synchronized {
      if (!classpathHasBeenChecked)
        checkClasspath()
      classpathValid
    }
  }

  /** Check if the classpath is valid for scala.
   *  It is said valid if it contains one and only scala library jar, with a version compatible
   *  with the one from the scala-ide plug-in
   */
  def classpathHasChanged() {
    classpathCheckLock.synchronized {
      try {
        // mark as in progress
        classpathHasBeenChecked = false
        checkClasspath()
        if (classpathValid) {
          // no point to reset the compilers on an invalid classpath,
          // it would not work anyway
          resetCompilers()
        }
      }
    }
  }

  def resetClasspathCheck() {
    // mark the classpath as not checked
    classpathCheckLock.synchronized {
      classpathHasBeenChecked = false
    }
  }

  /** Return all package fragments on the classpath that might be a Scala library, with their version.
   *
   *  A package fragment is considered a Scala library if it defines `scala.Predef`.
   *
   *  @note The chicken and egg problem. This method does not use the more elegant JDT API for
   *        retrieving compilation units off a package fragment because the JDT relies on the Scala
   *        presentation compiler for opening Scala source or class files. Since this method
   *        is called during the Scala compiler initialization (to determine the Scala library),
   *        this method can't rely on the compiler being present.
   */
  def scalaPackageFragments: Seq[(IPackageFragmentRoot, Option[String])] = {
    // look for all package fragment roots containing instances of scala.Predef
    val fragmentRoots = new ListBuffer[(IPackageFragmentRoot, Option[String])]

    for (fragmentRoot <- javaProject.getAllPackageFragmentRoots() if fragmentRoot.getPackageFragment("scala").exists) {
      fragmentRoot.getKind() match {
        case IPackageFragmentRoot.K_BINARY =>
          val resource = fragmentRoot.getUnderlyingResource

          val entry = resource match {
            case folder: IFolder => folder.findMember(new Path("scala/Predef.class"))
            case _               => // it must be a jar file
              // catch any JavaModelException and pretend it's not the scala library
              failAsValue(classOf[JavaModelException])(null) {
                val jarFile = JavaModelManager.getJavaModelManager().getZipFile(fragmentRoot.getPath())
                jarFile.getEntry("scala/Predef.class")
              }
          }

          if (entry ne null)
            fragmentRoots += ((fragmentRoot, getVersionNumber(fragmentRoot)))

        case IPackageFragmentRoot.K_SOURCE =>
          for {
            folder <- Option(fragmentRoot.getUnderlyingResource.asInstanceOf[IFolder])
            if folder.findMember(new Path("scala/Predef.scala")) ne null
          } fragmentRoots += ((fragmentRoot, getVersionNumber(fragmentRoot)))

        case _ =>
      }
    }

    fragmentRoots.toSeq
  }

  private def checkClasspath() {
    // look for all package fragment roots containing instances of scala.Predef
    val fragmentRoots = scalaPackageFragments

    // check the found package fragment roots
    fragmentRoots.length match {
      case 0 => // unable to find any trace of scala library
        setClasspathError(IMarker.SEVERITY_ERROR, "Unable to find a scala library. Please add the scala container or a scala library jar to the build path.")
      case 1 => // one and only one, now check if the version number is contained in library.properties
        fragmentRoots(0)._2 match {
          case Some(v) if v == plugin.scalaVer =>
            // exactly the same version, should be from the container. Perfect
            setClasspathError(0, null)
          case v if plugin.isCompatibleVersion(v) =>
            // compatible version (major, minor are the same). Still, add warning message
            setClasspathError(IMarker.SEVERITY_WARNING, "The version of scala library found in the build path is different from the one provided by scala IDE: " + v.get + ". Expected: " + plugin.scalaVer + ". Make sure you know what you are doing.")
          case Some(v) =>
            // incompatible version
            setClasspathError(IMarker.SEVERITY_ERROR, "The version of scala library found in the build path is incompatible with the one provided by scala IDE: " + v + ". Expected: " + plugin.scalaVer + ". Please replace the scala library with the scala container or a compatible scala library jar.")
          case None =>
            // no version found
            setClasspathError(IMarker.SEVERITY_ERROR, "The scala library found in the build path doesn't expose its version. Please replace the scala library with the scala container or a valid scala library jar")
        }
      case _ => // 2 or more of them, not great
        if (fragmentRoots.exists { case (_, version) => !plugin.isCompatibleVersion(version) })
          setClasspathError(IMarker.SEVERITY_ERROR, "More than one scala library found in the build path, including at least one with an incompatible version. Please update the project build path so it contains only compatible scala libraries")
        else
          setClasspathError(IMarker.SEVERITY_WARNING, "More than one scala library found in the build path, all with compatible versions. This is not an optimal configuration, try to limit to one scala library in the build path.")
    }
  }

  /** Return the version number contained in library.properties if it exists.
   */
  private def getVersionNumber(fragmentRoot: IPackageFragmentRoot): Option[String] = {
    def getVersion(resource: IStorage): Option[String] = try {
      val properties = new Properties()
      properties.load(resource.getContents())
      Option(properties.getProperty("version.number"))
    } catch {
      case _: IOException => None // be very lenient, not all libraries have a properties file
    }

    for (resource <- fragmentRoot.getNonJavaResources())
      resource match {
        case jarEntry: IJarEntryResource if jarEntry.isFile() && "library.properties".equals(jarEntry.getName) =>
          return getVersion(jarEntry)
        case entry: IFile if "library.properties".equals(entry.getName) =>
          return getVersion(entry)
        case _ =>
      }
    // couldn't find it
    None
  }

  /** Manage the possible classpath error/warning reported on the project.
   */
  private def setClasspathError(severity: Int, message: String) {
    // set the state
    classpathValid = severity != IMarker.SEVERITY_ERROR
    classpathHasBeenChecked = true

    // the marker manipulation need to be done in a Job, because it requires
    // a change on the IProject, which is locked for modification during
    // the classpath change notification
    val markerJob = new Job("Update classpath error marker") {
      override def run(monitor: IProgressMonitor): IStatus = {
        if (underlying.isOpen()) { // cannot change markers on closed project
          // clean the classpath markers
          underlying.deleteMarkers(plugin.classpathProblemMarkerId, false, IResource.DEPTH_ZERO)

          // add a new marker if needed
          severity match {
            case IMarker.SEVERITY_ERROR | IMarker.SEVERITY_WARNING =>
              if (severity == IMarker.SEVERITY_ERROR) {
                // delete all other Scala and Java error markers
                underlying.deleteMarkers(plugin.problemMarkerId, true, IResource.DEPTH_INFINITE)
                underlying.deleteMarkers(IJavaModelMarker.JAVA_MODEL_PROBLEM_MARKER, true, IResource.DEPTH_INFINITE)
              }

              // create the classpath problem marker
              val marker = underlying.createMarker(plugin.classpathProblemMarkerId)
              marker.setAttribute(IMarker.MESSAGE, message)
              marker.setAttribute(IMarker.SEVERITY, severity)

            case _ =>
          }
        }
        Status.OK_STATUS
      }
    }
    markerJob.setRule(underlying)
    markerJob.schedule()
  }
}