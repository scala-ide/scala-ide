package scala.tools.eclipse.contribution.weaving.jdt.cfprovider;

import scala.tools.eclipse.contribution.weaving.jdt.util.AbstractProviderRegistry;

public class LineNumberCheckRegistry extends AbstractProviderRegistry<ILineNumberCheck> {
	private static final LineNumberCheckRegistry INSTANCE = new LineNumberCheckRegistry();
    public static String LNCPROVIDERS_EXTENSION_POINT = "org.scala-ide.sdt.aspects.lncheck"; //$NON-NLS-1$
	public static LineNumberCheckRegistry getInstance() {
		return INSTANCE;
	}
	@Override
	protected String getExtensionPointId() {
	    return LNCPROVIDERS_EXTENSION_POINT;
	}
}
