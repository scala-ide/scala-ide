package scala.tools.eclipse.contribution.weaving.jdt.jcompiler;

import org.eclipse.core.internal.events.BuildManager;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResourceDelta;

@SuppressWarnings("restriction")
public aspect BuildManagerAspect {

  pointcut getDelta(BuildManager bm, IProject project):
    execution(IResourceDelta BuildManager+.getDelta(IProject)) &&
    args(project) &&
    this(bm);
  
  IResourceDelta around(BuildManager bm, IProject project):
    getDelta(bm, project) {
    System.out.println("[luc] getDelta() for " + project.getName());
    return BuildManagerStore.INSTANCE.appendJavaSourceFilesToCompile(proceed(bm, project), project);
  }

}
