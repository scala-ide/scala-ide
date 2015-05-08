package scala.tools.eclipse.contribution.weaving.jdt.jcompiler;

import org.eclipse.core.internal.events.BuildManager;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.jdt.internal.core.builder.AbstractImageBuilder;

import java.util.ArrayList;
import java.util.List;

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
   * Point cut on {@link AbstractImageBuilder#addAllSourceFiles(ArrayList)}
   */
  pointcut addAllSourceFiles(AbstractImageBuilder aib, ArrayList sourceFiles):
    execution(void AbstractImageBuilder+.addAllSourceFiles(ArrayList)) &&
    args(sourceFiles) &&
    this(aib);

  /**
   * Around {@link BuildManager#getDelta(IProject)}
   */
  IResourceDelta around(BuildManager bm, IProject project):
    getDelta(bm, project) {
    return BuildManagerStore.INSTANCE.appendJavaSourceFilesToCompile(proceed(bm, project), project);
  }

  /**
   * Around {@link AbstractImageBuilder#addAllSourceFiles(ArrayList)}
   * When this aspect is off then java compiler takes all java sources in project to compile, if on
   * java compiler takes scope specific java sources only.
   */
  void around(AbstractImageBuilder aib, ArrayList sourceFiles):
    addAllSourceFiles(aib, sourceFiles) {
    proceed(aib, sourceFiles);
    List sourcesToCompile = BuildManagerStore.INSTANCE.filterProjectSources(sourceFiles, aib);
    sourceFiles.clear();
    sourceFiles.addAll(sourcesToCompile);
  }
}
