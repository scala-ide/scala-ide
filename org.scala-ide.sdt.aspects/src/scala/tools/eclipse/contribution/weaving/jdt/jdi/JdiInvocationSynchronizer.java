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

  public synchronized <R> R runSynchronized(Callable<R> codeBlock) {
    try {
      locked = true;
      return codeBlock.call();
    } catch (Exception e) {
      if (e instanceof RuntimeException) {
        throw (RuntimeException) e;
      } else {
        throw new IllegalStateException("executed code block failed", e);
      }
    } finally {
      locked = false;
    }
  }

  public boolean isLocked() {
    return locked;
  }
}
