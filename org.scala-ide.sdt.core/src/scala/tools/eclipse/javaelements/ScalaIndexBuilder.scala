/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse.javaelements

import org.eclipse.core.resources.IFile
import org.eclipse.jdt.internal.compiler.classfmt.ClassFileConstants

import scala.tools.nsc.symtab.Flags

import scala.tools.eclipse.{ ScalaPlugin, ScalaPresentationCompiler,
	                           ScalaSourceIndexer, SettingConverterUtil }
import scala.tools.eclipse.util.ScalaPluginSettings

trait ScalaIndexBuilder { self : ScalaPresentationCompiler =>
  //BACK-2.8
  import scala.tools.nsc.symtab.Names
  private  def isConstructorName(name: Names#Name)  = name == nme.CONSTRUCTOR || name == nme.MIXIN_CONSTRUCTOR
  private implicit def append(v : Name) = new Object(){
    def append(suffix : String) : Names#Name = {
      import nme._
      //in scala 2.8 TermName and TypeName are private
      v.getClass.getSimpleName match {
        case x if x.indexOf("TypeName") > -1 => newTypeName(v + suffix)
        //case x if x.indexOf("TermName") > -1 => newTermName(v + suffix)
        case _ => newTermName(v + suffix)
      }
    }
  }
  //---end BACK-2.8
  
  object IndexBuilderTraverser {
  	lazy val store = ScalaPlugin.plugin.getPreferenceStore
  	lazy val infoName = 
  		SettingConverterUtil.convertNameToProperty(ScalaPluginSettings.YPlugininfo.name)
  	@inline def isInfo = store.getBoolean(infoName)
  }
  
  import IndexBuilderTraverser.isInfo
  
  class IndexBuilderTraverser(indexer : ScalaSourceIndexer) extends Traverser {
    var packageName = new StringBuilder
      
    def addPackage(p : PackageDef) = {
      if (isInfo) println("Package defn: "+p.name+" ["+this+"]")
      if (!packageName.isEmpty) packageName.append('.')
      packageName.append(p.name)  
    }
    
    def getSuperNames(supers : List[Tree]) = supers map ( _ match { 
        case Ident(id) => id.toChars
        case Select(_, name) => name.toChars
        case _ => "$$NoRef".toCharArray
      }) toArray
    
    def addClass(c : ClassDef) {
   	  if (isInfo) {      		
        println("Class defn: "+c.name+" ["+this+"]")
        println("Parents: "+c.impl.parents)
      }
        
      indexer.addClassDeclaration(
        mapModifiers(c.mods),
        packageName.toString.toCharArray,
        c.name.toChars,
        enclClassNames.reverse.toArray,
        Array.empty,
        getSuperNames(c.impl.parents),
        Array.empty,
        true
      )
    }
    
    def addModule(m : ModuleDef) {
      if (isInfo)
        println("Module defn: "+m.name+" ["+this+"]")
      
      indexer.addClassDeclaration(
        mapModifiers(m.mods),
        packageName.toString.toCharArray,
        m.name.append("$").toChars,
        enclClassNames.reverse.toArray,
        Array.empty,
        getSuperNames(m.impl.parents),
        Array.empty,
        true
      )
        
      indexer.addClassDeclaration(
        mapModifiers(m.mods),
        packageName.toString.toCharArray,
        m.name.toChars,
        Array.empty,
        Array.empty,
        Array.empty,
        Array.empty,
        true
      )
    }
    
    def addVal(v : ValDef) {
     if (isInfo)
        println("Val defn: >"+nme.getterName(v.name)+"< ["+this+"]")
        
      indexer.addMethodDeclaration(
        nme.getterName(v.name).toChars,
        Array.empty,
        mapType(v.tpt).toArray,
        Array.empty
      )
        
      if(v.mods.hasFlag(Flags.MUTABLE))
        indexer.addMethodDeclaration(
          nme.getterToSetter(nme.getterName(v.name)).toChars,
          Array.empty,
          mapType(v.tpt).toArray,
          Array.empty
        )
    }
    
    def addDef(d : DefDef) {
      if (isInfo)
        println("Def defn: "+d.name+" ["+this+"]")
      val name = if(isConstructorName(d.name)) enclClassNames.head else d.name.toChars
        
      val fps = for(vps <- d.vparamss; vp <- vps) yield vp
        
      val paramTypes = fps.map(v => mapType(v.tpt))
      indexer.addMethodDeclaration(
        name,
        paramTypes.map(_.toCharArray).toArray,
        mapType(d.tpt).toArray,
        Array.empty
      )
    }
    
    def addType(td : TypeDef) {
      // We don't care what to add, java doesn't see types anyway.
      indexer.addClassDeclaration(
        mapModifiers(td.mods),
        packageName.toString.toCharArray,
        td.name.toChars,
        Array.empty,
        Array.empty,
        Array.empty,
        Array.empty,
        true
      )
    }
     
    var enclClassNames = List[Array[Char]]()

    override def traverse(tree: Tree): Unit = {
      def inClass(c : Array[Char])(block : => Unit) {
        val old = enclClassNames
        enclClassNames = c::enclClassNames
        block
        enclClassNames = old
      }
      
      tree match {
        case pd : PackageDef => addPackage(pd)
        case cd : ClassDef => addClass(cd)
        case md : ModuleDef => addModule(md)
        case vd : ValDef => addVal(vd)
        case td : TypeDef => addType(td)
        case dd : DefDef if dd.name != nme.MIXIN_CONSTRUCTOR => addDef(dd)
        
        case _ =>
      }
      
      tree match {
        case cd : ClassDef => inClass(cd.name.toChars) { super.traverse(tree) }
        case md : ModuleDef => inClass(md.name.append("$").toChars) { super.traverse(tree) }
        
        case Apply(rt : RefTree, args) =>
          indexer.addMethodReference(rt.name.toChars, args.length)
          for (t <- args) traverse(t)
          
        // Partial apply.
        case Typed(ttree, Function(_, _)) => 
          ttree match {
            case rt : RefTree => 
              val name = rt.name.toChars
              for (i <- 0 to maxArgs) indexer.addMethodReference(name, i)
            case Apply(rt : RefTree, args) => 
              val name = rt.name.toChars
              for (i <- args.length to maxArgs) indexer.addMethodReference(name, i)
            case _ =>
          }
          super.traverse(tree)
          
        case rt : RefTree =>
          val name = rt.name.toChars
          indexer.addTypeReference(name)
          indexer.addFieldReference(name)
          super.traverse(tree)
          
        case _ => super.traverse(tree)
      }
    }
    
    val maxArgs = 22  // just arbitrary choice.
  }
  
}
