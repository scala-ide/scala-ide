package scala.tools.eclipse.contribution.weaving.jdt.jdi;

import java.io.PrintWriter;
import java.io.StringWriter;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;

import scala.tools.eclipse.contribution.weaving.jdt.ScalaJDTWeavingPlugin;

public aspect JdiEntryGuardianAspect {
  pointcut jdiInvocation():
    call(* com.sun.jdi.*.*(..));

  before(): jdiInvocation() {
    if (!JdiInvocationSynchronizer.instance().isLocked()) {
      StringWriter sw = new StringWriter();
      PrintWriter pw = new PrintWriter(sw);
      for (StackTraceElement traceElement: Thread.currentThread().getStackTrace()) {
        pw.println(traceElement.toString());
      }
      pw.flush();
      pw.close();
      ScalaJDTWeavingPlugin.getInstance().getLog().log(new Status(IStatus.WARNING, ScalaJDTWeavingPlugin.ID,
          sw.toString()));
    }
  }
}
