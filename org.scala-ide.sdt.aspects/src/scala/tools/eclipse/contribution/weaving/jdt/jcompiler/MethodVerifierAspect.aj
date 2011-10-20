package scala.tools.eclipse.contribution.weaving.jdt.jcompiler;

import java.util.List;

import org.eclipse.jdt.internal.compiler.lookup.MethodBinding;
import org.eclipse.jdt.internal.compiler.lookup.MethodVerifier;

@SuppressWarnings("restriction")
public aspect MethodVerifierAspect {

	pointcut checkAbstractMethod(MethodVerifier methodVerifier,
			MethodBinding abstractMethod): 
		execution(void MethodVerifier+.checkAbstractMethod(MethodBinding)) && 
		args(abstractMethod) &&
		this(methodVerifier);

	pointcut checkInheritedMethods(MethodVerifier methodVerifier,
			MethodBinding[] methods, int length): 
		execution(void MethodVerifier+.checkInheritedMethods(MethodBinding[], int)) && 
		args(methods, length) &&
		this(methodVerifier);

	private IMethodVerifierProvider getMethodVerifierProvider() {
		List<IMethodVerifierProvider> providers = MethodVerifierProviderRegistry
				.getInstance().getProviders();

		if (providers.size() > 1) {
			throw new IllegalStateException(
					"Exactly one provider is expected for extension '"
							+ MethodVerifierProviderRegistry.METHOD_VERIFIER_PROVIDERS_EXTENSION_POINT
							+ "'. Found " + providers.size());
		}

		return (providers.isEmpty()) ? null : providers.get(0);
	}

	void around(MethodVerifier methodVerifier, MethodBinding abstractMethod):
    checkAbstractMethod(methodVerifier, abstractMethod) {
		IMethodVerifierProvider provider = getMethodVerifierProvider();

		if (provider == null) {
			proceed(methodVerifier, abstractMethod);
			return;
		}

		if (provider.isConcreteTraitMethod(abstractMethod))
			// stop the abstract method's check if the method is a non-deferred
			// (i.e. concrete) trait method
			return;
		else
			proceed(methodVerifier, abstractMethod);
	}

	/**
	 * The way I understand it, `checkMethods` groups methods according to their
	 * signature. If more than one method with the same signature is found (in the 
	 * superclasses or inherited interfaces), then `checkInheritedMethods` is passed 
	 * the array of `methods` bindings with the same signature. 
	 * 
	 * When `methods` contain only deferred members, then we need to check whether any 
	 * of these `methods` is actually defined in a trait with a concrete implementation.
	 * If that is the case, then no error should be reported in the editor.
	 */
	void around(MethodVerifier methodVerifier, MethodBinding[] methods,
			int length):
		checkInheritedMethods(methodVerifier, methods, length) {
		IMethodVerifierProvider provider = getMethodVerifierProvider();

		if (provider == null || methods == null) {
			proceed(methodVerifier, methods, length);
			return;
		}

		for (int i = 0; i < methods.length; i++) {
			MethodBinding method = methods[i];
			// Yes, `method` can be null apparently. JDT internals beauty...
			if (method != null && provider.isConcreteTraitMethod(method)) {
				// prevent `MethodVerifier.checkInheritedMethods` to be called
				// since one of the mixed traits contains a concrete implementation. 
				return; 
			}
		}

		proceed(methodVerifier, methods, length);
	}
}