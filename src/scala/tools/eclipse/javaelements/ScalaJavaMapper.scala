/*
 * Copyright 2005-2008 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse.javaelements

import org.eclipse.jdt.internal.compiler.classfmt.ClassFileConstants

trait ScalaJavaMapper { self : ScalaCompilationUnit =>
  import proj.compiler._
  
  def mapModifiers(mods : Modifiers) : Int = {
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
        if(tt.symbol == null || tt.symbol == NoSymbol || tt.symbol.isRefinementClass || tt.symbol.owner.isRefinementClass)
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
