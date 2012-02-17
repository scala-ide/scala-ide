package scala.tools.eclipse.util

object Trim {
  def apply(v: String): Option[String] = {
    val value = if(v == null) null else v.trim
    if(value == null || value.isEmpty) None
    else Some(value)
  }
  
  def apply(v: Option[String]): Option[String] = v.flatMap(apply)
}