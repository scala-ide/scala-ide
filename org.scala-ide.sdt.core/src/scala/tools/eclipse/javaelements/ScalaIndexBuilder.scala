/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse.javaelements

import org.eclipse.core.resources.IFile

import scala.tools.nsc.symtab.Flags

import scala.tools.eclipse.{ ScalaPlugin, ScalaPresentationCompiler,
	                           ScalaSourceIndexer, SettingConverterUtil }
import scala.tools.eclipse.properties.ScalaPluginSettings

/** Add entries to the JDT index. This class traverses an *unattributed* Scala AST. This 
 *  means a tree without symbols or types. However, a tree that was typed may still get here
 *  (usually during reconciliation, after editing and saving a file). That adds some complexity
 *  because the Scala typechecker modifies the tree. One prime example is annotations, which
 *  after type-checking are removed from the tree and placed in the corresponding Symbol.
 *  
 *  The indexer builds a map from names to documents that mention that name. Names are
 *  categorized (for instance, as method definitions, method references, annotation references, etc.).
 *  
 *  The indexer is later used by the JDT to narrow the scope of a search. For instance, a search
 *  for test methods would first check the index for documents that have annotation references to
 *  'Test' in 'org.junit', and then pass those documents to the structure builder for  
 *  precise parsing, where names are actually resolved.
 */
trait ScalaIndexBuilder { self : ScalaPresentationCompiler =>

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
      if (!packageName.isEmpty) packageName.append('.')
      if (p.name != nme.EMPTY_PACKAGE_NAME && p.name != nme.ROOTPKG) {
        if (isInfo) logger.info("Package defn: "+p.name+" ["+this+"]")
        packageName.append(p.name)  
      }
    }

    def getSuperNames(supers: List[Tree]) = supers map (_ match {
      case Ident(id)                           => id.toChars
      case Select(_, name)                     => name.toChars
      case AppliedTypeTree(fun: RefTree, args) => fun.name.toChars
      case tpt @ TypeTree()                    => tpt.tpe.typeSymbol.name.toChars // maybe the tree was typed
      case parent =>
        logger.info("superclass not understood: %s".format(parent))
        "$$NoRef".toCharArray
    }) toArray
    
    /** Add annotations on the given tree. If the tree is not yet typed,
     *  it uses the (unresolved) annotations in the tree (part of modifiers).
     *  If the modifiers are empty, it uses the Symbol for finding them (the type-checker
     *  moves the annotations from the tree to the symbol).
     */
    def addAnnotations(tree: MemberDef) {
      if (tree.mods.annotations.isEmpty && tree.symbol.isInitialized) // don't force any symbols
        addAnnotations(tree.symbol)
      else 
        tree.mods.annotations.foreach(addAnnotationRef)
    }
    
    private def addAnnotations(sym: Symbol) =
      for (ann <- sym.annotations) {
        if (isInfo) logger.info("added annotation %s [using symbols]".format(ann.atp))
        indexer.addAnnotationTypeReference(ann.atp.toString.toChars)
      }
    
    private def addAnnotationRef(tree: Tree) {
      for (t <- tree) t match {
        case New(tpt) =>
          if (isInfo) logger.info("added annotation %s [using trees]".format(tpt))
          indexer.addAnnotationTypeReference(tpt.toString.toChars)
        case _ => ()
      }
    }
      
    def addClass(c : ClassDef) {
   	  if (isInfo) {      		
        logger.info("Class defn: "+c.name+" ["+this+"]")
        logger.info("Parents: "+c.impl.parents)
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
      
      addAnnotations(c)
    }
    
    def addModule(m : ModuleDef) {
      if (isInfo)
        logger.info("Module defn: "+m.name+" ["+this+"]")
      
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
        logger.info("Val defn: >"+nme.getterName(v.name)+"< ["+this+"]")
        
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
      addAnnotations(v)
    }
    
    def addDef(d : DefDef) {
      if (isInfo)
        logger.info("Def defn: "+d.name+" ["+this+"]")
      val name = if(nme.isConstructorName(d.name)) enclClassNames.head else d.name.toChars
        
      val fps = for(vps <- d.vparamss; vp <- vps) yield vp
        
      val paramTypes = fps.map(v => mapType(v.tpt))
      indexer.addMethodDeclaration(
        name,
        paramTypes.map(_.toCharArray).toArray,
        mapType(d.tpt).toArray,
        Array.empty
      )
      addAnnotations(d)
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
          if (isInfo)
            logger.info("method reference: "+rt.name+" ["+args.length+"]")

          indexer.addMethodReference(rt.name.toChars, args.length)
          super.traverse(tree)
          
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
