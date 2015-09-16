package scala.tools.eclipse.contribution.weaving.jdt.jdi;

import java.util.concurrent.Callable;

/**
 * Used to execute sync operation(s) on JDI object(s).
 */
public final class JdiInvocationSynchronizer {
  private final static JdiInvocationSynchronizer INSTANCE = new JdiInvocationSynchronizer();
  private volatile boolean locked = false;

  private JdiInvocationSynchronizer() {
    // no need to instantiate outside
  }

  public static JdiInvocationSynchronizer instance() {
    return INSTANCE;
  }

  public synchronized <R> R runSynchronized(Callable<R> codeBlock) throws Exception {
    try {
      locked = true;
      return codeBlock.call();
    } finally {
      locked = false;
    }
  }

  public boolean isLocked() {
    return locked;
  }
}
