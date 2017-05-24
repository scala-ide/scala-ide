package scala.tools.eclipse.contribution.weaving.jdt.ui.document;

import scala.tools.eclipse.contribution.weaving.jdt.util.AbstractProviderRegistry;

public class MasterProjectionDocumentProviderRegistry extends AbstractProviderRegistry<IMasterProjectionDocumentProvider> {

	private static final MasterProjectionDocumentProviderRegistry INSTANCE = new MasterProjectionDocumentProviderRegistry();

    public static String MASTER_PROJ_DOC_PROVIDERS_EXTENSION_POINT = "org.scala-ide.sdt.aspects.masterprojdocprovider"; //$NON-NLS-1$

	public static MasterProjectionDocumentProviderRegistry getInstance() {
		return INSTANCE;
	}

	@Override
	protected String getExtensionPointId() {
	    return MASTER_PROJ_DOC_PROVIDERS_EXTENSION_POINT;
	}
}