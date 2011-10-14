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

	void around(MethodVerifier methodVerifier, MethodBinding abstractMethod):
    checkAbstractMethod(methodVerifier, abstractMethod) {
		List<IMethodVerifierProvider> providers = MethodVerifierProviderRegistry
				.getInstance().getProviders();

		if (providers.isEmpty()) {
			proceed(methodVerifier, abstractMethod);
			return;
		}

		if (providers.size() > 1) {
			throw new IllegalStateException(
					"Exactly one provider is expected for extension '"
							+ MethodVerifierProviderRegistry.METHOD_VERIFIER_PROVIDERS_EXTENSION_POINT
							+ "'. Found " + providers.size());
		}

		IMethodVerifierProvider provider = providers.get(0);

		if (provider.isConcreteTraitMethod(abstractMethod))
			// stop the abstract method's check if the method is a non-deferred (i.e. concrete) trait method
			return; 
		else
			proceed(methodVerifier, abstractMethod);
	}
}