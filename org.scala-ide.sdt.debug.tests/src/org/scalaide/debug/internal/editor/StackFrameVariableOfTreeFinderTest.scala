package org.scalaide.debug.internal.editor

import scala.util.Try
import scala.reflect.internal.util.{Position, OffsetPosition}
import org.scalaide.debug.internal.ScalaDebugTestSession
import org.scalaide.debug.internal.ScalaDebugRunningTest
import org.scalaide.debug.internal.ScalaDebugger
import org.scalaide.core.testsetup.TestProjectSetup
import org.scalaide.core.testsetup.SDTTestUtils
import org.scalaide.core.internal.jdt.model.ScalaCompilationUnit
import org.junit.Test
import org.junit.Before
import org.junit.Assert._
import org.junit.After
import org.eclipse.core.runtime.NullProgressMonitor
import org.eclipse.core.resources.IncrementalProjectBuilder

object StackFrameVariableOfTreeFinderTest
extends TestProjectSetup("sfValFinding", bundleName = "org.scala-ide.sdt.debug.tests")
with ScalaDebugRunningTest {
  val ScalaClassName = "valfinding.ScalaClass"
  val OuterClassName = "valfinding.OuterClass"
  val BaseClassName = "valfinding.BaseClass"
  val DerivedClassName = "valfinding.DerivedClass"
  val ExtenderClassName = "valfinding.ExplicitExtenderOfTheTrait"
  val TheTraitName = "valfinding.TheTrait"
  val EnclosingTraitName = "valfinding.EnclosingTrait"
  val ObjectName = "valfinding.Objectt"
  val ClosureTestClassName = "valfinding.ClosureTest"

  val ScalaClassFile = "ScalaClass.scala"
  val NestedClassesFile = "NestedClasses.scala"
  val InheritanceFile = "Inheritance.scala"
  val EnclosingTraitFile = "EnclosingTrait.scala"
  val ObjectFieldsFile = "ObjectFields.scala"
  val ClosuresFile= "Closures.scala"
}

class StackFrameVariableOfTreeFinderTest {
  import StackFrameVariableOfTreeFinderTest._

  var testsAreInitialized = false

  @Before
  def initTests() {
    if(! testsAreInitialized) {
      project.underlying.build(IncrementalProjectBuilder.CLEAN_BUILD, new NullProgressMonitor)
      project.underlying.build(IncrementalProjectBuilder.INCREMENTAL_BUILD, new NullProgressMonitor)
      testsAreInitialized = true
    }
  }

  def assertFoundValue(atMarker: String, when: String = null, is: Option[String])
  (implicit cu: ScalaCompilationUnit) {
    cu.withSourceFile {(src, compiler) =>
      import compiler.{Try => _, _}

      def treeAtMarker(markerName: String): Tree = {
        val positions = SDTTestUtils.positionsOf(src.content, s" /*{$markerName}*/")
        assertEquals(s"Couldn't find exactly one occurence of marker $markerName in ${src.path}.", 1, positions.size)
        val markerPos = new OffsetPosition(src, positions.head - 1)

        val resp = new Response[Tree]
        askTypeAt(markerPos, resp)
        resp.get.left getOrElse (throw new RuntimeException(s"Failed to get the Tree at marker $markerName."))
      }

      val whenClause = if(when!=null) s"($when) " else ""
      val foundVariable = StackFrameVariableOfTreeFinder.find(src, compiler, ScalaDebugger.currentStackFrame)(treeAtMarker(atMarker))
      if(is.isDefined)
        foundVariable map {v =>
          val valueStr = Try{v.getValue.getValueString}.getOrElse {
            throw new AssertionError(s"Failed to '.getValue()' on the found variable at marker '$atMarker'.")}
          val expected = is.get
          assertTrue(
              s"Stack-frame value found at marker '$atMarker' $whenClause was expected to be '$expected'," +
              s" but is actually $valueStr.",
              valueStr.matches(s""""$expected"""" + """ \(id=\d+\)"""))
        } getOrElse fail(s"Failed to find stack-frame value at marker '$atMarker'.")
      else if(foundVariable.isDefined)
        fail(s"Expected to find no value at marker '$atMarker' $whenClause, " +
             s"however the value ${foundVariable.get.getValue.getValueString} was found.")
    }
  }

  def withDebugSession(test: ScalaDebugTestSession => Unit) {
    val session = ScalaDebugTestSession(file("ValFindingDemo.launch"))
    test(session)
    session.terminate
  }

  def scalaCu(filePath: String) =
    compilationUnit(filePath).asInstanceOf[ScalaCompilationUnit]

  @Test
  def testClassFields() {
    withDebugSession {session =>
      implicit val cu = scalaCu(ScalaClassFile)

      session.runToLine(ScalaClassName, 13)

      assertFoundValue(atMarker="class param & field decl",  is=Some("fieldClassParam"))
      assertFoundValue(atMarker="class param & field usage", is=Some("fieldClassParam"))
      assertFoundValue(atMarker="non-field class param only used in ctor (decl)",  is=Some("nonFieldClassParam"))
      assertFoundValue(atMarker="non-field class param only used in ctor (usage)", is=Some("nonFieldClassParam"))
      assertFoundValue(atMarker="class field decl",  is=Some("classField"))
      assertFoundValue(atMarker="class field usage", is=Some("classField"))
      assertFoundValue(atMarker="field with same name of a field", is=None)
      assertFoundValue(atMarker="similarly named field decl of a class we are not in",  is=None)
      assertFoundValue(atMarker="similarly named field usage of a class we are not in", is=None)
    }
  }

  @Test
  def testMethodParamsAndLocals() {
    withDebugSession {session =>
      implicit val cu = scalaCu(ScalaClassFile)

      session.runToLine(ScalaClassName, 17)

      assertFoundValue(atMarker="method param decl",  is=Some("func param"))
      assertFoundValue(atMarker="method param usage", is=Some("func param"))
      assertFoundValue(atMarker="similarly named param of a method we are not in", is=None)
      assertFoundValue(atMarker="method-local variable", "when the variable is not yet assigned", is=None)

      session.stepOver

      assertFoundValue(atMarker="method-local variable", "after the variable is assigned", is=Some("local val"))
      assertFoundValue(atMarker="similarly named local var of a method we are not in", is=None)
    }
  }

  @Test
  def localVarAccessFromNestedMethods() {
    withDebugSession {session =>
      implicit val cu = scalaCu(ScalaClassFile)

      session.runToLine(ScalaClassName, 27)

      assertFoundValue(atMarker="nested method param", is=Some("nesteds parameter"))
      assertFoundValue(atMarker="nested method local", is=Some("nested2Local"))
      assertFoundValue(atMarker="enclosing nested method local", is=Some("nested1Local"))
      assertFoundValue(atMarker="root enclosing method local", is=Some("local val"))
    }
  }

  @Test
  def testFieldAccessInNestedClasses() {
    withDebugSession {session =>
      implicit val cu = scalaCu(NestedClassesFile)

      session.runToLine(OuterClassName, 10)

      assertFoundValue(atMarker="inner class field decl", is=Some("Inner's Field"))

      session.runToLine(OuterClassName, 16)

      assertFoundValue(atMarker="method-local var shadowing field", is=Some("Local Shadower"))
      assertFoundValue(atMarker="shadowed field accessed with this", is=Some("Inner's Field"))
      assertFoundValue(atMarker="shadowed field accessed with this with class name", is=Some("Inner's Field"))
      assertFoundValue(atMarker="shadowed field accessed with this with enclosing class name", is=Some("Outer's Field"))
      assertFoundValue(atMarker="exclusive field of enclosing class", is=Some("outerExclusiveField"))
    }
  }

  @Test
  def testFieldAccessBaseAndDerivedClasses() {
    withDebugSession {session =>
      implicit val cu = scalaCu(InheritanceFile)

      session.runToLine(BaseClassName, 7)

      assertFoundValue(atMarker="base class field decl", is=Some("baseField"))
      assertFoundValue(atMarker="base class field usage", is=Some("baseField"))

      session.runToLine(DerivedClassName, 18)

      assertFoundValue(atMarker="base class field usage from derived class", is=Some("baseField"))
      assertFoundValue(atMarker="derived class field usage", is=Some("derived field"))
    }
  }

  @Test
  def testTraitFieldAccess() {
    withDebugSession {session =>
      implicit val cu = scalaCu(InheritanceFile)

      session.runToLine(ExtenderClassName, 34)

      assertFoundValue(atMarker="trait field access from ctor of extender", is=Some("traitFieldd"))

      session.runToLine(ExtenderClassName, 37)

      assertFoundValue(atMarker="trait field access from method of extender", is=Some("traitFieldd"))

      session.runToLine(TheTraitName, 28)

      assertFoundValue(atMarker="trait method param", is=Some("traitFuncParam"))
      assertFoundValue(atMarker="trait field decl", is=Some("traitFieldd"))
      assertFoundValue(atMarker="trait field usage from trait", is=Some("traitFieldd"))
      assertFoundValue(atMarker="private trait field usage from trait", is=Some("privateTraitField"))
    }
  }

  @Test
  def testAccessToFieldsOfEnclosingTraitAndTheEnclosed() {
    withDebugSession {session =>
      implicit val cu = scalaCu(EnclosingTraitFile)

      session.runToLine(EnclosingTraitName, 10)

      assertFoundValue(atMarker="field decl of class nested in trait", is=Some("nField"))
      assertFoundValue(atMarker="field usage of class nested in trait", is=Some("nField"))
      assertFoundValue(atMarker="field of enclosing trait usage", is=Some("tField"))
    }
  }

  @Test
  def testAccessToObjectFields() {
    withDebugSession {session =>
      implicit val cu = scalaCu(ObjectFieldsFile)

      session.runToLine(ObjectName, 7)

      assertFoundValue(atMarker="object field decl",  is=Some("obj field"))
      assertFoundValue(atMarker="object field usage", is=Some("obj field"))
    }
  }

  @Test
  def testClosureSupport() {
    withDebugSession {session =>
      implicit val cu = scalaCu(ClosuresFile)

      session.runToLine(ClosureTestClassName, 11)

      assertFoundValue(atMarker="closure param decl", is=Some("clParam1"))
      assertFoundValue(atMarker="closure param usage", is=Some("clParam1"))
      assertFoundValue(atMarker="captured field of enclosing class", is=Some("captured field"))
      assertFoundValue(atMarker="captured local variable of enclosing method", is=Some("Local val captured"))
      assertFoundValue(atMarker="local of another method named similarly to a local of closure", is=None)

      session.runToLine(ClosureTestClassName, 15)

      assertFoundValue(
          atMarker="local var of closure shadowing local var of enclosing method",
          "before the variable is assigned to", is=None)

      session.stepOver

      assertFoundValue(
          atMarker="local var of closure shadowing local var of enclosing method",
          "after the variable is assigned to", is=Some("shadowed in closure"))
    }
  }
}
