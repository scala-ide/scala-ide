package org.scalaide.core.internal.builder

import java.{ util => ju }

import scala.collection.mutable.HashSet

import org.eclipse.core.resources.IFile
import org.eclipse.core.resources.IProject
import org.eclipse.core.resources.IResourceDelta
import org.eclipse.core.resources.IResourceDeltaVisitor
import org.eclipse.core.resources.IncrementalProjectBuilder
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.core.runtime.SubMonitor
import org.eclipse.core.runtime.jobs.ISchedulingRule
import org.eclipse.jdt.internal.core.builder.JavaBuilder
import org.eclipse.jdt.internal.core.builder.State
import org.scalaide.core.IScalaPlugin
import org.scalaide.core.SdtConstants
import org.scalaide.core.internal.jdt.util.JDTUtils
import org.scalaide.logging.HasLogger
import org.scalaide.ui.internal.preferences.ResourcesPreferences
import org.scalaide.util.eclipse.EditorUtils
import org.scalaide.util.eclipse.FileUtils
import org.scalaide.util.internal.ReflectionUtils

class ScalaBuilder extends IncrementalProjectBuilder with JDTBuilderFacade with HasLogger {

  override def project = getProject()

  /** Lock only the current project during build. */
  override def getRule(kind: Int, args: java.util.Map[String, String]): ISchedulingRule =
    project

  override def clean(monitor: IProgressMonitor): Unit = {
    super.clean(monitor)
    val project = IScalaPlugin().getScalaProject(this.project)
    project.clean(monitor)

    ensureProject()
    scalaJavaBuilder.clean(monitor)
    JDTUtils.refreshPackageExplorer
  }

  override def build(kind: Int, ignored: ju.Map[String, String], monitor: IProgressMonitor): Array[IProject] = {
    import org.eclipse.core.resources.IncrementalProjectBuilder._

    val project = IScalaPlugin().getScalaProject(this.project)

    // check the classpath
    if (!project.isClasspathValid()) {
      // bail out if the classpath in not valid
      return new Array[IProject](0)
    }

    val allSourceFiles = project.allSourceFiles()
    val allFilesInSourceDirs = project.allFilesInSourceDirs()

    val needToCopyResources = allSourceFiles.size != allFilesInSourceDirs.size

    val (addedOrUpdated, removed) = if (project.prepareBuild())
      (allSourceFiles, Set.empty[IFile])
    else {
      kind match {
        case INCREMENTAL_BUILD | AUTO_BUILD =>
          val addedOrUpdated0 = new HashSet[IFile] ++ allSourceFiles.filter(FileUtils.hasBuildErrors(_))
          val removed0 = new HashSet[IFile]

          getDelta(project.underlying).accept(new IResourceDeltaVisitor {
            override def visit(delta: IResourceDelta) = {
              delta.getResource match {
                case file: IFile if FileUtils.isBuildable(file) && project.sourceFolders.exists(_.isPrefixOf(file.getLocation)) =>
                  delta.getKind match {
                    case IResourceDelta.ADDED | IResourceDelta.CHANGED =>
                      addedOrUpdated0 += file
                    case IResourceDelta.REMOVED =>
                      removed0 += file
                    case _ =>
                  }
                case _ =>
              }
              true
            }
          })
          // Only for sbt which is able to track external dependencies properly
          if (project.buildManager.canTrackDependencies) {
            def hasChanges(prj: IProject): Boolean = {
              val delta = getDelta(prj)
              delta == null || delta.getKind != IResourceDelta.NO_CHANGE
            }

            if (project.directDependencies.exists(hasChanges)) {
              // reset presentation compilers if a dependency has been rebuilt
              logger.debug(s"Restart presentation compiler for ${project.underlying.getName} due to dependent project change.")
              project.presentationCompiler.askRestart()

              // in theory need to be able to identify the exact dependencies
              // but this is deeply rooted inside the sbt dependency tracking mechanism
              // so we just tell it to have a look at all the files
              // and it will figure out the exact changes during initialization
              addedOrUpdated0 ++= allSourceFiles
            }
          }
          (Set.empty ++ addedOrUpdated0, Set.empty ++ removed0)
        case CLEAN_BUILD | FULL_BUILD =>
          (allSourceFiles, Set.empty[IFile])
      }
    }

    val subMonitor = SubMonitor.convert(monitor, 100).newChild(100, SubMonitor.SUPPRESS_NONE)
    subMonitor.beginTask("Running Scala Builder on " + project.underlying.getName, 100)

    logger.info("Building project " + project)
    project.build(addedOrUpdated, removed, subMonitor)
    TaskManager.updateTasks(project, addedOrUpdated)

    val depends = project.transitiveDependencies

    /* The Java builder has to be run for copying resources (non-source files) to the output directory.
     *
     * We need to run it when no Java sources have been modified
     * (since the SBT builder automatically calls the JDT builder internally if there are modified Java sources).
     */
    def shouldRunJavaBuilder: Boolean = {
      (needToCopyResources && !addedOrUpdated.exists(_.getName().endsWith(SdtConstants.JavaFileExtn)))
    }

    // SBT build manager already calls java builder internally
    val ret =
      if (allSourceFiles.exists(FileUtils.hasBuildErrors(_)) || !shouldRunJavaBuilder)
        depends.toArray
      else {
        ensureProject()
        val javaDepends = scalaJavaBuilder.build(kind, ignored, subMonitor)
        refresh()
        (Set.empty ++ depends ++ javaDepends).toArray
      }

    val isPcAutoRestartEnabled = IScalaPlugin().getPreferenceStore.getBoolean(ResourcesPreferences.PRES_COMP_AUTO_RESTART)
    if (isPcAutoRestartEnabled) {
      EditorUtils.withCurrentScalaSourceFile { ssf ⇒
        if (Option(ssf.getProblems()).exists(_.nonEmpty)) {
          logger.debug(s"Restarting presentation compiler of ${project.underlying.getName} due to finished build.")
          project.presentationCompiler.askRestart()
        }
      }
    }

    ret
  }
}

object StateUtils extends ReflectionUtils {
  private val stateClazz = Class.forName("org.eclipse.jdt.internal.core.builder.State").asInstanceOf[Class[State]]
  private val stateCtor = getDeclaredConstructor(stateClazz, classOf[JavaBuilder])
  private val tagAsStructurallyChangedMethod = getDeclaredMethod(stateClazz, "tagAsStructurallyChanged")
  private val structurallyChangedTypesField = getDeclaredField(stateClazz, "structurallyChangedTypes")

  def newState(b: JavaBuilder) = stateCtor.newInstance(b)

  def tagAsStructurallyChanged(s: State) = tagAsStructurallyChangedMethod.invoke(s)

  def resetStructurallyChangedTypes(s: State) = structurallyChangedTypesField.set(s, null)
}
