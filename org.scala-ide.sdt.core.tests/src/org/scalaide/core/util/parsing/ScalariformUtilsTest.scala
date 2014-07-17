package org.scalaide.core.util.parsing

import org.junit.Test
import org.junit.Assert._
import scalariform.parser._
import scalariform.lexer._
import org.scalaide.util.internal.scalariform._

class ScalariformUtilsTest {
  @Test def depthFirst() {
    val source = """
class A {
  class B
  class C {
    class D
  }
  class E
}
"""
    val firstClassName = ScalariformUtils.toListDepthFirst(parse(source))
      .collectFirst { case TmplDef(List(Token(Tokens.CLASS, _, _, _)), name, _, _, _, _, _, _) => name }
    assertEquals(Some("D"), firstClassName.map(_.text))
  }

  private def offsetFinder(haystack: String)(needle: String) = {
    val offset = haystack.indexOf(needle)
    assertNotSame(-1, offset)
    offset
  }

  @Test def getParametersWithMultiArgListAndNamed() {
    val source = """
class A {
  val a = new ClassA
  method(a, 1)("b", named = 'c')(a.toString, c)
}
"""
    val ast = parse(source)
    val offsetOf = offsetFinder(source) _
    val typeAtRange = (_: Int, _: Int) match {
      case (41, 42) => "ClassA" //a
      case (44, 45) => "Int" //1
      case (47, 50) => "String" //"b"
      case (60, 63) => "Char" //'c'
      case (65, 75) => "String" //a.toString
      case (77, 78) => "Any" //c
    }
    val parameterList = ScalariformUtils.getParameters(ast, offsetOf("method"), typeAtRange)
    val expected = List(List("a" -> "ClassA", "arg" -> "Int"), List("arg" -> "String", "named" -> "Char"), List("arg" -> "String", "c" -> "Any"))
    assertEquals(expected, parameterList)
  }

  @Test def enclosingClassForMethodInvocation() {
    val source = """
class A {
  method1()
  class B {
    method2()
  }
  object C {
    method3()
  }
}
"""
    val ast = parse(source)
    val offsetOf = offsetFinder(source) _

    def enclosing(methodName: String) = ScalariformUtils.enclosingClassForMethodInvocation(ast, offsetOf(methodName))

    assertEquals(Some("A"), enclosing("method1"))
    assertEquals(Some("B"), enclosing("method2"))
    assertEquals(Some("C"), enclosing("method3"))
  }

  @Test def equalsCallWithoutParameterList() {
    val source = """
class A {
  val obj = ""
  unknown1 = 0
  obj.unknown2 = 0
  unknown3() = 0
  obj.unknown4("a") = 0
}
"""

    val offsetOf = offsetFinder(source) _
    val ast = parse(source)

    assertTrue(ScalariformUtils.isEqualsCallWithoutParameterList(ast, offsetOf("unknown1")))
    assertTrue(ScalariformUtils.isEqualsCallWithoutParameterList(ast, offsetOf("unknown2")))
    assertFalse(ScalariformUtils.isEqualsCallWithoutParameterList(ast, offsetOf("unknown3")))
    assertFalse(ScalariformUtils.isEqualsCallWithoutParameterList(ast, offsetOf("unknown4")))
  }


  private def callingInfo(beginningOfCall: String, source: String) = {
    val offsetOf = offsetFinder(source) _
    val ast = parse(source)
    (ScalariformUtils.callingOffsetAndLength(ast, offsetOf(beginningOfCall)).get, offsetOf)
  }

  @Test def simpleCallOnSelf() {
    val source = """
class A {
  val list = List(1)
  list.map(method1)
}
"""
    val (callInfo, offsetOf) = callingInfo("method1", source)
    assertEquals(MethodCallInfo(offsetOf("map"), "map".length, ArgPosition(0, 0, None)), callInfo)
  }

  @Test def namedCallInfoOnSelf() {
    val source = """
class A {
  val list = List(1)
  list.map(f = method1)
}
"""
    val (callInfo, offsetOf) = callingInfo("method1", source)
    assertEquals(MethodCallInfo(offsetOf("map"), "map".length, ArgPosition(0, 0, Some("f"))), callInfo)
  }

  @Test def multipleArgumentListsCallingSelf() {
    val source = """
class A {
  higherOrderFunction(0, 0, 0)(0)(0)(func = method1, e=0, f=0)
  def higherOrderFunction(a: Int, b: Int)(c: Int)(d: Int)(e: Int, f: Int, func: Double => String) = ???
}
"""
    val (callInfo, offsetOf) = callingInfo("method1", source)
    assertEquals(MethodCallInfo(offsetOf("higherOrderFunction"), "higherOrderFunction".length, ArgPosition(3, 0, Some("func"))), callInfo)
  }

  @Test def callingOnOther() {
    val source = """
class A {
  val list = List(1)
  list.map(other.method1)
}
"""
    val (callInfo, offsetOf) = callingInfo("other", source)
    assertEquals(MethodCallInfo(offsetOf("map"), "map".length, ArgPosition(0, 0, None)), callInfo)
  }

  @Test def namedCallingOnOther() {
    val source = """
class A {
  val list = List(1)
  list.map(f = other.method1)
}
"""
    val (callInfo, offsetOf) = callingInfo("other", source)
    assertEquals(MethodCallInfo(offsetOf("map"), "map".length, ArgPosition(0, 0, Some("f"))), callInfo)
  }

  private def parse(source: String): AstNode = ScalariformParser.safeParse(source).get._1
}