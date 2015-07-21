package scala.tools.eclipse.contribution.weaving.jdt.jdi;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;

import scala.tools.eclipse.contribution.weaving.jdt.ScalaJDTWeavingPlugin;

/**
 * Used to execute sync operation(s) on JDI object(s).
 */
public final class JdiInvocationSynchronizer {
  private final static JdiInvocationSynchronizer INSTANCE = new JdiInvocationSynchronizer();
  public final static long DEFAULT_TIMEOUT_IN_SEC = 10;

  private JdiInvocationSynchronizer() {
    // no need to instantiate outside
  }

  private final ReentrantLock jdiLock = new ReentrantLock();

  public static JdiInvocationSynchronizer instance() {
    return INSTANCE;
  }

  public <R> R runSynchronized(Callable<R> codeBlock) {
    boolean isOwner = true;
    try {
      if (!jdiLock.tryLock(DEFAULT_TIMEOUT_IN_SEC, TimeUnit.SECONDS)) {
        isOwner = false;
        ScalaJDTWeavingPlugin.getInstance().getLog().log(new Status(IStatus.WARNING,
            ScalaJDTWeavingPlugin.ID, "Unable to get lock for JDI sync call"));
      }
      return codeBlock.call();
    } catch (Exception e) {
      throw new IllegalStateException("executed code block failed", e);
    } finally {
      if (isOwner)
        jdiLock.unlock();
    }
  }

  public boolean isLocked() {
    return jdiLock.isLocked();
  }
}
