package scala.tools.eclipse.contribution.weaving.jdt.jcompiler;

import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import static java.util.Arrays.asList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.jdt.internal.core.builder.AbstractImageBuilder;
import org.eclipse.jdt.internal.core.builder.JavaBuilder;
import org.eclipse.jdt.internal.core.builder.SourceFile;

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

  private IProject project(AbstractImageBuilder imageBuilder) {
	Field[] fields = AbstractImageBuilder.class.getDeclaredFields();
	Field javaBuilderField = null;
	for (Field f: fields) {
	  if ("javaBuilder".equals(f.getName())) {
	    javaBuilderField = f;
	    javaBuilderField.setAccessible(true);
	  } 
	}
	JavaBuilder builder = null;
	try {
	  builder = (JavaBuilder)javaBuilderField.get(imageBuilder);
	  if (builder == null) {
	    throw new IllegalArgumentException("java builder of image builder is null");
	  }
	} catch(Exception e) {
	  throw new IllegalArgumentException("image builder met problems with retrieving java builder", e);
	}
    return builder.getProject();
  }

  /**
   * Defensively filters <code>SourceFile</code> of current project in given scope.<br/>
   * @param sources
   * @param imageBuilder
   * @return these <code>SourceFile</code> which resources belong to current compilation scope
   */
  public List<SourceFile> filterProjectSources(List<SourceFile> sources, AbstractImageBuilder imageBuilder) {
	List<SourceFile> sourcesToCompile = new ArrayList<SourceFile>();
	File[] scopeProjectSources = projectToJavaSourceFiles.get(project(imageBuilder));
	List<File> projectFiles = scopeProjectSources != null ? asList(scopeProjectSources) : new ArrayList<File>();
	for (SourceFile source: sources) {
	  File file = source.resource.getRawLocation().makeAbsolute().toFile();
	  if (projectFiles.contains(file)) {
		sourcesToCompile.add(source);
	  }
	}
	return sourcesToCompile;
  }
}
