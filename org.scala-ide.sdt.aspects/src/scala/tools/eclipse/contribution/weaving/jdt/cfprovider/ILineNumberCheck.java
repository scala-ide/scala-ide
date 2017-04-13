package scala.tools.eclipse.contribution.weaving.jdt.cfprovider;

import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.debug.core.IJavaBreakpoint;

public interface ILineNumberCheck {
  IType realClassFile(IType classFile, IJavaBreakpoint breakpoint);
}
