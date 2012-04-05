package scala.tools.eclipse.ui

import scala.collection.mutable.ListBuffer
import scala.collection.mutable.Stack

final case class Summary(testsSucceededCount: Int, testsFailedCount: Int, testsIgnoredCount: Int, testsPendingCount: Int, testsCanceledCount: Int,
  suitesCompletedCount: Int, suitesAbortedCount: Int) {
  val testsCompletedCount = testsSucceededCount + testsFailedCount
}

case class TestNameInfo(testName: String, decodedTestName: Option[String])

final case class NameInfo(suiteName: String, suiteId: String, suiteClassName: Option[String], decodedSuiteName:Option[String],  testName: Option[TestNameInfo])

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
    }
  } 
  def children = childrenBuffer.toArray
  def hasChildren = children.length > 0
}

final case class TestModel(
  testName: String,
  testText: String,
  decodedTestName: Option[String],
  var duration: Option[Long],
  var errorMessage: Option[String], 
  var errorStackTrace: Option[String], 
  location: Option[Location],
  rerunner: Option[String],
  threadName: String,
  timeStamp: Long, 
  var status: TestStatus
) extends Node

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
  
}

final case class SuiteModel(
  suiteName: String,
  suiteId: String,
  suiteClassName: Option[String],
  decodedSuiteName: Option[String],
  location: Option[Location],
  rerunner: Option[String],
  var duration: Option[Long] = None,
  var errorMessage: Option[String], 
  var errorStackTrace: Option[String], 
  threadName: String,
  timeStamp: Long, 
  var status: SuiteStatus
) extends Node {
  
  private val scopeStack: Stack[ScopeModel] = Stack[ScopeModel]()
  private var flatTestsCache: ListBuffer[TestModel] = new ListBuffer[TestModel]()
  
  override def addChild(child: Node) {
    if (scopeStack.isEmpty)
      super.addChild(child)
    else 
      scopeStack.head.addChild(child)
    
    child match {
      case scope: ScopeModel => 
        scopeStack.push(scope)
      case _ => 
        if (child.isInstanceOf[TestModel])
          flatTestsCache += child.asInstanceOf[TestModel]
    }
  }
  
  def closeScope() {
    scopeStack.pop()
  }
  
  def updateTest(testName: String, status: TestStatus, duration: Option[Long], errorMessage: Option[String], errorStackTrace: Option[String]) = {
    val node = flatTestsCache.toArray.find(node => node.isInstanceOf[TestModel] && node.asInstanceOf[TestModel].testName == testName)
    node match {
      case Some(node) => 
        val test = node.asInstanceOf[TestModel]
        test.status = status
        test.duration = duration
        test.errorMessage = errorMessage
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
}

final case class RunModel(
  testCount: Int, 
  var duration: Option[Long] = None,
  var summary: Option[Summary] = None,
  var errorMessage: Option[String], 
  var errorStackTrace: Option[String], 
  threadName: String,
  timeStamp: Long, 
  var status: RunStatus
) extends Node 

final case class InfoModel(
  message: String,
  nameInfo: Option[NameInfo],
  aboutAPendingTest: Option[Boolean],
  aboutACanceledTest: Option[Boolean],
  errorMessage: Option[String], 
  errorStackTrace: Option[String], 
  location: Option[Location], 
  threadName: String,
  timeStamp: Long
) extends Node

final case class MarkupModel(
  text: String,
  nameInfo: Option[NameInfo],
  aboutAPendingTest: Option[Boolean],
  aboutACanceledTest: Option[Boolean],
  location: Option[Location],
  threadName: String,
  timeStamp: Long
) extends Node