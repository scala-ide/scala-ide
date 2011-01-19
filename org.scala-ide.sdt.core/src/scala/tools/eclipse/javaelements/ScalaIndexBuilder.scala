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

  object IndexBuilderTraverser {
  	lazy val store = ScalaPlugin.plugin.getPreferenceStore
  	lazy val infoName = 
  		SettingConverterUtil.convertNameToProperty(ScalaPluginSettings.YPlugininfo.name)
  	@inline def isInfo = store.getBoolean(infoName)
  }
  
  import IndexBuilderTraverser.isInfo
  
  class IndexBuilderTraverser(indexer : ScalaSourceIndexer) extends Traverser {
    def addPackage(p : PackageDef) = {
      if (isInfo) println("Package defn: "+p.name+" ["+this+"]")
    }
    
    def addClass(c : ClassDef) {
   	  if (isInfo) {      		
        println("Class defn: "+c.name+" ["+this+"]")
        println("Parents: "+c.impl.parents)
      }
        
      val name = c.symbol.simpleName.toString
        
      val parentTree = c.impl.parents.head
      val superclassType = parentTree.tpe
      val (superclassName, primaryType, interfaceTrees) =
        if (superclassType == null)
          (null, null, c.impl.parents)
        else if (superclassType.typeSymbol.isTrait)
          (null, superclassType.typeSymbol, c.impl.parents)
        else {
          val interfaceTrees0 = c.impl.parents.drop(1) 
          val superclassName0 = superclassType.typeSymbol.fullName
          if (superclassName0 == "java.lang.Object") {
            if (interfaceTrees0.isEmpty)
              ("java.lang.Object".toCharArray, null, interfaceTrees0)
            else
              (null, interfaceTrees0.head.tpe.typeSymbol, interfaceTrees0)
          }
          else
            (superclassName0.toCharArray, superclassType.typeSymbol, interfaceTrees0)   
        }

      val mask = ~(if (c.symbol.isAnonymousClass) ClassFileConstants.AccPublic else 0)
        
      val interfaceTypes = interfaceTrees.map(t => (t, t.tpe))
      val interfaceNames = interfaceTypes.map({ case (tree, tpe) => (if (tpe ne null) tpe.typeSymbol.fullName else "null-"+tree).toCharArray })
        
      indexer.addClassDeclaration(
        mapModifiers(c.symbol) & mask,
        c.symbol.enclosingPackage.fullName.toCharArray,
        name.toCharArray,
        enclosingTypeNames(c.symbol).map(_.toArray).toArray,
        superclassName,
        interfaceNames.toArray,
        new Array[Array[Char]](0),
        true
      )

      addAnnotations(c.symbol.annotations)
    }
    
    def addModule(m : ModuleDef) {
      if (isInfo)
        println("Module defn: "+m.name+" ["+this+"]")
        
      val name = m.symbol.simpleName.toString

      val parentTree = m.impl.parents.head
      val superclassType = parentTree.tpe
      val superclassName = (if (superclassType ne null) superclassType.typeSymbol.fullName else "null-"+parentTree).toCharArray
        
      val interfaceTrees = m.impl.parents.drop(1)
      val interfaceTypes = interfaceTrees.map(t => (t, t.tpe))
      val interfaceNames = interfaceTypes.map({ case (tree, tpe) => (if (tpe ne null) tpe.typeSymbol.fullName else "null-"+tree).toCharArray })
        
      indexer.addClassDeclaration(
        mapModifiers(m.symbol),
        m.symbol.enclosingPackage.fullName.toCharArray,
        (name+"$").toCharArray,
        enclosingTypeNames(m.symbol).map(_.toArray).toArray,
        superclassName,
        interfaceNames.toArray,
        new Array[Array[Char]](0),
        true
      )
        
      indexer.addFieldDeclaration(
        (m.symbol.fullName+"$").toCharArray,
        "MODULE$".toCharArray
      )

      indexer.addClassDeclaration(
        mapModifiers(m.symbol),
        m.symbol.enclosingPackage.fullName.toCharArray,
        name.toString.toCharArray,
        new Array[Array[Char]](0),
        superclassName,
        interfaceNames.toArray,
        new Array[Array[Char]](0),
        true
      )

      addAnnotations(m.symbol.annotations)
    }
    
    def addVal(v : ValDef) {
     if (isInfo)
        println("Val defn: >"+nme.getterName(v.name)+"< ["+this+"]")
        
      indexer.addMethodDeclaration(
        nme.getterName(v.name).toString.toCharArray,
        new Array[Array[Char]](0),
        mapType(v.tpt).toArray,
        new Array[Array[Char]](0)
      )
        
      if(v.symbol.hasFlag(Flags.MUTABLE))
        indexer.addMethodDeclaration(
          nme.getterToSetter(nme.getterName(v.name)).toString.toCharArray,
          new Array[Array[Char]](0),
          mapType(v.tpt).toArray,
          new Array[Array[Char]](0)
        )
        
      addAnnotations(v.symbol.annotations)
    }
    
    def addDef(d : DefDef) {
      if (isInfo)
        println("Def defn: "+d.name+" ["+this+"]")
      val isCtor0 = d.symbol.isConstructor
      val nm =
        if(isCtor0)
          d.symbol.owner.simpleName
        else
          d.name
        
      val fps = for(vps <- d.vparamss; vp <- vps) yield vp
        
      val paramTypes = fps.map(v => mapType(v.tpt))
      indexer.addMethodDeclaration(
        nm.toString.toCharArray,
        paramTypes.map(_.toCharArray).toArray,
        mapType(d.tpt).toArray,
        new Array[Array[Char]](0)
      )
        
      addAnnotations(d.symbol.annotations)
    }
    
    def addType(td : TypeDef) {
      // TODO
    }

    def addAnnotations(annots : List[AnnotationInfo]) {
      annots.map(annot => indexer.addAnnotationTypeReference(annot.atp.typeSymbol.nameString.toArray))
    }
    
    override def traverse(tree: Tree): Unit = tree match {
      case pd : PackageDef => addPackage(pd); super.traverse(tree)
      case cd : ClassDef => addClass(cd); traverse(cd.impl)
      case md : ModuleDef => addModule(md); super.traverse(tree)
      case vd : ValDef => addVal(vd); traverse(vd.rhs)
      case td : TypeDef => addType(td); traverse(td.rhs)
      case dd : DefDef =>
        if(dd.name != nme.MIXIN_CONSTRUCTOR) {
          addDef(dd)
          traverse(dd.tpt)
          traverse(dd.rhs)
        }
      case tt : TypeTree if tt.original ne null => {
        super.traverse(tt.original)            
      }
      case u =>
        super.traverse(tree)
    }
  }
}
