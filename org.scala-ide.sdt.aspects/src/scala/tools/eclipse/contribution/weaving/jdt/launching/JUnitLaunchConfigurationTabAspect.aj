package scala.tools.eclipse.contribution.weaving.jdt.launching;

import java.util.HashSet;
import java.util.Set;

import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.internal.junit.launcher.TestKind;
import org.eclipse.jdt.junit.launcher.JUnitLaunchConfigurationTab;

@SuppressWarnings("restriction")
public privileged aspect JUnitLaunchConfigurationTabAspect {

  pointcut getMethodsForType(IJavaProject javaProject, IType type, TestKind testKind): 
    execution(private Set<String> JUnitLaunchConfigurationTab.getMethodsForType(IJavaProject, IType, TestKind)) && args(javaProject, type, testKind);

  Set<String> around(IJavaProject javaProject, IType type, TestKind testKind) : getMethodsForType(javaProject, type, testKind) {
    if ((testKind == null) || testKind.getId().startsWith("org.eclipse.jdt.junit.loader")) {
      return proceed(javaProject, type, testKind);
    } else if (testKind.getFinder() instanceof ISearchMethods) {
      return ((ISearchMethods)testKind.getFinder()).getTestMethods(javaProject, type);
    } else
      return new HashSet<String>();
  }
}
