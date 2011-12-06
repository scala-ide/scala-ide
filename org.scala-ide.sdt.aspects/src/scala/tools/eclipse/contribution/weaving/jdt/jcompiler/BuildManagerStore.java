package scala.tools.eclipse.contribution.weaving.jdt.jcompiler;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;

/**
 * Class used to store java files to be compile per projects
 */
public class BuildManagerStore {
  
  /**
   * Singleton
   */
  public static final BuildManagerStore INSTANCE= new BuildManagerStore();
  
  /**
   * Project to java files to compile
   */
  private Map<IProject, File[]> projectToJavaSourceFiles= new HashMap<IProject, File[]>();
  
  private BuildManagerStore() {
  }
  
  /**
   * Return a resource delta containing the same changes as the given delta, plus the files set to be compiled for 
   * the given project.<br>
   * If delta is <code>null</code>, or there are no files to compile for the given project, return the given resource delta.
   */
  public IResourceDelta appendJavaSourceFilesToCompile(IResourceDelta delta, IProject project) {
    if (delta == null) {
      return delta;
    }
    
    // no need to create a new resource delta if no files have to be added.
    File[] files= projectToJavaSourceFiles.get(project);
    if (files == null || files.length == 0) {
      return delta;
    }

    // create a new delta
    ExpandableResourceDelta newDelta= ExpandableResourceDelta.duplicate(delta);
    
    // add the additional files
    IWorkspaceRoot workspaceRoot = ResourcesPlugin.getWorkspace().getRoot();
    for (File file: files) {
      for (IFile resource: workspaceRoot.findFilesForLocationURI(file.toURI())) {
        // filter only the resources on the right project, to support nested projects
        if (resource.getProject() == project) {
          newDelta.addChangedResource(resource);
        }
      }
    }
    
    return newDelta;
  }
  
  /**
   * Set the java files to compile for the given project.<br>
   * Use <code>null</code> to reset the data for a project.
   */
  public void setJavaSourceFilesToCompile(File[] files, IProject project) {
    if (files == null) {
      projectToJavaSourceFiles.remove(project);
    } else {
      projectToJavaSourceFiles.put(project, files);
    }
  }

}
