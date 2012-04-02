package scala.tools.eclipse.ui

sealed abstract class Event

final case class TestStarting (
  suiteName: String,
  suiteId: String,
  suiteClassName: Option[String],
  decodedSuiteName: Option[String],
  testName: String,
  testText: String,
  decodedTestName: Option[String],
  location: Option[Location],
  rerunner: Option[String],
  threadName: String,
  timeStamp: Long
) extends Event

final case class TestSucceeded (
  suiteName: String,
  suiteId: String,
  suiteClassName: Option[String],
  decodedSuiteName: Option[String], 
  testName: String,
  testText: String,
  decodedTestName: Option[String],
  duration: Option[Long],
  location: Option[Location],
  rerunner: Option[String],
  threadName: String,
  timeStamp: Long
) extends Event

final case class TestFailed (
  message: String,
  suiteName: String,
  suiteId: String,
  suiteClassName: Option[String],
  decodedSuiteName: Option[String],
  testName: String,
  testText: String,
  decodedTestName: Option[String],
  errorMessage: Option[String],
  errorStackTraces: Option[String],
  duration: Option[Long],
  location: Option[Location],
  rerunner: Option[String],
  threadName: String,
  timeStamp: Long
) extends Event

final case class TestIgnored (
  suiteName: String,
  suiteId: String,
  suiteClassName: Option[String],
  decodedSuiteName: Option[String],
  testName: String,
  testText: String,
  decodedTestName: Option[String],
  location: Option[Location],
  threadName: String,
  timeStamp: Long
) extends Event

final case class TestPending (
  suiteName: String,
  suiteId: String,
  suiteClassName: Option[String],
  decodedSuiteName: Option[String],
  testName: String,
  testText: String,
  decodedTestName: Option[String],
  duration: Option[Long],
  location: Option[Location],
  threadName: String,
  timeStamp: Long
) extends Event

final case class TestCanceled (
  message: String,
  suiteName: String,
  suiteId: String,
  suiteClassName: Option[String],
  decodedSuiteName: Option[String],
  testName: String,
  testText: String,
  decodedTestName: Option[String],
  errorMessage: Option[String],
  errorStackTraces: Option[String], 
  duration: Option[Long],
  location: Option[Location],
  threadName: String,
  timeStamp: Long
) extends Event

final case class SuiteStarting (
  suiteName: String,
  suiteId: String,
  suiteClassName: Option[String],
  decodedSuiteName: Option[String],
  location: Option[Location],
  rerunner: Option[String],
  threadName: String,
  timeStamp: Long
) extends Event

final case class SuiteCompleted (
  suiteName: String,
  suiteId: String,
  suiteClassName: Option[String],
  decodedSuiteName: Option[String],
  duration: Option[Long],
  location: Option[Location],
  rerunner: Option[String],
  threadName: String,
  timeStamp: Long
) extends Event

final case class SuiteAborted (
  message: String,
  suiteName: String,
  suiteId: String,
  suiteClassName: Option[String],
  decodedSuiteName: Option[String], 
  errorMessage: Option[String],
  errorStackTraces: Option[String],
  duration: Option[Long],
  location: Option[Location],
  rerunner: Option[String],
  threadName: String,
  timeStamp: Long
) extends Event

final case class RunStarting (
  testCount: Int,
  threadName: String,
  timeStamp: Long
) extends Event

final case class RunCompleted (
  duration: Option[Long],
  summary: Option[Summary],
  threadName: String,
  timeStamp: Long
) extends Event

final case class RunStopped (
  duration: Option[Long],
  summary: Option[Summary],
  threadName: String,
  timeStamp: Long
) extends Event

final case class RunAborted (
  message: String,
  errorMessage: Option[String],
  errorStackTraces: Option[String],
  duration: Option[Long],
  summary: Option[Summary],
  location: Option[Location],
  threadName: String,
  timeStamp: Long
) extends Event

final case class InfoProvided (
  message: String,
  nameInfo: Option[NameInfo],
  aboutAPendingTest: Option[Boolean],
  aboutACanceledTest: Option[Boolean],
  errorMessage: Option[String],
  errorStackTraces: Option[String],
  location: Option[Location],
  threadName: String,
  timeStamp: Long
) extends Event

final case class MarkupProvided (
  text: String,
  nameInfo: Option[NameInfo],
  aboutAPendingTest: Option[Boolean],
  aboutACanceledTest: Option[Boolean],
  location: Option[Location],
  threadName: String,
  timeStamp: Long
) extends Event

final case class ScopeOpened (
  message: String,
  nameInfo: NameInfo,
  aboutAPendingTest: Option[Boolean],
  aboutACanceledTest: Option[Boolean],
  location: Option[Location],
  threadName: String,
  timeStamp: Long
) extends Event

final case class ScopeClosed (
  message: String,
  nameInfo: NameInfo,
  aboutAPendingTest: Option[Boolean],
  aboutACanceledTest: Option[Boolean],
  location: Option[Location],
  threadName: String,
  timeStamp: Long
) extends Event