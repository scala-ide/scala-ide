package scala.tools.eclipse.semantichighlighting.classifier

import SymbolTypes._
import org.junit._

class StringInterpolationTest extends AbstractSymbolClassifierTest {

  @Test
  def variables_in_interpolated_strings_are_marked_inInterpolatedString {
    checkSymbolInfoClassification("""
      object A {
        val templateVal = 1
        var templateVar = 2
        lazy val lazyTemplateVal = 3
        val str = s"Here is $templateVal and $templateVar and $lazyTemplateVal"
        def method(parameterName: Int) = {
          val aaasomelocalVal = 1
          var aaasomelocalVar = 2
          lazy val aaaasomelazyLocalVal = 3
          s"$parameterName, $aaasomelocalVal, $aaasomelocalVar, $aaaasomelazyLocalVal"
        }
        val assignment = templateVal
        val assignment2 = templateVar
        val assignment3 = lazyTemplateVal
      }""", """
      object A {
        val @   VAL   @ = 1
        val @   VAR   @ = 2
        lazy val @ LAZY_VAL    @ = 3
        val str = s"Here is $@ STR_VAL @ and $@ STR_VAR @ and $@ STR_LAZY_VAL@"
        def method(@ PARAM     @: Int) = {
          val @  LOCAL_VAL  @ = 1
          var @  LOCAL_VAR  @ = 2
          lazy val @  LAZY_LOCAL_VAL  @ = 3
          s"$@STR_PARAM  @, $@STR_LOCAL_VAL@, $@STR_LOCAL_VAR@, $@STR_LAZY_LOCAL_VAL@"
        }
        val assignment = @   VAL   @
        val assignment2 = @   VAR   @
        val assignment3 = @ LAZY_VAL    @
      }""",
      Map("VAL" -> SymbolInfo(TemplateVal, Nil, deprecated = false, inInterpolatedString = false),
          "VAR" -> SymbolInfo(TemplateVar, Nil, deprecated = false, inInterpolatedString = false),
          "LAZY_VAL" -> SymbolInfo(LazyTemplateVal, Nil, deprecated = false, inInterpolatedString = false),
          "STR_VAL" -> SymbolInfo(TemplateVal, Nil, deprecated = false, inInterpolatedString = true),
          "STR_VAR" -> SymbolInfo(TemplateVar, Nil, deprecated = false, inInterpolatedString = true),
          "STR_LAZY_VAL" -> SymbolInfo(LazyTemplateVal, Nil, deprecated = false, inInterpolatedString = true),
          "PARAM" -> SymbolInfo(Param, Nil, deprecated = false, inInterpolatedString = false),
          "LOCAL_VAL" -> SymbolInfo(LocalVal, Nil, deprecated = false, inInterpolatedString = false),
          "LOCAL_VAR" -> SymbolInfo(LocalVar, Nil, deprecated = false, inInterpolatedString = false),
          "LAZY_LOCAL_VAL" -> SymbolInfo(LazyLocalVal, Nil, deprecated = false, inInterpolatedString = false),
          "STR_PARAM" -> SymbolInfo(Param, Nil, deprecated = false, inInterpolatedString = true),
          "STR_LOCAL_VAL" -> SymbolInfo(LocalVal, Nil, deprecated = false, inInterpolatedString = true),
          "STR_LOCAL_VAR" -> SymbolInfo(LocalVar, Nil, deprecated = false, inInterpolatedString = true),
          "STR_LAZY_LOCAL_VAL" -> SymbolInfo(LazyLocalVal, Nil, deprecated = false, inInterpolatedString = true)
          ),
      '@')
  }
  
  @Test
  def variables_in_blocks_are_marked_but_methods_are_not {
    checkSymbolInfoClassification("""
      object A {
        val templateVal = "abc"
        val str = s"Here is ${templateVal.toUpperCase()}"
      }""", """
      object A {
        val templateVal = "abc"
        val str = s"Here is ${@ STR_VAL @.@  METHOD @()}"
      }""",
      Map("STR_VAL" -> SymbolInfo(TemplateVal, Nil, deprecated = false, inInterpolatedString = true),
          "METHOD" -> SymbolInfo(Method, Nil, deprecated = false, inInterpolatedString = false)),
      '@')
  }
}
