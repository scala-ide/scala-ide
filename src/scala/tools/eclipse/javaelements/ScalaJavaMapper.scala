/*
 * Copyright 2005-2008 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse.javaelements

import org.eclipse.jdt.internal.compiler.classfmt.ClassFileConstants

trait ScalaJavaMapper {
  val plugin : ScalaPlugin
  val proj : plugin.Project
  val compiler : proj.compiler0.type
  
  import compiler.{ Ident, Tree, TypeTree }
  
  def mapModifiers(mods : compiler.Modifiers) : Int = {
    var jdtMods = 0
    if(mods.isPrivate)
      jdtMods = jdtMods | ClassFileConstants.AccPrivate
    else if(mods.isProtected)
      jdtMods = jdtMods | ClassFileConstants.AccProtected
    else
      jdtMods = jdtMods | ClassFileConstants.AccPublic
    
    if(mods.isFinal)
      jdtMods = jdtMods | ClassFileConstants.AccFinal
    
    if(mods.isTrait)
      jdtMods = jdtMods | ClassFileConstants.AccInterface
    
    jdtMods
  }
        
  def mapType(t : Tree) : String = {
    (t match {
      case tt : TypeTree => {
        if(tt.symbol == null || tt.symbol.name.toString == compiler.nme.REFINE_CLASS_NAME.toString)
          "scala.AnyRef"
        else
          tt.symbol.fullNameString
      }
      case Ident(name) => name.toString
      case _ => "scala.AnyRef"
    }) match {
      case "scala.AnyRef" => "java.lang.Object"
      case "scala.Unit" => "void"
      case "scala.Boolean" => "boolean"
      case "scala.Byte" => "byte"
      case "scala.Short" => "short"
      case "scala.Int" => "int"
      case "scala.Long" => "long"
      case "scala.Float" => "float"
      case "scala.Double" => "double"
      case "<NoSymbol>" => "void"
      case n => n
    }
  }
}
