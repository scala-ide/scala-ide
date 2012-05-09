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

import java.net.ServerSocket
import java.net.Socket
import java.io.BufferedReader
import java.io.InputStreamReader
import scala.xml.Elem
import scala.xml.XML
import org.xml.sax.SAXException
import java.util.Observable
import scala.xml.NodeSeq

class ScalaTestListener extends Observable with Runnable {

  @volatile
  private var stopped: Boolean = false
  private var serverSocket: ServerSocket = null
  private var connection: Socket = null
  private var in: BufferedReader = null

  def getPort = serverSocket.getLocalPort
  
  def bindSocket() = {
    serverSocket = new ServerSocket(0)
  }
  
  def run() {
    stopped = false
    try {
      connection = serverSocket.accept()
      in = new BufferedReader(new InputStreamReader(connection.getInputStream))
      while (!connection.isClosed && (!stopped || in.ready)) {
        var eventRawXml = ""
        var endingXml = ""
        var eventXml: Elem = null
        while (eventXml == null && !connection.isClosed && (!stopped || in.ready)) {
          val line = in.readLine
          if (line != null) {
            if (eventRawXml == "" && line.length > 0)
              endingXml = line.substring(0, 1) + "/" + line.substring(1)
            eventRawXml += line
            if (line.trim == endingXml.trim) 
              eventXml = XML.loadString(eventRawXml)
            else if (!connection.isClosed && !in.ready)
              Thread.sleep(10)
          }
          else if (!in.ready)
            Thread.sleep(10)
        }
        if (eventXml != null) {
          eventXml.label match {
            case "TestStarting" => 
              send(
                TestStarting(
                  (eventXml \ "suiteName").text,
                  (eventXml \ "suiteId").text,
                  stringOpt(eventXml \ "suiteClassName"),
                  stringOpt(eventXml \ "decodedSuiteName"),
                  (eventXml \ "testName").text,
                  (eventXml \ "testText").text,
                  stringOpt(eventXml \ "decodedTestName"),
                  locationOpt(eventXml \ "location"),
                  stringOpt(eventXml \ "rerunner"),
                  (eventXml \ "threadName").text,
                  (eventXml \ "timeStamp").text.toLong)
              )
            case "TestSucceeded" => 
              send(
                TestSucceeded(
                  (eventXml \ "suiteName").text,
                  (eventXml \ "suiteId").text,
                  stringOpt(eventXml \ "suiteClassName"),
                  stringOpt(eventXml \ "decodedSuiteName"),
                  (eventXml \ "testName").text,
                  (eventXml \ "testText").text,
                  stringOpt(eventXml \ "decodedTestName"),
                  longOpt(eventXml \ "duration"),
                  locationOpt(eventXml \ "location"),
                  stringOpt(eventXml \ "rerunner"),
                  (eventXml \ "threadName").text,
                  (eventXml \ "timeStamp").text.toLong
                )
              )
            case "TestFailed" => 
              send(
                TestFailed(
                  (eventXml \ "message").text,
                  (eventXml \ "suiteName").text,
                  (eventXml \ "suiteId").text,
                  stringOpt(eventXml \ "suiteClassName"),
                  stringOpt(eventXml \ "decodedSuiteName"),
                  (eventXml \ "testName").text,
                  (eventXml \ "testText").text,
                  stringOpt(eventXml \ "decodedTestName"),
                  stringOpt(eventXml \ "throwable" \ "message"),
                  intOpt(eventXml \ "throwable" \ "depth"),
                  stackTracesOpt(eventXml \ "throwable" \ "stackTraces"),
                  longOpt(eventXml \ "duration"),
                  locationOpt(eventXml \ "location"),
                  stringOpt(eventXml \ "rerunner"),
                  (eventXml \ "threadName").text,
                  (eventXml \ "timeStamp").text.toLong
                )
              )
            case "TestIgnored" => 
              send(
                TestIgnored(
                  (eventXml \ "suiteName").text,
                  (eventXml \ "suiteId").text,
                  stringOpt(eventXml \ "suiteClassName"),
                  stringOpt(eventXml \ "decodedSuiteName"),
                  (eventXml \ "testName").text,
                  (eventXml \ "testText").text,
                  stringOpt(eventXml \ "decodedTestName"),
                  locationOpt(eventXml \ "location"),
                  (eventXml \ "threadName").text,
                  (eventXml \ "timeStamp").text.toLong
                )
              )
            case "TestPending" => 
              send(
                TestPending(
                  (eventXml \ "suiteName").text,
                  (eventXml \ "suiteId").text,
                  stringOpt(eventXml \ "suiteClassName"),
                  stringOpt(eventXml \ "decodedSuiteName"),
                  (eventXml \ "testName").text,
                  (eventXml \ "testText").text,
                  stringOpt(eventXml \ "decodedTestName"),
                  longOpt(eventXml \ "duration"),
                  locationOpt(eventXml \ "location"),
                  (eventXml \ "threadName").text,
                  (eventXml \ "timeStamp").text.toLong
                )    
              )
            case "TestCanceled" => 
              send(
                TestCanceled(
                  (eventXml \ "message").text,
                  (eventXml \ "suiteName").text,
                  (eventXml \ "suiteId").text,
                  stringOpt(eventXml \ "suiteClassName"),
                  stringOpt(eventXml \ "decodedSuiteName"),
                  (eventXml \ "testName").text,
                  (eventXml \ "testText").text,
                  stringOpt(eventXml \ "decodedTestName"),
                  stringOpt(eventXml \ "throwable" \ "message"),
                  intOpt(eventXml \ "throwable" \ "depth"),
                  stackTracesOpt(eventXml \ "throwable" \ "stackTraces"),
                  longOpt(eventXml \ "duration"),
                  locationOpt(eventXml \ "location"),
                  (eventXml \ "threadName").text,
                  (eventXml \ "timeStamp").text.toLong
                )
              )
            case "ScopeOpened" => 
              send(
                ScopeOpened(
                  (eventXml \ "message").text,
                  nameInfo(eventXml \ "nameInfo"),
                  booleanOpt(eventXml \ "aboutAPendingTest"),
                  booleanOpt(eventXml \ "aboutACanceledTest"),
                  locationOpt(eventXml \ "location"),
                  (eventXml \ "threadName").text,
                  (eventXml \ "timeStamp").text.toLong
                )
              )
            case "ScopeClosed" => 
              send(
                ScopeClosed (
                  (eventXml \ "message").text,
                  nameInfo(eventXml \ "nameInfo"),
                  booleanOpt(eventXml \ "aboutAPendingTest"),
                  booleanOpt(eventXml \ "aboutACanceledTest"),
                  locationOpt(eventXml \ "location"),
                  (eventXml \ "threadName").text,
                  (eventXml \ "timeStamp").text.toLong
                )    
              )
            case "SuiteStarting" => 
              println(eventRawXml)
              send(
                SuiteStarting (
                  (eventXml \ "suiteName").text,
                  (eventXml \ "suiteId").text,
                  stringOpt(eventXml \ "suiteClassName"),
                  stringOpt(eventXml \ "decodedSuiteName"),
                  locationOpt(eventXml \ "location"),
                  stringOpt(eventXml \ "rerunner"),
                  (eventXml \ "threadName").text,
                  (eventXml \ "timeStamp").text.toLong
                )    
              )
            case "SuiteCompleted" => 
              send(
                SuiteCompleted (
                  (eventXml \ "suiteName").text,
                  (eventXml \ "suiteId").text,
                  stringOpt(eventXml \ "suiteClassName"),
                  stringOpt(eventXml \ "decodedSuiteName"),
                  longOpt(eventXml \ "duration"),
                  locationOpt(eventXml \ "location"),
                  stringOpt(eventXml \ "rerunner"),
                  (eventXml \ "threadName").text,
                  (eventXml \ "timeStamp").text.toLong
                )    
              )
            case "SuiteAborted" => 
              send(
                SuiteAborted (
                  (eventXml \ "message").text,
                  (eventXml \ "suiteName").text,
                  (eventXml \ "suiteId").text,
                  stringOpt(eventXml \ "suiteClassName"),
                  stringOpt(eventXml \ "decodedSuiteName"),
                  stringOpt(eventXml \ "throwable" \ "message"),
                  intOpt(eventXml \ "throwable" \ "depth"),
                  stackTracesOpt(eventXml \ "throwable" \ "stackTraces"),
                  longOpt(eventXml \ "duration"),
                  locationOpt(eventXml \ "location"),
                  stringOpt(eventXml \ "rerunner"),
                  (eventXml \ "threadName").text,
                  (eventXml \ "timeStamp").text.toLong
                )    
              )
            case "RunStarting" => 
              send(
                RunStarting(
                  (eventXml \ "testCount").text.toInt,
                  (eventXml \ "threadName").text,
                  (eventXml \ "timeStamp").text.toLong
                )    
              )
            case "RunCompleted" => 
              send(
                RunCompleted(
                  longOpt(eventXml \ "duration"),
                  summaryOpt(eventXml \ "summary"),
                  (eventXml \ "threadName").text,
                  (eventXml \ "timeStamp").text.toLong
                )    
              )
              stop()
            case "RunStopped" => 
              send(
                RunStopped (
                  longOpt(eventXml \ "duration"),
                  summaryOpt(eventXml \ "summary"),
                  (eventXml \ "threadName").text,
                  (eventXml \ "timeStamp").text.toLong
                ) 
              )
              stop()
            case "RunAborted" => 
              send(
                RunAborted (
                  (eventXml \ "message").text,
                  stringOpt(eventXml \ "throwable" \ "message"),
                  intOpt(eventXml \ "throwable" \ "depth"),
                  stackTracesOpt(eventXml \ "throwable" \ "stackTraces"),
                  longOpt(eventXml \ "duration"),
                  summaryOpt(eventXml \ "summary"),
                  locationOpt(eventXml \ "location"),
                  (eventXml \ "threadName").text,
                  (eventXml \ "timeStamp").text.toLong
                )
              )
              stop()
            case "InfoProvided" => 
              send(
                InfoProvided (
                  (eventXml \ "message").text,
                  nameInfoOpt(eventXml \ "nameInfo"),
                  booleanOpt(eventXml \ "aboutAPendingTest"),
                  booleanOpt(eventXml \ "aboutACanceledTest"),
                  stringOpt(eventXml \ "throwable" \ "message"),
                  intOpt(eventXml \ "throwable" \ "depth"),
                  stackTracesOpt(eventXml \ "throwable" \ "stackTraces"),
                  locationOpt(eventXml \ "location"),
                  (eventXml \ "threadName").text,
                  (eventXml \ "timeStamp").text.toLong
                )
              )
            case "MarkupProvided" => 
              send(
                MarkupProvided (
                  (eventXml \ "text").text,
                  nameInfoOpt(eventXml \ "nameInfo"),
                  booleanOpt(eventXml \ "aboutAPendingTest"),
                  booleanOpt(eventXml \ "aboutACanceledTest"),
                  locationOpt(eventXml \ "location"),
                  (eventXml \ "threadName").text,
                  (eventXml \ "timeStamp").text.toLong
                )
              )
            case _ => 
              // Ignore others
          }
        }
        if (!connection.isClosed && !in.ready)
          Thread.sleep(10)
      }
    }
    finally {
      in.close()
      connection.close()
    }
  }
  
  def send(event: Event){
    setChanged()
    notifyObservers(event)
  }
  
  def stop() {
    stopped = true
  }
  
  private def stringOpt(nodeSeq: NodeSeq) = {
    if (nodeSeq.text == "")
      None
    else
      Some(nodeSeq.text)
  }
  
  private def intOpt(nodeSeq: NodeSeq) = {
    if (nodeSeq.text == "")
      None
    else
      Some(nodeSeq.text.toInt)
  }
  
  private def longOpt(nodeSeq: NodeSeq) = {
    if (nodeSeq.text == "")
      None
    else
      Some(nodeSeq.text.toLong)
  }
  
  private def booleanOpt(nodeSeq: NodeSeq) = {
    if (nodeSeq.text == "")
      None
    else
      Some(nodeSeq.text.toBoolean)
  }
  
  private def locationOpt(nodeSeq: NodeSeq) = {
    val location = nodeSeq.head
    val nodeOpt = location.child.find(node => node.label == "TopOfClass" || node.label == "TopOfMethod" || node.label == "LineInFile" || node.label == "SeeStackDepthException")
    nodeOpt match {
      case Some(node) => 
        node.label match {
          case "TopOfClass" => 
            Some(TopOfClass((node \ "className").text))
          case "TopOfMethod" =>
            Some(TopOfMethod((node \ "className").text, (node \ "methodId").text))
          case "LineInFile" =>
            Some(LineInFile((node \ "lineNumber").text.toInt, (node \ "fileName").text))
          case "SeeStackDepthException" => 
            Some(SeeStackDepthException)
          case _ =>
            None
        }
      case None => 
        None
    }
  }
  
  private def stackTracesOpt(nodeSeq: NodeSeq): Option[Array[StackTraceElement]] = {
    if (nodeSeq.text == "")
      None
    else {
      val stackTraces = nodeSeq.head.child.filter(c => c.text.trim.length > 0)
      Some(stackTraces.map { st => 
        StackTraceElement((st \ "className").text, 
                          (st \ "methodName").text, 
                          (st \ "fileName").text, 
                          (st \ "lineNumber").text.toInt, 
                          (st \ "isNative").text.toBoolean, 
                          (st \ "toString").text)  
      }.toArray)
    }
  }
  
  private def nameInfoOpt(nodeSeq: NodeSeq) = {
    if (nodeSeq.text == "")
      None
    else {
      val testName = 
        if ((nodeSeq \ "testName").text == "")
          None
        else
          Some(TestNameInfo((nodeSeq \ "testName" \ "testName").text, stringOpt(nodeSeq \ "testName" \ "decodedTestName")))
      Some(
        NameInfo(
          (nodeSeq \ "suiteName").text, 
          (nodeSeq \ "suiteId").text, 
          stringOpt(nodeSeq \ "suiteClassName"), 
          stringOpt(nodeSeq \ "decodedSuiteName"),  
          testName)
      )
    }
  }
  
  private def nameInfo(nodeSeq: NodeSeq) = {
    nameInfoOpt(nodeSeq) match {
      case Some(nameInfo) =>
        nameInfo
      case None => 
        null
    }
  }
  
  private def summaryOpt(nodeSeq: NodeSeq) = {
    if (nodeSeq.text == "")
      None
    else {
      Some(
        Summary(
          (nodeSeq \ "testsSucceededCount").text.toInt, 
          (nodeSeq \ "testsFailedCount").text.toInt, 
          (nodeSeq \ "testsIgnoredCount").text.toInt, 
          (nodeSeq \ "testsPendingCount").text.toInt, 
          (nodeSeq \ "testsCanceledCount").text.toInt, 
          (nodeSeq \ "suitesCompletedCount").text.toInt, 
          (nodeSeq \ "suitesAbortedCount").text.toInt)    
      )
    }
  }
}