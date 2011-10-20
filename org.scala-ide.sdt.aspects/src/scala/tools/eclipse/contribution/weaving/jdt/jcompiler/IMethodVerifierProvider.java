package scala.tools.eclipse.contribution.weaving.jdt.jcompiler;

import org.eclipse.jdt.internal.compiler.lookup.MethodBinding;

@SuppressWarnings("restriction")
public interface IMethodVerifierProvider {
	boolean isConcreteTraitMethod(MethodBinding abstractMethod);
}
