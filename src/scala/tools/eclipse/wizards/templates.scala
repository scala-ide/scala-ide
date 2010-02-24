/*
 * Copyright 2010 LAMP/EPFL
 * @author Tim Clendenen
 * 
 */
package scala.tools.eclipse.wizards

object templates extends QualifiedNameSupport {
	
  type LineDelimiter = String
	
  val DEFAULT_SUPER_TYPE = "scala.AnyRef"
	  
  def newLine(implicit ld: LineDelimiter): (String => String) = 
	(s: String) => s + ld
	  
  def newLines(implicit ld: LineDelimiter): (String => String) = 
    (s: String) => s + ld + ld
  
  def packageTemplate(opt: Option[String])(implicit ld: String): String = {
    val g = (s: String) => "package " + s
    val f = newLines compose g
	opt map f getOrElse ""
  }

  def commentTemplate(opt: Option[String])(implicit ld: String): String =
    opt map newLine getOrElse("")
  
  def importsTemplate(xs: List[String])(implicit ld: String): String = {
    val g = (s: String) => "import " + s
    val f = g compose newLine compose removeParameters
    xs map f reduceLeftOption(_ + _) map newLine getOrElse "" 
  }
    
  def typeTemplate = extendsTemplate compose explicitSuperTypes

  private val explicitSuperTypes = (xs: List[String]) => 
    xs match {
	  case List(DEFAULT_SUPER_TYPE, rest @ _*) => rest map removePackage toList
	  case List(all @ _*) => all map removePackage toList
    }
  
  private val extendsTemplate = (xs: List[String]) => 
    xs match {
	  case l: List[_] if(l.nonEmpty) => " extends " + l.mkString(" with ") 
	  case _ => ""
    }
}