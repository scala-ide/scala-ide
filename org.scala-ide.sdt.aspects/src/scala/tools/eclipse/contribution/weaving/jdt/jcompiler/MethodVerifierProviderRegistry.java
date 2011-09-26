package scala.tools.eclipse.contribution.weaving.jdt.jcompiler;

import scala.tools.eclipse.contribution.weaving.jdt.util.AbstractProviderRegistry;

public class MethodVerifierProviderRegistry extends
		AbstractProviderRegistry<IMethodVerifierProvider> {
	private static final MethodVerifierProviderRegistry INSTANCE = new MethodVerifierProviderRegistry();

	public static String METHOD_VERIFIER_PROVIDERS_EXTENSION_POINT = "org.scala-ide.sdt.aspects.method_verifier"; //$NON-NLS-1$

	public static MethodVerifierProviderRegistry getInstance() {
		return INSTANCE;
	}

	@Override
	protected String getExtensionPointId() {
		return METHOD_VERIFIER_PROVIDERS_EXTENSION_POINT;
	}
}
