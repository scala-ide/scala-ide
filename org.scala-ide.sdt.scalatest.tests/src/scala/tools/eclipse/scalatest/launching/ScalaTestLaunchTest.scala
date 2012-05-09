/*
 * SCALA LICENSE
 *
 * Copyright (C) 2011-2012 Artima, Inc. All rights reserved.
 *
 * This software was developed by Artima, Inc.
 *
 * Permission to use, copy, modify, and distribute this software in source
 * or binary form for any purpose with or without fee is hereby granted,
 * provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the EPFL nor the names of its contributors
 *    may be used to endorse or promote products derived from this
 *    software without specific prior written permission.
 *
 *
 * THIS SOFTWARE IS PROVIDED BY THE REGENTS AND CONTRIBUTORS ``AS IS'' AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE REGENTS OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
 * OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 */

package scala.tools.eclipse.scalatest.launching

import scala.tools.eclipse.testsetup.TestProjectSetup
import org.eclipse.core.resources.IncrementalProjectBuilder
import org.eclipse.core.runtime.NullProgressMonitor
import org.junit.Test
import org.eclipse.debug.core.DebugPlugin
import org.eclipse.debug.core.ILaunchManager
import org.junit.BeforeClass

class ScalaTestLaunchTest {

  import ScalaTestProject._
  
  private def launch(launchName: String) {
    val launchConfig = DebugPlugin.getDefault.getLaunchManager.getLaunchConfiguration(file(launchName + ".launch"))
    launchConfig.launch(ILaunchManager.RUN_MODE, null)
  }
  
  @Test
  def testLaunchPackage() {
    launch("com.test")
  }
  
  @Test
  def testLaunchFile() {
    launch("SingleSpec.scala")
    launch("MultiSpec.scala")
  }
  
  @Test
  def testLaunchSuite() {
    launch("SingleSpec")
    launch("StackSpec2")
    launch("TestingFreeSpec")
    launch("TestingFunSuite")
  }
  
  @Test
  def testLaunchTest() {
    launch("AStackshouldtastelikepeanutbutter")
    launch("AStackwhenemptyshouldcomplainonpop")
    launch("AStackwhenfull")
    launch("AStackwheneveritisemptycertainlyoughttocomplainonpeek")
    launch("AStackwheneveritisempty")
    launch("AStack")
    launch("com.test.TestingFunSuite-'test2'")
  }
  
  // These tests requires jar file for specs1 and scalachecks wrapper runner, 
  // which is not in any public maven repo yet.  We could enable them back 
  // when they are in public maven repo.
  /*@Test
  def testLaunchSpec1() {
    launch("ExampleSpec1.scala")
    launch("ExampleSpec1")
    launch("Mysystem")
    launch("Mysystemalsocanprovidesadvancedfeature1")
  }
  
  @Test
  def testLaunchScalaCheck() {
    launch("StringSpecification.scala")
    launch("StringSpecification")
    launch("com.test.StringSpecification-'substring1'")
  }*/
}