/*
 * Copyright 2005-2009 LAMP/EPFL
 * @author Sean McDirmid
 */
// $Id$

package scala.tools.eclipse.properties;
import scala.xml._
class EditorPreferences extends lampion.eclipse.EditorPreferences {
  val plugin = ScalaUIPlugin.plugin
  val lt = "<"

  def sampleCode : NodeSeq =
<code><keyword>package</keyword> <package>sample</package>.<package>apackage</package>
<keyword>import</keyword> <object>scala</object>._
<comment>/** This is a sample of how Scala code will be highlighted */</comment>
<keyword>class</keyword> <class>SomeClass</class>[<type>TYPE_PARAM</type>] {{
  <keyword>val</keyword> <val>aValue</val> : <type>String</type> = <string>"a string"</string>
  <keyword>type</keyword> <type>aTypeMember</type> {lt}: <class>AnyRef</class>
  <keyword>def</keyword> <def>aDef</def>(<arg>anArg</arg> : <class>AnyVal</class>) = <symbol>'aSymbol</symbol>
  <keyword>var</keyword> <var>aVar</var> = <char>'c'</char>
}}
<keyword>class</keyword> <trait>SomeTrait</trait>
<keyword>class</keyword> <object>SomeObject</object>
</code>;
  
}
