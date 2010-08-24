package scala.tools.eclipse.contribution.weaving.jdt.debug;

import org.eclipse.jdt.internal.debug.ui.BreakpointMarkerUpdater;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.Position;

/**
 * We suppress BreakpointMarkerUpdater, which had a habit of either removing
 * breakpoints in Scala files or throwing exceptions (see #1901, #1000113)
 */
@SuppressWarnings("restriction")
public privileged aspect SuppressBreakpointMarkerUpdaterAspect {

  pointcut updateMarker(BreakpointMarkerUpdater markerUpdater, IMarker marker,
      IDocument document, Position position): 
    args(marker, document, position) 
    && execution(boolean BreakpointMarkerUpdater.updateMarker(IMarker, IDocument, Position))
    && target(markerUpdater);

  boolean around(BreakpointMarkerUpdater markerUpdater, IMarker marker,
      IDocument document, Position position): 
    updateMarker(markerUpdater, marker, document, position) {
    IFile resource = ((IFile) marker.getResource());
    if (resource != null && resource.getFileExtension().equals("scala"))
      return true;
    else
      return proceed(markerUpdater, marker, document, position);
  }

}
