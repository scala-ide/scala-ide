package scala.tools.eclipse.quickfix

package object createmethod {
  type ParameterList = List[List[(String, String)]]
  type ReturnType = Option[String]
}