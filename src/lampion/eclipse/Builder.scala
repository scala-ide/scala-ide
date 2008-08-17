/*
 * Copyright 2005-2008 LAMP/EPFL
 * @author Sean McDirmid
 */
// $Id$

package lampion.eclipse;
import org.eclipse.core.resources._
import org.eclipse.core.runtime.{IProgressMonitor,IPath}
import scala.collection.jcl._

abstract class Builder extends IncrementalProjectBuilder {
  def plugin : Plugin
  def ifile(that : AnyRef) : IFile = 
    if (that.isInstanceOf[IFile]) (that.asInstanceOf[IFile])
    else null
  def ipath(that : AnyRef) =
    if (that.isInstanceOf[IPath]) (that.asInstanceOf[IPath])
    else null

  override def build(kind : Int, args : java.util.Map[_,_], monitor0 : IProgressMonitor) : Array[IProject] = {
    implicit val monitor = monitor0
    import IncrementalProjectBuilder._
    val plugin = this.plugin 
    val project = plugin.projectSafe(getProject).get
    val toBuild = new LinkedHashSet[project.File]
    kind match {
    case CLEAN_BUILD => return project.externalDepends.toList.toArray
    case INCREMENTAL_BUILD|AUTO_BUILD if (!project.doFullBuild) =>
    getDelta(project.underlying).accept(new IResourceDeltaVisitor {
        def visit(delta : IResourceDelta) = ifile(delta.getResource) match {
          case null => true
          case (file) =>
            if (delta.getKind != IResourceDelta.REMOVED) {
              val file0 = project.fileSafe(file)
              if (!file0.isEmpty && project.sourceFolders.exists(_.getLocation.isPrefixOf(file.getLocation))) {
                toBuild += file0.get
              }
            }
            true
        }
      })
      project.externalDepends.map(getDelta).foreach(_.accept(new IResourceDeltaVisitor {
        override def visit(delta : IResourceDelta) : Boolean = {
          val file = ifile(delta.getResource)
          if (delta.getKind == IResourceDelta.REMOVED) return true
          if (file == null) return true
          val paths = plugin.reverseDependencies.get(file.getLocation)
          if (paths.isEmpty) return true
          val i = paths.get.elements 
          while (i.hasNext) {
            val path = ipath(i.next)
            if (project.sourceFolders.exists(_.getLocation.isPrefixOf(path))) {
              i.remove
              project.stale(file.getLocation)
              val p = project.underlying
              val f = p.getFile(path.removeFirstSegments(path.matchingFirstSegments(p.getLocation)))
              toBuild += project.fileSafe(f).get
            }
          }        
          true
        }
      }))
      true
    case _ => 
      project.doFullBuild = false
      project.sourceFolders.foreach(_.accept(new IResourceVisitor {
      def visit(resource : IResource) = ifile(resource) match {
      case null => true
      case (file) =>           
        project.fileSafe(file) match {
        case Some(file0) => toBuild += file0
        case _ =>
        }
        true
      }}))
    }
    // everything that needs to be recompiled is in toBuild now. 
    val built = new LinkedHashSet[project.File] // don't recompile twice.
    var buildAgain = false
    if (monitor != null) monitor.beginTask("build all", 100)
    while (!toBuild.isEmpty) {
      toBuild.foreach{f => f.clearBuildErrors; f.willBuild}
      project.assert(!toBuild.isEmpty)
      
      toBuild.foreach(f => Console.println("build " + f))
      val changed = project.build(toBuild)
      if (!changed.isEmpty) {
        changed.foreach(f => Console.println("changed " + f))
      }
      project.assert(!toBuild.isEmpty)
      built ++= toBuild
      project.assert(!built.isEmpty)
      toBuild.clear
      
      def f(changed : project.File) : Unit = changed.underlying.path match {
        case Some(changed) => plugin.reverseDependencies.get(changed) match {
          case Some(paths) => paths.foreach(path => {
            val file = plugin.workspace.getFileForLocation(path)
            if (file.exists) {
              if (file.getProject == project.underlying) {
                project.fileSafe(file) match {
                  case Some(file) if !built.contains(file) => 
                    if (toBuild add file) {
                      //f(file) // transitive colsure of dependencies...sigh.
                    }
                  case Some(file) => plugin.reverseDependencies(changed) += path
                  case _ => file.touch(monitor)
                }
              } else {
                plugin.projectSafe(file.getProject).foreach(_.stale(changed))
                if (hasBeenBuilt(file.getProject)) buildAgain = true
                file.touch(monitor)
              }
            }
          })
          case None => 
        }
        case None =>
      }
      changed.foreach(f)
    }
    if (buildAgain) needRebuild
    else project.buildDone(built)
    project.externalDepends.toList.toArray
  }
  override def clean(monitor0 : IProgressMonitor) = {
    super.clean(monitor0)
    implicit val monitor = monitor0
    val plugin = this.plugin
    val project = plugin.projectSafe(getProject).get
    project.clean
  }
}
