package scala.tools.eclipse.contribution.weaving.jdt.core;

import org.eclipse.jdt.internal.core.util.Util;

/**
 * We override the behaviour of isValidCompilationUnitName() for .scala files.
 * The standard implementation applies Java identifier rules on the prefix of
 * the file name, so that, for example, "package.scala" would not be judged
 * valid. See Issue #3266.
 */
@SuppressWarnings("restriction")
public aspect CompilationUnitNameAspect {

	pointcut isValidCompilationUnitName(String name, String sourceLevel, String complianceLevel):
	    args(name, sourceLevel, complianceLevel) &&
		execution(boolean Util.isValidCompilationUnitName(String, String, String));

	boolean around(String name, String sourceLevel, String complianceLevel):
	    isValidCompilationUnitName(name, sourceLevel, complianceLevel) {
		if (name != null && name.endsWith(".scala"))
			return true;
		else
			return proceed(name, sourceLevel, complianceLevel);
	}
}
