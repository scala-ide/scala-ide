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

package scala.tools.eclipse.scalatest.ui

import scala.collection.mutable.ListBuffer
import scala.collection.mutable.Stack

final case class Summary(testsSucceededCount: Int, testsFailedCount: Int, testsIgnoredCount: Int, testsPendingCount: Int, testsCanceledCount: Int,
  suitesCompletedCount: Int, suitesAbortedCount: Int) {
  val testsCompletedCount = testsSucceededCount + testsFailedCount
}

case class TestNameInfo(testName: String, decodedTestName: Option[String])

final case class NameInfo(suiteName: String, suiteId: String, suiteClassName: Option[String], decodedSuiteName:Option[String],  testName: Option[TestNameInfo])

case class StackTraceElement(className: String, methodName: String, fileName: String, lineNumber: Int, isNative: Boolean, toStringValue: String) {
  override def toString = toStringValue
}

object TestStatus extends Enumeration {
  type TestStatus = Value
  val STARTED, SUCCEEDED, FAILED, IGNORED, PENDING, CANCELED = Value
}

object ScopeStatus extends Enumeration {
  type ScopeStatus = Value
  val OPENED, CLOSED = Value
}

object SuiteStatus extends Enumeration {
  type SuiteStatus = Value
  val STARTED, SUCCEED, FAILED, ABORTED = Value
}

object RunStatus extends Enumeration {
  type RunStatus = Value
  val STARTED, COMPLETED, STOPPED, ABORTED = Value
}

import TestStatus._
import ScopeStatus._
import SuiteStatus._
import RunStatus._

sealed abstract class Node {
  private var childrenBuffer = new ListBuffer[Node]()
  var parent: Node = null
  def addChild(child: Node) {
    synchronized {
      childrenBuffer += child
      child.parent = this
    }
  } 
  def children = childrenBuffer.toArray
  def hasChildren = children.length > 0
  def getStackTraces: Option[Array[StackTraceElement]]
  def getStackDepth: Option[Int]
}

final case class TestModel(
  suiteId: String, 
  testName: String,
  testText: String,
  decodedTestName: Option[String],
  var duration: Option[Long],
  var errorMessage: Option[String], 
  var errorDepth: Option[Int], 
  var errorStackTrace: Option[Array[StackTraceElement]], 
  var location: Option[Location],
  rerunner: Option[String],
  threadName: String,
  timeStamp: Long, 
  var status: TestStatus
) extends Node {
  def getStackTraces = errorStackTrace
  def getStackDepth = errorDepth
}

final case class ScopeModel(
  message: String,
  nameInfo: NameInfo,
  location: Option[Location],
  threadName: String,
  timeStamp: Long, 
  var status: ScopeStatus
) extends Node {
  
  def scopeSucceed: Boolean = {
    children.forall { child => 
      child match {
        case test: TestModel => 
          test.status != TestStatus.FAILED
        case scope: ScopeModel => 
          scope.scopeSucceed
        case _ =>
          true
      }
    }
  }
  
  def getStackTraces = None
  def getStackDepth = None
}

final case class SuiteModel(
  suiteName: String,
  suiteId: String,
  suiteClassName: Option[String],
  decodedSuiteName: Option[String],
  var location: Option[Location],
  rerunner: Option[String],
  var duration: Option[Long] = None,
  var errorMessage: Option[String], 
  var errorDepth: Option[Int], 
  var errorStackTrace: Option[Array[StackTraceElement]], 
  threadName: String,
  timeStamp: Long, 
  var status: SuiteStatus
) extends Node {
  
  private val scopeStack: Stack[Node] = Stack[Node]()
  private var flatTestsCache: ListBuffer[TestModel] = new ListBuffer[TestModel]()
  
  override def addChild(child: Node) {
    if (scopeStack.isEmpty)
      super.addChild(child)
    else 
      scopeStack.head.addChild(child)
    
    child match {
      case scope: ScopeModel => 
        scopeStack.push(scope)
      case test: TestModel =>
        scopeStack.push(test)
        flatTestsCache += test
      case _ => 
        // Do nothing for other type of child
    }
  }
  
  def closeScope() {
    scopeStack.pop()
  }
  
  def updateTest(testName: String, status: TestStatus, duration: Option[Long], location: Option[Location], errorMessage: Option[String], errorDepth: Option[Int], errorStackTrace: Option[Array[StackTraceElement]]) = {
    val node = flatTestsCache.toArray.find(node => node.isInstanceOf[TestModel] && node.asInstanceOf[TestModel].testName == testName)
    node match {
      case Some(node) => 
        val test = node.asInstanceOf[TestModel]
        test.status = status
        test.duration = duration
        test.location = location
        test.errorMessage = errorMessage
        test.errorDepth = errorDepth
        test.errorStackTrace = errorStackTrace
        test
      case None => 
        // Should not happen
        throw new IllegalStateException("Unable to find test name: " + testName + ", suiteId: " + suiteId)
    }
  }
  
  def suiteSucceeded = {
    flatTestsCache.toArray.forall(child => child.status != TestStatus.FAILED)
  }
  
  def getStackTraces = errorStackTrace
  def getStackDepth = errorDepth
}

final case class RunModel(
  testCount: Int, 
  var duration: Option[Long] = None,
  var summary: Option[Summary] = None,
  var errorMessage: Option[String], 
  var errorDepth: Option[Int],
  var errorStackTrace: Option[Array[StackTraceElement]], 
  threadName: String,
  timeStamp: Long, 
  var status: RunStatus
) extends Node {
  def getStackTraces = errorStackTrace
  def getStackDepth = errorDepth
}

final case class InfoModel(
  message: String,
  nameInfo: Option[NameInfo],
  aboutAPendingTest: Option[Boolean],
  aboutACanceledTest: Option[Boolean],
  errorMessage: Option[String], 
  errorDepth: Option[Int],
  errorStackTrace: Option[Array[StackTraceElement]], 
  location: Option[Location], 
  threadName: String,
  timeStamp: Long
) extends Node {
  def getStackTraces = errorStackTrace
  def getStackDepth = errorDepth
}