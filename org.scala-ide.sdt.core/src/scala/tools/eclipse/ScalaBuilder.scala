/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse

import scala.collection.mutable.HashSet
import java.{ lang => jl, util => ju }
import org.eclipse.core.resources.{ IFile, IncrementalProjectBuilder, IProject, IResource, IResourceDelta, IResourceDeltaVisitor, IResourceVisitor }
import org.eclipse.core.runtime.{ IProgressMonitor, IPath, SubMonitor }
import org.eclipse.jdt.internal.core.JavaModelManager
import org.eclipse.jdt.internal.core.builder.{ JavaBuilder, State }
import scala.tools.eclipse.javaelements.JDTUtils
import scala.tools.eclipse.util.{ FileUtils, ReflectionUtils }
import util.HasLogger
import scala.tools.nsc.interactive.RefinedBuildManager

class ScalaBuilder extends IncrementalProjectBuilder with HasLogger {
  def plugin = ScalaPlugin.plugin

  private val scalaJavaBuilder = new GeneralScalaJavaBuilder
  
  override def clean(monitor : IProgressMonitor) {
    super.clean(monitor)
    val project = plugin.getScalaProject(getProject)
    project.clean(monitor)
    
    ensureProject
    scalaJavaBuilder.clean(monitor)
    project.buildManager.clean(monitor)
    JDTUtils.refreshPackageExplorer
  }
  
  override def build(kind : Int, ignored : ju.Map[String, String], monitor : IProgressMonitor) : Array[IProject] = {
    import IncrementalProjectBuilder._
    import buildmanager.sbtintegration.EclipseSbtBuildManager
    
    val project = plugin.getScalaProject(getProject)
    
    // check the classpath
    if (!project.isClasspathValid()) {
      // bail out is the classpath in not valid
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
            def visit(delta : IResourceDelta) = {
              delta.getResource match {
                case file : IFile if plugin.isBuildable(file) && project.sourceFolders.exists(_.isPrefixOf(file.getLocation)) =>
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
          project.buildManager match {
            case _: EclipseSbtBuildManager =>
              
              def hasChanges(prj: IProject): Boolean = {
                val delta = getDelta(prj)
                delta == null || delta.getKind != IResourceDelta.NO_CHANGE
              }
              
              if (project.externalDepends.exists(hasChanges)) {
                // reset presentation compilers if a dependency has been rebuilt
                logger.debug("Resetting presentation compiler for %s due to dependent project change".format(project.underlying.getName()))
                project.resetPresentationCompiler
                
                // in theory need to be able to identify the exact dependencies
                // but this is deeply rooted inside the sbt dependency tracking mechanism
                // so we just tell it to have a look at all the files 
                // and it will figure out the exact changes during initialization
                addedOrUpdated0 ++= allSourceFiles
              }
            case _ => 
          }
          (Set.empty ++ addedOrUpdated0, Set.empty ++ removed0)
        case CLEAN_BUILD | FULL_BUILD =>
          (allSourceFiles, Set.empty[IFile])
      }
    }

    val subMonitor = SubMonitor.convert(monitor, 100).newChild(100, SubMonitor.SUPPRESS_NONE)
    subMonitor.beginTask("Running Scala Builder", 100)
      
    project.build(addedOrUpdated, removed, subMonitor)
    
    val depends = project.externalDepends.toList.toArray
    
    /** The Java builder has to be run for copying resources (non-source files) to the output directory.
     * 
     *  We need to run it when using the refined builder, or the SBT builder and no Java sources have been modified 
     *  (since the SBT builder automatically calls the JDT builder internally if there are modified Java sources).
     */
    def shouldRunJavaBuilder: Boolean = {
      (project.buildManager.isInstanceOf[RefinedBuildManager]
         || (needToCopyResources && !addedOrUpdated.exists(_.getName().endsWith(plugin.javaFileExtn)))
      )
    }
    
    // SBT build manager already calls java builder internally
    if (allSourceFiles.exists(FileUtils.hasBuildErrors(_)) || !shouldRunJavaBuilder)
      depends
    else {
      ensureProject
      val javaDepends = scalaJavaBuilder.build(kind, ignored, subMonitor) 
      val modelManager = JavaModelManager.getJavaModelManager
      val state = modelManager.getLastBuiltState(getProject, null).asInstanceOf[State]
      val newState = if (state ne null) state
        else {
          ScalaJavaBuilderUtils.initializeBuilder(scalaJavaBuilder, 0, false)
          StateUtils.newState(scalaJavaBuilder)
        }
      StateUtils.tagAsStructurallyChanged(newState)
      StateUtils.resetStructurallyChangedTypes(newState)
      modelManager.setLastBuiltState(getProject, newState)
      JDTUtils.refreshPackageExplorer
      (Set.empty ++ depends ++ javaDepends).toArray
    }
  }
  
  def ensureProject = {
    if (scalaJavaBuilder.getProject == null)
      scalaJavaBuilder.setProject0(getProject)
  }
}

object StateUtils extends ReflectionUtils {
  private val stateClazz = Class.forName("org.eclipse.jdt.internal.core.builder.State").asInstanceOf[Class[State]]
  private val stateCtor = getDeclaredConstructor(stateClazz, classOf[JavaBuilder])
  private val tagAsStructurallyChangedMethod = getDeclaredMethod(stateClazz, "tagAsStructurallyChanged")
  private val structurallyChangedTypesField = getDeclaredField(stateClazz, "structurallyChangedTypes")
  
  def newState(b : JavaBuilder) = stateCtor.newInstance(b)
  
  def tagAsStructurallyChanged(s : State) = tagAsStructurallyChangedMethod.invoke(s)
  
  def resetStructurallyChangedTypes(s : State) = structurallyChangedTypesField.set(s, null)
}
