package scala.tools.eclipse.contribution.weaving.jdt.core;

import org.eclipse.jdt.internal.core.util.Util;
import org.eclipse.jdt.core.JavaConventions;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.jdt.internal.core.JavaModelStatus;

/**
 * We override the behaviour of isValidCompilationUnitName() for .scala files.
 * The standard implementation applies Java identifier rules on the prefix of
 * the file name, so that, for example, "package.scala" would not be judged
 * valid. See Issues #3266, #1000859.
 */
@SuppressWarnings("restriction")
public aspect CompilationUnitNameAspect {

	private static boolean isScalaFileName(String name) {
		return name != null && name.length() > 6 && name.endsWith(".scala");
	}

	pointcut isValidCompilationUnitName(String name, String sourceLevel, String complianceLevel):
	    args(name, sourceLevel, complianceLevel) &&
		execution(boolean Util.isValidCompilationUnitName(String, String, String));

	boolean around(String name, String sourceLevel, String complianceLevel):
	    isValidCompilationUnitName(name, sourceLevel, complianceLevel) {
		if (isScalaFileName(name))
			return true;
		else
			return proceed(name, sourceLevel, complianceLevel);
	}

	pointcut validateCompilationUnitName(String name, String sourceLevel, String complianceLevel):
	    args(name, sourceLevel, complianceLevel) &&
		execution(IStatus JavaConventions.validateCompilationUnitName(String, String, String));

	IStatus around(String name, String sourceLevel, String complianceLevel):
	    validateCompilationUnitName(name, sourceLevel, complianceLevel) {
		if (isScalaFileName(name))
			return JavaModelStatus.VERIFIED_OK;
		else
			return proceed(name, sourceLevel, complianceLevel);
	}
}
