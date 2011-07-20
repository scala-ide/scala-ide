package scala.tools.eclipse.contribution.weaving.jdt.debug;

import org.eclipse.ui.IEditorInput;
import org.eclipse.jdt.internal.ui.javaeditor.IClassFileEditorInput;
import org.eclipse.jdt.core.IClassFile;
import scala.tools.eclipse.contribution.weaving.jdt.IScalaClassFile;
import org.eclipse.jdt.internal.debug.ui.JDIModelPresentation;

/**
 * Fixes ticket 1000494. 
 * @see http://www.assembla.com/spaces/scala-ide/tickets/1000494-cannot-set-breakpoint-for-files-opened-via-the-stacktrace-printed-in-the-console
 */
@SuppressWarnings("restriction")
public aspect JDIModelPresentationAspect {
  //FIXME: Can we share code with ClassFileEditorIdAspect ?! How? (duplication!) 
  pointcut getEditorId(JDIModelPresentation jdiModelPresentation, IEditorInput input, Object inputObject): 
    args(input, inputObject) 
    && execution(String JDIModelPresentation.getEditorId(IEditorInput, Object))
    && target(jdiModelPresentation);

  String around(JDIModelPresentation jdiModelPresentation, IEditorInput input, Object inputObject): 
    getEditorId(jdiModelPresentation, input, inputObject) {
    if(input instanceof IClassFileEditorInput) {
      IClassFile classFile = ((IClassFileEditorInput) input).getClassFile();
      if(classFile instanceof IScalaClassFile) {
        return "scala.tools.eclipse.ScalaClassFileEditor";
      }
    }
    return proceed(jdiModelPresentation, input, inputObject);
  }

}
