package scala.tools.eclipse.contribution.weaving.jdt.ui.document;

import org.eclipse.jface.text.IDocument;

public interface IMasterProjectionDocumentProvider {
	IDocument extractActualMaster(IDocument master);
}
