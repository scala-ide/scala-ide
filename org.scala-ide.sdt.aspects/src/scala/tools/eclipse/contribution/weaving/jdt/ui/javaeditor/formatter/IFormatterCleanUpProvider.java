package scala.tools.eclipse.contribution.weaving.jdt.ui.javaeditor.formatter;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.ui.cleanup.ICleanUpFix;

public interface IFormatterCleanUpProvider {

	ICleanUpFix createCleanUp(ICompilationUnit cu);

}
