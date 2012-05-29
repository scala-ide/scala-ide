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
  errorDepth: Option[Int], 
  errorStackTraces: Option[Array[StackTraceElement]],
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
  errorDepth: Option[Int], 
  errorStackTraces: Option[Array[StackTraceElement]], 
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
  errorDepth: Option[Int], 
  errorStackTraces: Option[Array[StackTraceElement]],
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
  errorDepth: Option[Int], 
  errorStackTraces: Option[Array[StackTraceElement]],
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
  errorDepth: Option[Int], 
  errorStackTraces: Option[Array[StackTraceElement]],
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