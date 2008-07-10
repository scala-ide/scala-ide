/*
 * Copyright 2005-2008 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse.javaelements

import scala.collection.mutable.{ ArrayBuffer, Buffer, HashMap }

import org.eclipse.core.resources.{
  IContainer, IFile, IFolder, IProject, IResource, IResourceVisitor, IWorkspace }
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.{
  IClasspathEntry, ICompilationUnit, IJavaProject, IPackageFragment,
  IPackageFragmentRoot, JavaCore, JavaModelException, WorkingCopyOwner }
import org.eclipse.jdt.internal.core.{
  JavaElement, JavaModelManager, OpenableElementInfo }

/**
 * Maintains a cache containing ICompilationUnits for .scala files and is
 * responsible for their instantiation.
 */
object ScalaCompilationUnitManager {
  private val compilationUnitStore = HashMap[IFile, ScalaCompilationUnit]()

  /**
   * Returns the ScalaCompilationUnit corresponding to the given
   * CompilationUnit, if there is one, otherwise return the unit itself
   * @param cu
   * @return
   */
  def mapToScalaCompilationUnit(cu : ICompilationUnit) : ICompilationUnit = {
    if (cu == null)
      return cu
 
    val res = cu.getResource
    if (res.getType == IResource.FILE) {
      val ajcu = getScalaCompilationUnit(res.asInstanceOf[IFile])
      if (ajcu != null)
        return ajcu
    }
    cu
  }

  /**
   * Returns the WorkingCopyOwner used to create ScalaCompilationUnits
   * @return
   */
  def defaultScalaWorkingCopyOwner : WorkingCopyOwner =
    ScalaWorkingCopyOwner

  def getScalaCompilationUnit(file : IFile) : ScalaCompilationUnit = {
    var cu = getScalaCompilationUnitFromCache(file)
    if (cu != null)
      return cu
    if (creatingCUisAllowedFor(file))
      cu = createCU(file)
    cu
  }

  def getScalaCompilationUnitFromCache(file : IFile) : ScalaCompilationUnit =
    compilationUnitStore.getOrElse(file, null)
  
  //returns true if it was already there, and false if it needed to be inserted
  def ensureUnitIsInModel(scu : ScalaCompilationUnit) : Boolean = {
    //ensure unit is in the model
    val info = scu.getParent.asInstanceOf[JavaElement].getElementInfo.asInstanceOf[OpenableElementInfo]
    val elems = info.getChildren
    for (i <- 0 until elems.length) {
      val element = elems(i)
      if (element == scu)
        return true
    }
    info.addChild(scu)
    false
  }
  
  def getScalaCompilationUnitsForPackage(pFragment : IPackageFragment) = {
    val scus = new ArrayBuffer[ScalaCompilationUnit]
    val folder = pFragment.getCorrespondingResource
    if(folder != null) {
      folder.accept(new IResourceVisitor {
        def visit(resource : IResource) : Boolean = {
          if (resource.isInstanceOf[IFile]) {
            val f = resource.asInstanceOf[IFile] 
            if (f.getFileExtension().equals("scala"))
              scus += getScalaCompilationUnit(f)
          }
          resource == folder
        }
      })
    }
    scus
  }
  
  def getScalaCompilationUnits(jp : IJavaProject) = {
    val scus = new ArrayBuffer[ScalaCompilationUnit]
    jp.getProject.accept(new IResourceVisitor {
      def visit(resource : IResource ) : Boolean = {
        if(resource.isInstanceOf[IFile] && "scala".equals(resource.getFileExtension)) {
          val scu = getScalaCompilationUnit(resource.asInstanceOf[IFile])
          if(scu != null)
            scus += scu
        }        
        resource.getType == IResource.FOLDER || resource.getType == IResource.PROJECT
      }})
    scus
  }
  
  def getScalaCompilationUnits(root : IPackageFragmentRoot) = {
    val scus = new ArrayBuffer[ScalaCompilationUnit]
    root.getResource.accept(new IResourceVisitor {
      def visit(resource : IResource) : Boolean = {
        if(resource.isInstanceOf[IFile] && "scala".equals(resource.getFileExtension)) {
          val scu = getScalaCompilationUnit(resource.asInstanceOf[IFile])
          if(scu != null)
            scus += scu
        }        
        return resource.getType == IResource.FOLDER || resource.getType == IResource.PROJECT;
      }})
    scus
  }

  def removeFileFromModel(file : IFile) = {
    val scu = compilationUnitStore.getOrElse(file, null)
    if (scu != null) {
      try {
        if (file.getProject.isOpen) {          
          val info = scu.getParent.asInstanceOf[JavaElement].getElementInfo.asInstanceOf[OpenableElementInfo]
          info.removeChild(scu)
        }
        JavaModelManager.getJavaModelManager.removeInfoAndChildren(scu)
      } catch {
        case _ : JavaModelException =>
      }
      compilationUnitStore -= file
    }
  }

  private def createCU(file : IFile) : ScalaCompilationUnit = {
    val scu = new ScalaCompilationUnit(file)

    try {
      val info = scu.getParent.asInstanceOf[JavaElement].getElementInfo.asInstanceOf[OpenableElementInfo]
      info.removeChild(scu) // Remove identical CompilationUnit if it exists
      info.addChild(scu)
      compilationUnitStore.put(file, scu)
    } catch {
      case _ : JavaModelException =>
    }
    scu
  }

  def creatingCUisAllowedFor(file : IFile) : Boolean = {
    file != null &&
    (file.getFileExtension == "scala" &&
    ScalaPlugin.isScalaProject(file.getProject) &&
    (JavaCore.create(file.getProject).isOnClasspath(file)))
  }

  def initCompilationUnits(project : IProject) = flattenProject(project).foreach{createCU _}

  def initCompilationUnits(workspace : IWorkspace) =
    for (project <- workspace.getRoot.getProjects)
      flattenProject(project).foreach{createCU _}

  def getCachedCUs(project : IProject) = {
    for (f <- compilationUnitStore.keys if f.getProject == project)
      yield compilationUnitStore.get(f).get
  }
  
  private def flattenProject(project : IProject) : Iterator[IFile] = {
    try {
      if (!ScalaPlugin.isScalaProject(project))
        return Iterator.empty
      
      val jp = JavaCore.create(project)
      jp.getRawClasspath.filter(_.getEntryKind == IClasspathEntry.CPE_SOURCE).elements.flatMap{
        entry => {
	        val p = entry.getPath
	        val folder = if (p.segmentCount == 1) project else project.getFolder(p.removeFirstSegments(1)) 
	        flatten(folder)
        }
      }
    } catch {
      case _ : JavaModelException => Iterator.empty
    }
  }

  private def flatten(r : IResource) : Iterator[IFile] = {
    try {
      r match {
        case r if !r.exists => Iterator.empty
        case folder : IFolder if folder.getType == IResource.FOLDER => folder.members.elements.flatMap{flatten _}
        case file : IFile if file.getType == IResource.FILE && file.getFileExtension == "scala" => Iterator.single(file)
        case _ => Iterator.empty
      }
    } catch {
      case _ : CoreException => Iterator.empty
    }
  }
}
