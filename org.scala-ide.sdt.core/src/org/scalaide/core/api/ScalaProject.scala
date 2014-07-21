package org.scalaide.core.api

import org.eclipse.core.resources.IProject
import org.scalaide.core.compiler.ScalaPresentationCompilerProxy
import org.eclipse.jdt.core.IJavaProject
import org.eclipse.core.runtime.IPath
import org.eclipse.core.resources.IContainer
import org.eclipse.core.resources.IFile
import scala.tools.nsc.Settings
import org.scalaide.core.internal.builder.EclipseBuildManager
import org.eclipse.jface.preference.IPreferenceStore
import org.eclipse.core.runtime.SubMonitor
import org.eclipse.core.runtime.IProgressMonitor
import scala.collection.mutable.Publisher
import java.io.File
import org.scalaide.core.internal.project.LabeledScalaInstallation
import scala.tools.nsc.settings.ScalaVersion
import org.eclipse.jdt.core.IClasspathEntry

/**
 * A message class to signal various project-related statuses, such as a Scala Installation change, or a successful Build.
 * Immutable.
 */
trait ScalaProjectMessage
case class BuildSuccess() extends ScalaProjectMessage
case class ScalaInstallationChange() extends ScalaProjectMessage

/** The Scala classpath broken down in the JDK, Scala library and user library.
 *
 *  The Scala compiler needs these entries to be separated for proper setup.
 *  Immutable.
 *
 *  @note All paths are file-system absolute paths. Any path variables or
 *        linked resources are resolved.
 */
trait ScalaClasspath {
  /**
   * The JDK elements that should figure on classpath.
   */
  val jdkPaths: Seq[IPath]
  /**
   * The scala standard library.
   */
  val scalaLib: Option[IPath]
  /**
   * User libraries that should figure on classpath.
   */
  val userCp: Seq[IPath]
  /**
   * An optional Scala version string for diagnostics.
   * If present, should match the content of the library.properties in the sacla Library.
   */
  val scalaVersionString: Option[String]
  /**
   *  The File reference for the aforementioned scalaLib.
   */
  val scalaLibraryFile: Option[File]
  /**
   * The concatenation of the full classpath.
   */
  val fullClasspath: Seq[File]
}

/**
 * This trait represents a handle on a Scala compiler module, and its component jars.
 * e.g. scala-compiler, scala-library, scala-reflect, scala-xml ...
 * Immutable.
 */
trait ScalaModule {
  val classJar: IPath
  val sourceJar: Option[IPath]
  /**
   * Are the files pointed to by this module available on the file system ?
   */
  def isValid(): Boolean
  /**
   * Creates a classpath entry for the library.
   */
  def libraryEntries(): IClasspathEntry
  /**
   * Returns a hash string uniquely identifying the module.
   * Depends on the path of cointained archives relative to the Scala plugin's location.
   */
  def getHashString(): String
}

/**
 * This trait represents a handle on a complete Scala installation, containing at least compiler and library modules.
 * Immutable.
 */
trait ScalaInstallation {
  /**
   * A precise Scala version.
   */
  def version: ScalaVersion
  /**
   *  The compiler module itself.
   */
  def compiler: ScalaModule
  /**
   * The library module for this installation.
   */
  def library: ScalaModule
  /**
   * Extra modules, e.g. reflect, swing, actors, xml.
   */
  def extraJars: Seq[ScalaModule]
  /**
   * Returns the whole set of all jars included in this installation.
   */
  def allJars(): Seq[ScalaModule]
  /**
   * Are the registered components of this installation available on the file system ?
   */
  def isValid(): Boolean
}


/**
 * This trait symbolises a Scala Installation Choice.
 * Commonly implemented as a case class with several utility methods.
 * The marker consitutes the choice, it can be :
 * - either a Scala version, in which case the Scala Installation to be used will be
 *   the latest available bundle with the same binary-compatible version (same major, minor) as the one specified
 * - either an Int representing a hash, which points to the hash of an available Scala Installation.
 *
 * Immutable.
 */
trait ScalaInstallationChoice{
  val marker: Either[ScalaVersion, Int]
}

/**
 * This class represents a Scala Project and associated tools necessary to build it.
 *
 * This class is not thread-safe.
 */
trait ScalaProject extends Publisher[ScalaProjectMessage] {

  /**
   * An IProject which is the project object at the Eclipse platform's level.
   */
  val underlying: IProject

  /**
   * The instance of the presentation compiler that runs on this project's source elements.
   */
  val presentationCompiler: ScalaPresentationCompilerProxy

  /**
   *  Does this project have the platform's level of a Scala-corresponding Nature ?
   */
  def hasScalaNature(): Boolean

  /** The direct dependencies of this project. It only returns opened projects. */
  def directDependencies(): Seq[IProject]

  /** All direct and indirect dependencies of this project.
   *
   *  Indirect dependencies are considered only if that dependency is exported by the dependent project.
   *  Consider the following dependency graph:
   *     A -> B -> C
   *
   *  transitiveDependencies(C) = {A, B} iff B *exports* the A project in its classpath
   */
  def transitiveDependencies(): Seq[IProject]

  /** Return the exported dependencies of this project. An exported dependency is
   *  another project this project depends on, and which is exported to downstream
   *  dependencies.
   */
  def exportedDependencies(): Seq[IProject]

  /** The JDT-level project corresponding to this (scala) project. */
  val javaProject: IJavaProject

  /** The Sequence of source folders used by this project */
  def sourceFolders(): Seq[IPath]

  /** Return the output folders of this project. Paths are relative to the workspace root,
   *  and they are handles only (may not exist).
   */
  def outputFolders(): Seq[IPath]

  /** The output folder file-system absolute paths. */
  def outputFolderLocations(): Seq[IPath]

  /** Return the source folders and their corresponding output locations
   *  without relying on NameEnvironment. Does not create folders if they
   *  don't exist already.
   *
   *  @return A map of source folders to their corresponding
   *          output folder.
   */
  def sourceOutputFolders(): Map[IContainer, IContainer]

  /** Return all source files in the source path. It only returns buildable files (meaning
   *  Java or Scala sources).
   */
  def allSourceFiles(): Set[IFile]

  /** Return all the files in the current project. It walks all source entries in the classpath
   *  and respects inclusion and exclusion filters. It returns both buildable files (java or scala)
   *  and all other files in the source path.
   */
  def allFilesInSourceDirs(): Set[IFile]

  /** All arguments passed to scalac, including classpath as well as custom settings. */
  def scalacArguments(): Seq[String]

  /**
   * Initializes compiler settings from an instance of the compiler's scala.tools.nsc.Settings
   * and a filter for settings that should be taken into account. Has various side-effects.
   */
  def initializeCompilerSettings(settings: Settings, filter:Settings#Setting => Boolean)

  /** Return the current project's preference store.
   *  @return A project-specific store if the project is set to use project-specific settings,
   *  a scoped preference store otherwise.
   */
  def storage(): IPreferenceStore

  /**
   * Initialization for the build manager associated to this project
   * @return an initialized EclipseBuildManager
   */
  def buildManager(): EclipseBuildManager

  /**
   * It true, it means all source Files have to be reloaded
   */
  def prepareBuild(): Boolean

  /**
   * Builds the project.
   */
  def build(addedOrUpdated: Set[IFile], removed: Set[IFile], monitor: SubMonitor): Unit

  /** Reset the presentation compiler of projects that depend on this one.
   *  This should be done after a successful build, since the output directory
   *  now contains an up-to-date version of this project.
   */
  def resetDependentProjects(): Unit

  /**
   *  Cleans metadata on the project, such as error markers and classpath validation status
   */
  def clean(implicit monitor: IProgressMonitor): Unit

  /* Classpath Management */

  /** The ScalaClasspath Instance valid for tihs project */
  def scalaClasspath(): ScalaClasspath

  /** Return the classpath entries coming from the JDK.  */
  def jdkPaths(): Seq[IPath]

  /** The result of validation checks performed on classpath */
  def isClasspathValid(): Boolean

  /** Inform this project the classpath was just changed. Triggers validation */
  def classpathHasChanged(queue: Boolean = true): Unit

  /* Installation Management */

  /** Is this project set (through compiler options) to use a scalac compatibility mode to interpret source
   *  at a different version than the one embedded in the compiler ?
   *  @see scalac's `-Xsource` flag
   */
  def isUsingCompatibilityMode(): Boolean

  /**
   * Get the source level configured for this project.
   * @returns a scala version in the form <major>.<minor>
   */
  def getDesiredSourceLevel(): String

  /**
   * Get the ScalaInstallation Choice configured for this project.
   * @returns a ScalaInstallationChoice
   */
  def getDesiredInstallationChoice(): ScalaInstallationChoice

  /**
   * Get the ScalaInstallation used for building this project.
   * The IDE will try to match this to the ScalaInstallation Choice above, but may fail to achieve this,
   * e.g. if the ScalaInstallation Choice points to a version that is no longer on disk.
   * @returns a usable Scala Installation.
   */
  def getDesiredInstallation(): ScalaInstallation

}

object ScalaProject {

  def apply(underlying: IProject): ScalaProject = org.scalaide.core.internal.project.ScalaProject(underlying)

}
