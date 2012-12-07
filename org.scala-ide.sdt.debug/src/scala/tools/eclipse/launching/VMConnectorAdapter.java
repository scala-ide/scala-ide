package scala.tools.eclipse.launching;

import java.util.Map;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.jdt.launching.*;

/**
 * Workaround for compatibility between Indigo and Juno.
 *
 * The `connect` method changed signature to take type arguments, and Scala
 * won't allow overriding
 *   connect(Map<String, String> args, ..)
 * with
 *   connect(args: Map[_, _], ..)
 *
 * But we can do this in Java, and keep one source code for the two
 * platforms.
 */
public abstract class VMConnectorAdapter implements IVMConnector {

  public void connect(Map arguments, IProgressMonitor monitor, ILaunch launch) throws CoreException {
    typedConnect((Map<String, String>) arguments, monitor, launch);
  }

  /** Same as `connect`, but with proper type paramters. */
  public abstract void typedConnect(Map<String, String> arguments, IProgressMonitor monitor, ILaunch launch);
}
