package scala.tools.eclipse.contribution.weaving.jdt.launching;

import java.util.Set;

import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;

public interface ISearchMethods {
  /** Return all @Test annotated methods in the given type. */
  public Set<String> getTestMethods(IJavaProject project, IType type);
}
