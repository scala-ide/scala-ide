package org.scalaide.core.internal.launching

import org.eclipse.core.resources.IProject
import org.eclipse.jdt.launching.JavaLaunchDelegate

class ScalaLaunchDelegate extends JavaLaunchDelegate with ClasspathGetterForLaunchDelegate
    with ProblemHandlersForLaunchDelegate with MainClassFinalCheckForLaunchDelegate {
  override val existsProblemsAccessor: IProject => Boolean = existsProblems _
}
