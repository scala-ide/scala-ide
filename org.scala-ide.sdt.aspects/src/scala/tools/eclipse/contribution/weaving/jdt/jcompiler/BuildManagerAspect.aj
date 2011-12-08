package scala.tools.eclipse.contribution.weaving.jdt.jcompiler;

import org.eclipse.core.internal.events.BuildManager;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResourceDelta;

/**
 * Weaving on the BuildManager, to be able to replace and update
 * deltas.
 */
@SuppressWarnings("restriction")
public aspect BuildManagerAspect {

  /**
   * Point cut on {@link BuildManager#getDelta(IProject)}
   */
  pointcut getDelta(BuildManager bm, IProject project):
    execution(IResourceDelta BuildManager+.getDelta(IProject)) &&
    args(project) &&
    this(bm);

  /**
   * Around {@link BuildManager#getDelta(IProject)}
   */
  IResourceDelta around(BuildManager bm, IProject project):
    getDelta(bm, project) {
    return BuildManagerStore.INSTANCE.appendJavaSourceFilesToCompile(proceed(bm, project), project);
  }

}
