package org.scalaide.core.internal.project

import scala.collection.mutable
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
import org.scalaide.core.ScalaPlugin.plugin
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
import org.scalaide.logging.HasLogger
import java.io.File
import org.eclipse.jdt.internal.core.JavaProject
import org.scalaide.core.resources.MarkerFactory
import org.scalaide.util.internal.eclipse.EclipseUtils
import org.osgi.framework.Version
import org.scalaide.core.ScalaPlugin
import org.scalaide.util.internal.SettingConverterUtil
import org.scalaide.ui.internal.preferences.ScalaPluginSettings
import scala.tools.nsc.settings.ScalaVersion
import org.eclipse.jface.util.StatusHandler
import org.eclipse.debug.core.DebugPlugin
import scala.collection.immutable.HashMap

/** The Scala classpath broken down in the JDK, Scala library and user library.
 *
 *  The Scala compiler needs these entries to be separated for proper setup.
 *
 *  @note All paths are file-system absolute paths. Any path variables or
 *        linked resources are resolved.
 */
case class ScalaClasspath(private[project] val jdkPaths: Seq[IPath], // JDK classpath
  val scalaLib: Option[IPath], // scala library
  val userCp: Seq[IPath], // user classpath, excluding the Scala library and JDK
  private[project] val scalaVersion: Option[String]) {
  override def toString =
    """
    jdkPaths: %s
    scalaLib: %s
    usercp: %s
    scalaVersion: %s

    """.format(jdkPaths, scalaLib, userCp, scalaVersion)

  def scalaLibraryFile: Option[File] =
    scalaLib.map(_.toFile.getAbsoluteFile)

  private def toPath(ps: Seq[IPath]): Seq[File] = ps map (_.toFile.getAbsoluteFile)

  /** Return the full classpath of this project.
   *
   *  It puts the JDK and the Scala library in front of the user classpath.
   */
  def fullClasspath: Seq[File] =
    toPath(jdkPaths) ++ scalaLibraryFile.toSeq ++ toPath(userCp)
}

/** A Scala library definition.
 *
 *  @param location  The file-system absolute path to the root of the Scala library
 *  @param version   An option version, retrieved from library.properties, if present
 *  @param isProject Whether the library is provided by a project inside the workspace
 *
 */
case class ScalaLibrary(location: IPath, version: Option[String], isProject: Boolean)

/** Extractor which returns the Scala version of a jar,
 */
private object VersionInFile {

  /**
   * Regex accepting filename of the format: name_2.xx.xx-version.jar.
   * It is used to extract the `2.xx.xx` section.
   */
  private val CrossCompiledRegex = """.*_(2\.\d+(?:\.\d*)?)(?:-.*)?.jar""".r

  def unapply(fileName: String): Option[ScalaVersion] = {
    fileName match {
      case CrossCompiledRegex(version) =>
        Some(ScalaVersion(version))
      case _ =>
        None
    }
  }
}

/** Scala project classpath management. This class is responsible for breaking down the classpath in
 *  JDK entries, Scala library entries, and user entries. It also validates the classpath and
 *  manages the classpath error markers for the given Scala project.
 */
trait ClasspathManagement extends HasLogger { self: ScalaProject =>

  /** Return the Scala classpath breakdown for the managed project. */
  def scalaClasspath: ScalaClasspath = {
    val jdkEntries = jdkPaths
    val cp = javaClasspath.filterNot(jdkEntries.toSet)

    scalaLibraries match {
      case Seq(ScalaLibrary(pf, version, _), _*) =>
        new ScalaClasspath(jdkEntries, Some(pf), cp.filterNot(_ == pf), version)
      case _ =>
        new ScalaClasspath(jdkEntries, None, cp, None)
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
            Option(container).map(_.getClasspathEntries.toSeq.map(_.getPath))
          } else None

        case _ => None

      }).flatten
  }

  /** Return the fully resolved classpath of this project, including the
   *  Scala library and the JDK entries, in the *project-defined order*.
   *
   *  The Scala compiler needs the JDK and Scala library on the bootclasspath,
   *  meaning that during compilation the effective order is with these two
   *  components at the head of the list. This method *does not* move them
   *  in front.
   */
  private def javaClasspath: Seq[IPath] = {
    val path = new mutable.LinkedHashSet[IPath]

    def computeClasspath(project: IJavaProject, exportedOnly: Boolean): Unit = {
      val cpes = project.getResolvedClasspath(true)

      for (
        cpe <- cpes if !exportedOnly || cpe.isExported ||
          cpe.getEntryKind == IClasspathEntry.CPE_SOURCE
      ) cpe.getEntryKind match {
        case IClasspathEntry.CPE_PROJECT =>
          val depProject = plugin.workspaceRoot.getProject(cpe.getPath.lastSegment)
          if (JavaProject.hasJavaNature(depProject)) {
            computeClasspath(JavaCore.create(depProject), true)
          }
        case IClasspathEntry.CPE_LIBRARY =>
          if (cpe.getPath != null) {
            val absPath = plugin.workspaceRoot.findMember(cpe.getPath)
            if (absPath != null)
              path += absPath.getLocation
            else {
              path += cpe.getPath
            }
          } else
            logger.error("Classpath computation encountered a null path for " + cpe, null)
        case IClasspathEntry.CPE_SOURCE =>
          val cpeOutput = cpe.getOutputLocation
          val outputLocation = if (cpeOutput != null) cpeOutput else project.getOutputLocation

          if (outputLocation != null) {
            val absPath = plugin.workspaceRoot.findMember(outputLocation)
            if (absPath != null)
              path += absPath.getLocation
          }

        case _ =>
          logger.warn("Classpath computation encountered unknown entry: " + cpe)
      }
    }
    computeClasspath(javaProject, false)
    path.toList
  }

  private val classpathCheckLock = new Object
  @volatile
  private var classpathHasBeenChecked = false
  @volatile
  private var classpathValid = false;

  def isCheckingClassPath(): Boolean = java.lang.Thread.holdsLock(classpathCheckLock)

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
  def classpathHasChanged() = {
    classpathCheckLock.synchronized {
      // mark as in progress
      classpathHasBeenChecked = false
      checkClasspath()
      if (classpathValid) {
        // no point in resetting compilers on an invalid classpath,
        // it would not work anyway. But we need to reset them if the classpath
        // was (and still is) valid, because the contents might have changed.
        logger.info("Resetting compilers due to classpath change.")
        resetCompilers()
      }
    }
  }

  protected def resetClasspathCheck() {
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
   *
   *  @return the absolute file-system path to package fragments that define `scala.Predef`.
   *          If it contains path variables or is a linked resources, the path is resolved.
   */
  def scalaLibraries: Seq[ScalaLibrary] = {
    val pathToPredef = new Path("scala/Predef.class")

    def isZipFileScalaLib(p: IPath): Boolean = {
      // catch any JavaModelException and pretend it's not the scala library
      failAsValue(classOf[JavaModelException], classOf[IOException])(false) {
        val jarFile = JavaModelManager.getJavaModelManager().getZipFile(p)
        jarFile.getEntry("scala/Predef.class") ne null
      }
    }

    // look for all package fragment roots containing instances of scala.Predef
    val fragmentRoots = new mutable.ListBuffer[ScalaLibrary]

    for (fragmentRoot <- javaProject.getAllPackageFragmentRoots() if fragmentRoot.getPackageFragment("scala").exists) {
      fragmentRoot.getKind() match {
        case IPackageFragmentRoot.K_BINARY =>
          val resource = fragmentRoot.getUnderlyingResource

          val foundIt: Boolean = resource match {
            case folder: IFolder => folder.findMember(pathToPredef) ne null
            case file: IFile     => isZipFileScalaLib(file.getFullPath)
            case _ =>
              val file = fragmentRoot.getPath.toFile
              file.exists && {
                if (file.isFile) isZipFileScalaLib(fragmentRoot.getPath)
                else fragmentRoot.getPath.append(pathToPredef).toFile.exists
              }
          }

          if (foundIt) fragmentRoots += ScalaLibrary(fragmentRoot.getPath, getVersionNumber(fragmentRoot), isProject = false)

        case IPackageFragmentRoot.K_SOURCE =>
          for {
            folder <- Option(fragmentRoot.getUnderlyingResource.asInstanceOf[IFolder])
            if folder.findMember(new Path("scala/Predef.scala")) ne null
            if (folder.getProject != underlying) // only consider a source library if it comes from a different project
            dependentPrj <- ScalaPlugin.plugin.asScalaProject(folder.getProject)
            (srcPath, binFolder) <- dependentPrj.sourceOutputFolders
            if srcPath.getProjectRelativePath == folder.getProjectRelativePath
          } {
            fragmentRoots += ScalaLibrary(binFolder.getLocation, getVersionNumber(fragmentRoot), isProject = true)
          }

        case _ =>
      }
    }

    fragmentRoots.toSeq
  }

  private def checkClasspath() {
    // check the version of Scala library used, and if enabled, the Scala compatibility of the other jars.
    val withVersionClasspathValidator =
      storage.getBoolean(SettingConverterUtil.convertNameToProperty(ScalaPluginSettings.withVersionClasspathValidator.name))
    val errors =
      validateScalaLibrary(scalaLibraries) ++
        (if (withVersionClasspathValidator) {
          validateBinaryVersionsOnClasspath()
        } else {
          Seq()
        })
    updateClasspathMarkers(errors)
    classpathHasBeenChecked = true
  }

  private def validateScalaLibrary(fragmentRoots: Seq[ScalaLibrary]): Seq[(Int, String, String)] = {
    import org.scalaide.util.internal.CompilerUtils._

    def incompatibleScalaLibrary(scalaLib: ScalaLibrary) = scalaLib match {
      case ScalaLibrary(_, Some(version), false) => !plugin.isCompatibleVersion(ScalaVersion(version), this)
      case _                               => false
    }

    val scalaVersion = plugin.scalaVer.unparse
    val expectedVersion =
      if (this.isUsingCompatibilityMode)
        previousShortString(plugin.scalaVer)
      else
        scalaVersion

    fragmentRoots.length match {
      case 0 => // unable to find any trace of scala library
        (IMarker.SEVERITY_ERROR, "Unable to find a scala library. Please add the scala container or a scala library jar to the build path.", plugin.classpathProblemMarkerId) :: Nil
      case 1 => // one and only one, now check if the version number is contained in library.properties
        if (fragmentRoots(0).isProject) {
          // if the library is provided by a project in the workspace, disable the warning (the version file is missing anyway)
          Nil
        } else fragmentRoots(0).version match {
          case Some(v) if (!this.isUsingCompatibilityMode() && ScalaVersion(v) == plugin.scalaVer) =>
            // exactly the same version, should be from the container. Perfect
            Nil
          case Some(v) if plugin.isCompatibleVersion(ScalaVersion(v), this) =>
            // compatible version (major, minor are the same). Still, add warning message
            (IMarker.SEVERITY_WARNING, s"The version of scala library found in the build path ($v) is different from the one provided by scala IDE ($scalaVersion). Make sure you know what you are doing.", plugin.classpathProblemMarkerId) :: Nil
          case Some(v) if (isBinaryPrevious(plugin.scalaVer, ScalaVersion(v))) => {
            val msg = s"The version of scala library found in the build path of ${underlying.getName()} ($v) is prior to the one provided by scala IDE ($scalaVersion). Please set a Scala Installation in this project's compiler Options."
             // Previous version, and the XSource flag isn't there already : warn and suggest fix using Xsource
             (IMarker.SEVERITY_ERROR, msg, plugin.scalaVersionProblemMarkerId) :: Nil
          }
          case Some(v) => {
            // incompatible version
            (IMarker.SEVERITY_ERROR, s"The version of scala library found in the build path ($v) is incompatible with the one expected by scala IDE ($expectedVersion). Please replace the scala library with the scala container or a compatible scala library jar.", plugin.classpathProblemMarkerId) :: Nil
          }
          case None =>
            // no version found
            (IMarker.SEVERITY_ERROR, "The scala library found in the build path doesn't expose its version. Please replace the scala library with the scala container or a valid scala library jar", plugin.classpathProblemMarkerId) :: Nil
        }
      case _ => // 2 or more of them, not great, but warn only if the library is not a project
        if (fragmentRoots.exists(incompatibleScalaLibrary))
          (IMarker.SEVERITY_ERROR, moreThanOneLibraryError(fragmentRoots.map(_.location), compatible = false), plugin.classpathProblemMarkerId) :: Nil
        else
          (IMarker.SEVERITY_WARNING, moreThanOneLibraryError(fragmentRoots.map(_.location), compatible = true), plugin.classpathProblemMarkerId) :: Nil
    }
  }

  private def moreThanOneLibraryError(libs: Seq[IPath], compatible: Boolean): String = {
    val first =  "More than one scala library found in the build path (%s).".format(libs.mkString(", "))
    if (compatible) first + "This is not an optimal configuration, try to limit to one Scala library in the build path."
    else first + "At least one has an incompatible version. Please update the project build path so it contains only one compatible scala library."
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
  private def updateClasspathMarkers(errors: Seq[(Int, String, String)]) {
    // set the state
    classpathValid = errors.forall(_._1 != IMarker.SEVERITY_ERROR)

    // the marker manipulation needs to be done in a Job, because it requires
    // a change on the IProject, which is locked for modification during
    // the classpath change notification
    EclipseUtils.scheduleJob("Update classpath error markers", underlying) { monitor =>
      if (underlying.isOpen()) { // cannot change markers on closed project
        // clean the classpath markers
        underlying.deleteMarkers(plugin.classpathProblemMarkerId, true, IResource.DEPTH_ZERO)

        if (!classpathValid) {
          // delete all other Scala and Java error markers
          underlying.deleteMarkers(plugin.problemMarkerId, true, IResource.DEPTH_INFINITE)
          underlying.deleteMarkers(IJavaModelMarker.JAVA_MODEL_PROBLEM_MARKER, true, IResource.DEPTH_INFINITE)
        }

        // create the classpath problem markers
        errors foreach {
          case (severity, message, markerId) => (new cpMarkerFactory(markerId)).create(underlying, severity, message)
        }
      }
      Status.OK_STATUS
    }
  }

  private def validateBinaryVersionsOnClasspath(): Seq[(Int, String, String)] = {
    val entries = scalaClasspath.userCp
    val errors = mutable.ListBuffer[(Int, String, String)]()
    val badEntries = mutable.ListBuffer[(IPath, ScalaVersion)]()

    for (entry <- entries if entry ne null) {
      entry.lastSegment() match {
        case VersionInFile(version) =>
          if (!plugin.isCompatibleVersion(version, this)) {
            badEntries += ((entry,version))
            val msg = s"${entry.lastSegment()} of ${this.underlying.getName()} build path is cross-compiled with an incompatible version of Scala (${version.unparse}). In case this report is mistaken, this check can be disabled in the compiler preference page."
            errors += ((IMarker.SEVERITY_ERROR, msg, plugin.scalaVersionProblemMarkerId))
          }
        case _ =>
          // ignore libraries that aren't cross compiled/are compatible
      }
    }
    errors.toSeq
  }

  private class cpMarkerFactory(key:String) extends MarkerFactory(key)
}
