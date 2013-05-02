package scala.tools.eclipse.quickfix.createmethod

import scala.tools.eclipse.javaelements.ScalaCompilationUnit
import scala.tools.eclipse.logging.HasLogger
import scala.tools.nsc.symtab.Flags

object MembersInScope extends HasLogger {
  def getValVarAndZeroArgMethods(scu: ScalaCompilationUnit, offset: Int) = {
    val inScopeOpt = scu.withSourceFile { (srcFile, compiler) =>
      val membersInScope = new compiler.Response[List[compiler.Member]]
      val cpos = compiler.rangePos(srcFile, offset, offset, offset)
      compiler.askScopeCompletion(cpos, membersInScope)

      for (members <- membersInScope.get.left.toOption) yield {
        compiler.askOption { () =>
          val thingsInScope = members.collect {
            case compiler.ScopeMember(sym, tpe, true, _) if sym.isValue =>
              //TODO: I have a feeling this is the wrong way to do this :)
              lazy val typeInfo = {
                val info = sym.infoString(tpe)
                if (info.contains(": ")) info.drop(info.indexOf(": ") + 2) else info
              }
              
              sym.paramss match {
                case Nil => //plain value or zero arg method, eg "def method = 0"
                  Some(InScope(sym.decodedName, typeInfo))
                case List(Nil) => //method with a zero length param list, eg "def method() = 0"
                  Some(InScope(sym.decodedName, typeInfo))
                case _ => None
              }
            case _ => None
          }
          thingsInScope.flatten
        }
      }
    }(None)

    inScopeOpt.flatten.getOrElse(Nil)
  }
}

case class InScope(name: String, tpe: String)
