package scala.tools.eclipse.contribution.weaving.jdt.ui.document;

import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.projection.ProjectionDocumentManager;

public privileged aspect CreateSlaveProjectionDocument {
	pointcut createSlaveDocument(IDocument master):
		execution(IDocument ProjectionDocumentManager.createSlaveDocument(IDocument)) &&
		args(master);

	IDocument around(IDocument master): createSlaveDocument(master) {
		IDocument extracted = master;
		for (IMasterProjectionDocumentProvider provider : MasterProjectionDocumentProviderRegistry.getInstance().getProviders()) {
			extracted = provider.extractActualMaster(extracted);
	    }
		return proceed(extracted);
	}
}
