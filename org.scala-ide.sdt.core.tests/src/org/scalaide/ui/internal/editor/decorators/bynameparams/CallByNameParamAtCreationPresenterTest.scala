package org.scalaide.ui.internal.editor.decorators.bynameparams

import scala.annotation.migration
import org.eclipse.jface.text.Region
import org.junit.Assert.assertEquals
import org.junit.Assert._
import org.junit.Test
import org.scalaide.CompilerSupportTests
import org.scalaide.core.semantichighlighting.classifier.RegionParser
import org.scalaide.core.semantichighlighting.classifier.RegionParser.EmbeddedSubstr
import CallByNameParamAtCreationPresenterTest.mkScalaCompilationUnit
import org.scalaide.ui.internal.editor.decorators.bynameparams.CallByNameParamAtCreationPresenter.Cfg

object CallByNameParamAtCreationPresenterTest extends CompilerSupportTests

class CallByNameParamAtCreationPresenterTest {
  import CallByNameParamAtCreationPresenterTest._

  @Test
  def testWithStringArgument() {
    testWithSingleLineCfg("""
      object O {
        def method(s: => String) = Unit
        method("Hanelore Hostasch")
      }""",
      "\"Hanelore Hostasch\"")
  }

  @Test
  def testWithValArgument() {
    testWithSingleLineCfg("""
      object O {
        val wert = 43
        def method(i: => Int) = Unit
        method(wert)
      }""",
      EmbeddedSubstr("wert", "(", ")"))
  }

  @Test
  def testWithMultipleMethods() {
    testWithSingleLineCfg("""
      object O {
        val wert = 43
        def method1(i: => Int) = Unit
        def method2(i: Int) = Unit
        method1(wert)
        method2(wert)
      }""",
      EmbeddedSubstr("wert", "1(", ")"))
  }

  @Test
  def testWithMathExpression() {
    testWithSingleLineCfg("""
      object O {
        def method(i: => Int) = Unit
        method(1 + 2 + 3 + 4)
      }""",
      "1 + 2 + 3 + 4")
  }

  @Test
  def testWithStringExpression() {
    testWithSingleLineCfg("""
      object O {
        def method(s: => String) = Unit
        method("" + "asdf".substring(1))
      }""",
      """"" + "asdf".substring(1)""")
  }

  @Test
  def testWithRecursion() {
    testWithSingleLineCfg("""
      object O {
        def method(s: => String) = s
        method(method(method("recursive")))
      }""",
      "\"recursive\"", """method("recursive")""", """method(method("recursive"))""")
  }

  @Test
  def testWithMultipleArgs() {
    testWithSingleLineCfg("""
      object O {
        def method(s1: => String, s2: String) = s1 + s2
        method("hallo", "welt")
      }""",
      "\"hallo\"")
  }

  @Test
  def testWithMultipleArgLists() {
    testWithSingleLineCfg("""
      object O {
        def method(s1: => String)(s2: String)(i1: Int, i2: => Int, i3: Int) = Unit
        method("hallo")("welt")(1, 2, 3)
      }""",
      "\"hallo\"", EmbeddedSubstr("2", " ", ","))
  }

  @Test
  def testWithCompilationErrorAlreadyDefined() {
    testWithSingleLineCfg("""
      object O {
        def alreadyDefined(s: => String) = Unit
        def alreadyDefined(s: => String) = Unit
        alreadyDefined("")
      }""")
  }

  @Test
  def testWithCompilationErrorInArg() {
    testWithSingleLineCfg("""
      object O {
        def method(s: => String) = Unit
        alreadyDefined("" "ups")
      }""")
  }

  @Test
  def testWithMuliLineCfgWithMultipleLines() {
    testWithMultiLineCfg("""
      object O {
        def method(s: => String) = Unit
        method("a" +
          "b + c" +
          "d")
      }""", """"a" +
          "b + c" +
          "d"""")
  }

  @Test
  def testWithSingleLineCfgWithMultipleLines() {
    testWithSingleLineCfg("""
      object O {
        def method(s: => String) = Unit
        method("a" +
          "b + c" +
          "d")
      }""", """"a" +""")
  }

  private def testWith(source: String, cfg: Cfg, paramCreations: EmbeddedSubstr*) {
    val cu = mkScalaCompilationUnit(source)
    cu.withSourceFile { (sourceFile, compiler) =>
      val res = CallByNameParamAtCreationPresenter.findByNameParamCreations(compiler, cu, sourceFile, cfg)
      val regions = res.values.map(pos => new Region(pos.offset, pos.length)).toSet
      val expectedRegions = RegionParser.substrRegions(source, paramCreations: _*).keySet
      assertEquals(expectedRegions, regions)

      assertEquals(Set(), res.keys.filterNot(_.isInstanceOf[CallByNameParamAtCreationAnnotation]))
      val annotationMsgs = res.keySet.map(_.getText)
      for (substr <- paramCreations) {
        assertTrue(annotationMsgs.toString(), annotationMsgs.exists(_.contains(substr.str)))
      }
    }
  }

  private def testWithSingleLineCfg(source: String, paramCreations: EmbeddedSubstr*) {
    testWith(source, Cfg(firstLineOnly = true), paramCreations: _*)
  }

  private def testWithMultiLineCfg(source: String, paramCreations: EmbeddedSubstr*) {
    testWith(source, Cfg(firstLineOnly = false), paramCreations: _*)
  }
}
