/*
 * Copyright 2005-2008 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse.javaelements

import org.eclipse.core.resources.IFile
import org.eclipse.jdt.internal.compiler.classfmt.ClassFileConstants

trait ScalaIndexBuilder extends ScalaJavaMapper { self : ScalaCompilationUnit =>
  import proj.compiler._
  
  class IndexBuilderTraverser(indexer : ScalaSourceIndexer) extends Traverser {
    private var currentBuilder : Owner = new CompilationUnitBuilder
    private val file = new ScalaFile(self.getResource.asInstanceOf[IFile])
    
    trait Owner {
      def parent : Owner

      def compilationUnitBuilder : Owner = parent.compilationUnitBuilder
      
      def isPackage = false
      def isCtor = false
      def isTemplate = false
      def template : Owner = if (parent != null) parent.template else null

      def addPackage(p : PackageDef) : Owner = this
      def addClass(c : ClassDef) : Owner = this
      def addModule(m : ModuleDef) : Owner = this
      def addVal(v : ValDef) : Owner = this
      def addType(t : TypeDef) : Owner = this
      def addDef(d : DefDef) : Owner = this
      def addFunction(f : Function) : Owner = this
    }
    
    trait PackageOwner extends Owner { self =>
      override def addPackage(p : PackageDef) : Owner = {
        println("Package defn: "+p.name+" ["+this+"]")
        
        new Builder {
          val parent = self
          override def isPackage = true
        }
      }
    }
    
    trait ClassOwner extends Owner { self =>
      override def addClass(c : ClassDef) : Owner = {
        println("Class defn: "+c.name+" ["+this+"]")
        println("Parents: "+c.impl.parents)
        
        val name0 = c.name.toString
        val isAnon = name0 == "$anon"
        val name = if (isAnon) "" else name0
        
        val parentTree = c.impl.parents.first
        val superclassType = parentTree.tpe
        val (superclassName, primaryType, interfaceTrees) =
          if (superclassType == null)
            (null, null, c.impl.parents)
          else if (superclassType.typeSymbol.isTrait)
            (null, superclassType.typeSymbol, c.impl.parents)
          else {
            val interfaceTrees0 = c.impl.parents.drop(1) 
            val superclassName0 = superclassType.typeSymbol.fullNameString
            if (superclassName0 == "java.lang.Object") {
              if (interfaceTrees0.isEmpty)
                ("java.lang.Object".toCharArray, null, interfaceTrees0)
              else
                (null, interfaceTrees0.first.tpe.typeSymbol, interfaceTrees0)
            }
            else
              (superclassName0.toCharArray, superclassType.typeSymbol, interfaceTrees0)   
          }

        
        val mask = ~(if (isAnon) ClassFileConstants.AccPublic else 0)
        
        val interfaceTypes = interfaceTrees.map(t => (t, t.tpe))
        val interfaceNames = interfaceTypes.map({ case (tree, tpe) => (if (tpe ne null) tpe.typeSymbol.fullNameString else "null-"+tree).toCharArray })
        
        indexer.addClassDeclaration(
          mapModifiers(c.mods) & mask,
          "plugin.test".toCharArray,
          name.toCharArray,
          new Array[Array[Char]](0),
          superclassName,
          interfaceNames.toArray,
          new Array[Array[Char]](0),
          true
        )
        
        new Builder {
          val parent = self
          
          override def isTemplate = true
          override def template = this
        }
      }
    }
    
    trait ModuleOwner extends Owner { self =>
      override def addModule(m : ModuleDef) : Owner = {
        println("Module defn: "+m.name+" ["+this+"]")
        
        val parentTree = m.impl.parents.first
        val superclassType = parentTree.tpe
        val superclassName = (if (superclassType ne null) superclassType.typeSymbol.fullNameString else "null-"+parentTree).toCharArray
        
        val interfaceTrees = m.impl.parents.drop(1)
        val interfaceTypes = interfaceTrees.map(t => (t, t.tpe))
        val interfaceNames = interfaceTypes.map({ case (tree, tpe) => (if (tpe ne null) tpe.typeSymbol.fullNameString else "null-"+tree).toCharArray })
        
        indexer.addClassDeclaration(
          mapModifiers(m.mods),
          "plugin.test".toCharArray,
          (m.name+"$").toCharArray,
          new Array[Array[Char]](0),
          superclassName,
          interfaceNames.toArray,
          new Array[Array[Char]](0),
          true
        )

        indexer.addClassDeclaration(
          mapModifiers(m.mods),
          "plugin.test".toCharArray,
          m.name.toString.toCharArray,
          new Array[Array[Char]](0),
          superclassName,
          interfaceNames.toArray,
          new Array[Array[Char]](0),
          true
        )

        new Builder {
          val parent = self
          
          override def isTemplate = true
          override def template = this
        }
      }
    }
    
    class CompilationUnitBuilder extends PackageOwner with ClassOwner with ModuleOwner {
      val parent = null
      override def compilationUnitBuilder = this 
    }
    
    abstract class Builder extends PackageOwner with ClassOwner with ModuleOwner
    
    override def traverse(tree: Tree): Unit = tree match {
      case pd : PackageDef => atBuilder(currentBuilder.addPackage(pd)) { super.traverse(tree) }
      case cd : ClassDef => {
        val cb = currentBuilder.addClass(cd)
        atOwner(tree.symbol) {
          atBuilder(cb) {
            //traverseTrees(cd.mods.annotations)
            //traverseTrees(cd.tparams)
            traverse(cd.impl)
          }
        }
      }
      case md : ModuleDef => atBuilder(currentBuilder.addModule(md)) { super.traverse(tree) }
      case vd : ValDef => {
        val vb = currentBuilder.addVal(vd)
        atOwner(tree.symbol) {
          atBuilder(vb) {
            //traverseTrees(vd.mods.annotations)
            //traverse(vd.tpt)
            traverse(vd.rhs)
          }
        }
      }
      case td : TypeDef => {
        val tb = currentBuilder.addType(td)
        atOwner(tree.symbol) {
          atBuilder(tb) {
            //traverseTrees(td.mods.annotations);
            //traverseTrees(td.tparams);
            traverse(td.rhs)
          }
        }
      }
      case dd : DefDef => {
        if(dd.name != nme.MIXIN_CONSTRUCTOR) {
          val db = currentBuilder.addDef(dd)
          atOwner(tree.symbol) {
            // traverseTrees(dd.mods.annotations)
            // traverseTrees(dd.tparams)
            //if(db.isCtor)
            //  atBuilder(currentBuilder) { traverseTreess(dd.vparamss) }
            //else
            //  atBuilder(db) { traverseTreess(dd.vparamss) }
            atBuilder(db) {
              traverse(dd.tpt)
              traverse(dd.rhs)
            }
          }
        }
      }
      case Template(parents, self, body) => {
        println("Template: "+parents)
        //traverseTrees(parents)
        //if (!self.isEmpty) traverse(self)
        traverseStats(body, tree.symbol)
      }
      case Function(vparams, body) => {
        println("Anonymous function: "+tree.symbol.simpleName)
      }
      case tt : TypeTree => {
        //println("Type tree: "+tt)
        //atBuilder(currentBuilder.addTypeTree(tt)) { super.traverse(tree) }            
        super.traverse(tree)            
      }
      case u =>
        //println("Unknown type: "+u.getClass.getSimpleName+" "+u)
        super.traverse(tree)
    }

    def atBuilder(builder: Owner)(traverse: => Unit) {
      val prevBuilder = currentBuilder
      currentBuilder = builder
      traverse
      currentBuilder = prevBuilder
    }
  }
}
