package scala.tools.eclipse

import org.eclipse.core.resources.{ IFile, IProject, IWorkspace }
import org.eclipse.jdt.internal.core.JavaModelManager
import org.eclipse.jdt.internal.core.search.indexing.IndexManager
import org.eclipse.jdt.internal.core.util.Util

import scala.tools.eclipse.util.ReflectionUtils
import scala.tools.eclipse.javaelements.JDTUtils._

object ScalaIndexManager extends ReflectionUtils {
  private val indexManager = {
    // This is an instance method in 3.3, static in 3.4
    val jmmClazz = Class.forName("org.eclipse.jdt.internal.core.JavaModelManager")
    val getIndexManagerMethod = getDeclaredMethod(jmmClazz, "getIndexManager")
    val jmm = JavaModelManager.getJavaModelManager
    getIndexManagerMethod.invoke(jmm).asInstanceOf[IndexManager]
  }
  
  def addToIndex(f : IFile) {
    val containerPath = f.getProject.getFullPath
    indexManager.remove(Util.relativePath(f.getFullPath, 1), containerPath)
  
    val participant = new ScalaSearchParticipant
	  val document = participant.getDocument(f.getFullPath.toString)
	  val indexLocation = indexManager.computeIndexLocation(containerPath)
	  indexManager.scheduleDocumentIndexing(document, containerPath, indexLocation, participant)
  }
  
  def removeFromIndex(f : IFile) {
    val containerPath = f.getProject.getFullPath
    indexManager.remove(Util.relativePath(f.getFullPath, 1), containerPath)
  }

  def initIndex(project : IProject) {
    flattenProject(project).foreach(addToIndex _)
  }

  def initIndex(workspace : IWorkspace) {
    workspace.getRoot.getProjects.foreach(initIndex _)
  }
}
