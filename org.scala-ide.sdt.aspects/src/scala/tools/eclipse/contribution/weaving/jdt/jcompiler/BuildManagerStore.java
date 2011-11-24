package scala.tools.eclipse.contribution.weaving.jdt.jcompiler;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;

public class BuildManagerStore {
  
  public static final BuildManagerStore INSTANCE= new BuildManagerStore();
  
  private Map<IProject, File[]> projectToJavaSourceFiles= new HashMap<IProject, File[]>();
  
  private BuildManagerStore() {
  }
  
  public IResourceDelta appendJavaSourceFilesToCompile(IResourceDelta delta, IProject project) {
    if (delta == null) {
      return delta;
    }
    
    File[] files= projectToJavaSourceFiles.get(project);
    if (files == null) {
      return delta;
    }

    // create a new delta
    ExpandableResourceDelta newDelta= ExpandableResourceDelta.duplicate(delta);
    
    // add the additional files
    IWorkspaceRoot workspaceRoot = ResourcesPlugin.getWorkspace().getRoot();
    for (File file: files) {
      for (IFile resource: workspaceRoot.findFilesForLocationURI(file.toURI())) {
        newDelta.addChangedResource(resource);
      }
    }
    
    return newDelta;
  }
  
  public void setJavaSourceFilesToCompile(File[] files, IProject project) {
    if (files == null) {
      projectToJavaSourceFiles.remove(project);
    } else {
      projectToJavaSourceFiles.put(project, files);
    }
  }

}
