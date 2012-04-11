package scala.tools.eclipse.debug.model

import org.junit.Test
import org.junit.Assert._
import org.mockito.Mockito._
import org.eclipse.jdt.internal.debug.core.model.JDIDebugTarget
import com.sun.jdi.VirtualMachine
import com.sun.jdi.ThreadReference
import java.util.ArrayList
import org.eclipse.debug.core.DebugPlugin
import org.junit.Before
import com.sun.jdi.event.ThreadStartEvent

class ScalaDebugTargetTest {
  
  @Before
  def initializeDebugPlugin() {
    if (DebugPlugin.getDefault == null) {
      new DebugPlugin
    }
  }
  
  @Test
  def threadNotTwiceInList() {
    val THREAD_NAME= "thread name"
    
    val debugTarget= createDebugTarget
    
    val event = mock(classOf[ThreadStartEvent])
    val thread = mock(classOf[ThreadReference])
    when(event.thread).thenReturn(thread)
    when(thread.name).thenReturn(THREAD_NAME)
    
    debugTarget.handleEvent(event, null, false, null)
    
    val threads1= debugTarget.getThreads
    assertEquals("Wrong number of threads", 1, threads1.length)
    assertEquals("Wrong thread name", THREAD_NAME, threads1(0).getName)
    
    // a second start event should not result in a duplicate entry
    debugTarget.handleEvent(event, null, false, null)
    
    val threads2= debugTarget.getThreads
    assertEquals("Wrong number of threads", 1, threads2.length)
    assertEquals("Wrong thread name", THREAD_NAME, threads2(0).getName)
  }
  
  def createDebugTarget(): ScalaDebugTarget = {
    val javaDebugTarget= mock(classOf[JDIDebugTarget])
    val vm= mock(classOf[VirtualMachine])
    when(javaDebugTarget.getVM).thenReturn(vm)
    when(vm.allThreads).thenReturn(new ArrayList[ThreadReference]())
    new ScalaDebugTarget(javaDebugTarget, null, null)
  }

}