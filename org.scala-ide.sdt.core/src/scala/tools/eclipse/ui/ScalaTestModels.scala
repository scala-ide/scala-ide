package scala.tools.eclipse.ui

import scala.collection.mutable.ListBuffer

final case class Summary(testsSucceededCount: Int, testsFailedCount: Int, testsIgnoredCount: Int, testsPendingCount: Int, testsCanceledCount: Int,
  suitesCompletedCount: Int, suitesAbortedCount: Int) {
  val testsCompletedCount = testsSucceededCount + testsFailedCount
}

case class TestNameInfo(testName: String, decodedTestName: Option[String])

final case class NameInfo(suiteName: String, suiteID: String, suiteClassName: Option[String], decodedSuiteName:Option[String],  testName: Option[TestNameInfo])

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
  val STARTED, COMPLETED, ABORTED = Value
}

object RunStatus extends Enumeration {
  type RunStatus = Value
  val STARTED, COMPLETED, STOPPED, ABORTED = Value
}

import TestStatus._
import ScopeStatus._
import SuiteStatus._
import RunStatus._

case class Node

final case class Test(
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

final case class Scope(
  message: String,
  nameInfo: NameInfo,
  location: Option[Location],
  threadName: String,
  timeStamp: Long, 
  var status: ScopeStatus
) extends Node {
  private val childrenBuffer = new ListBuffer[Node]()
  def children = childrenBuffer.toList
}

final case class Suite(
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
  private val childrenBuffer = new ListBuffer[Node]()
  def children = childrenBuffer.toList
}

final case class Run(
  testCount: Int, 
  var duration: Option[Long] = None,
  var summary: Option[Summary] = None,
  var errorMessage: Option[String], 
  var errorStackTrace: Option[String], 
  threadName: String,
  timeStamp: Long, 
  var status: RunStatus
)

final case class Info(
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

final case class Markup(
  text: String,
  nameInfo: Option[NameInfo],
  aboutAPendingTest: Option[Boolean],
  aboutACanceledTest: Option[Boolean],
  location: Option[Location],
  threadName: String,
  timeStamp: Long
) extends Node