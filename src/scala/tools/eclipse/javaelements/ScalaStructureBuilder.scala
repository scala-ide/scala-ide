/*
 * Copyright 2005-2008 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse.javaelements

import java.util.Map

import org.eclipse.core.resources.IFile
import org.eclipse.jdt.internal.core.{ CompilationUnit, JavaElement, JavaModelManager, SourceRefElement }
import org.eclipse.jdt.internal.compiler.classfmt.ClassFileConstants
import org.eclipse.jdt.core.Signature

import scala.tools.nsc.symtab.Flags

trait ScalaStructureBuilder extends ScalaJavaMapper { self : ScalaCompilationUnit =>
  import compiler.{ ClassDef, DefDef, Function, Ident, ModuleDef, PackageDef, StubTree, Template, Traverser, Tree, TypeDef, TypeTree, ValDef }
    
  class StructureBuilderTraverser(unitInfo : ScalaCompilationUnitInfo, newElements0 : Map[AnyRef, AnyRef]) extends Traverser {
    private var currentBuilder : Owner = new CompilationUnitBuilder
    private val manager = JavaModelManager.getJavaModelManager
    private val file = proj.fileSafe(self.getResource.asInstanceOf[IFile]).get
    
    trait Owner {
      def parent : Owner

      def element : JavaElement
      def elementInfo : AnyRef
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

      def addChild(child : JavaElement) =
        JavaElementInfoUtils.addChild(elementInfo, child)
      
      def resolveDuplicates(handle : SourceRefElement) {
        while (newElements0.containsKey(handle)) {
          handle.occurrenceCount += 1
        }
      }
    }
    
    trait PackageOwner extends Owner { self =>
      override def addPackage(p : PackageDef) : Owner = {
        println("Package defn: "+p.name+" ["+this+"]")
        
        val pkgElem = JavaElementFactory.createPackageDeclaration(compilationUnitBuilder.element.asInstanceOf[CompilationUnit], p.symbol.fullNameString)
        resolveDuplicates(pkgElem)
        JavaElementInfoUtils.addChild(compilationUnitBuilder.elementInfo, pkgElem)
        
        val pkgElemInfo = JavaElementFactory.createSourceRefElementInfo
        newElements0.put(pkgElem, pkgElemInfo)
        
        new Builder {
          val parent = self
          val element = pkgElem
          val elementInfo = pkgElemInfo
          override def isPackage = true
        }
      }
    }
    
    trait ClassOwner extends Owner { self =>
      override def addClass(c : ClassDef) : Owner = {
        println("Class defn: "+c.name+" ["+this+"]")
        
        val owner = if (isPackage) compilationUnitBuilder else this
        val classElem =
          if(c.mods.isTrait)
            new ScalaTraitElement(owner.element, c.name.toString)
          else
            new ScalaClassElement(owner.element, c.name.toString)
        
        resolveDuplicates(classElem)
        owner.addChild(classElem)
        
        val classElemInfo = new ScalaElementInfo
        classElemInfo.setHandle(classElem)
        classElemInfo.setFlags0(mapModifiers(c.mods))
        val start = c.symbol.pos.offset.getOrElse(0)
        val end = start + c.name.length - 1
        classElemInfo.setNameSourceStart0(start)
        classElemInfo.setNameSourceEnd0(end)
        newElements0.put(classElem, classElemInfo)
        
        new Builder {
          val parent = self
          val element = classElem
          val elementInfo = classElemInfo
          override def isTemplate = true
          override def template = this
        }
      }
    }
    
    trait ModuleOwner extends Owner { self =>
      override def addModule(m : ModuleDef) : Owner = {
        println("Module defn: "+m.name+" ["+this+"]")
        
        val owner = if (isPackage) compilationUnitBuilder else this
        val moduleElem = new ScalaModuleElement(owner.element, m.name.toString)
        resolveDuplicates(moduleElem)
        owner.addChild(moduleElem)
        
        val moduleElemInfo = new ScalaElementInfo
        moduleElemInfo.setHandle(moduleElem)
        moduleElemInfo.setFlags0(mapModifiers(m.mods))
        val start = m.symbol.pos.offset.getOrElse(0)
        val end = start + m.name.length -1
        moduleElemInfo.setNameSourceStart0(start)
        moduleElemInfo.setNameSourceEnd0(end)
        newElements0.put(moduleElem, moduleElemInfo)
        
        val mb = new Builder {
          val parent = self
          val element = moduleElem
          val elementInfo = moduleElemInfo
          override def isTemplate = true
          override def template = this
        }

        val instanceElem = new ScalaModuleInstanceElement(moduleElem)
        mb.resolveDuplicates(instanceElem)
        mb.addChild(instanceElem)
        
        val instanceElemInfo = new ScalaSourceFieldElementInfo
        instanceElemInfo.setFlags0(ClassFileConstants.AccPrivate | ClassFileConstants.AccStatic | ClassFileConstants.AccFinal)
        newElements0.put(instanceElem, instanceElemInfo)
        
        mb
      }
    }
    
    trait ValOwner extends Owner { self =>
      override def addVal(v : ValDef) : Owner = {
        println("Val defn: >"+compiler.nme.getterName(v.name)+"< ["+this+"]")
        
        val elemName = compiler.nme.getterName(v.name)
        
        val valElem =
          if(v.mods.hasFlag(Flags.MUTABLE))
            new ScalaVarElement(element, elemName.toString)
          else
            new ScalaValElement(element, elemName.toString)
        resolveDuplicates(valElem)
        addChild(valElem)

        val valElemInfo = new ScalaSourceFieldElementInfo
        valElemInfo.setFlags0(mapModifiers(v.mods))
        val start = v.symbol.pos.offset.getOrElse(0)
        val end = start + elemName.length - 1
        valElemInfo.setNameSourceStart0(start)
        valElemInfo.setNameSourceEnd0(end)
        newElements0.put(valElem, valElemInfo)

        
        val tn = manager.intern(mapType(v.tpt).toArray)
        valElemInfo.setTypeName(tn)
        
        new Builder {
          val parent = self
          val element = valElem
          val elementInfo = valElemInfo
        } 
      }
    }
    
    trait TypeOwner extends Owner { self =>
      override def addType(t : TypeDef) : Owner = {
        println("Type defn: >"+t.name.toString+"< ["+this+"]")
        
        val typeElem = new ScalaTypeElement(element, t.name.toString)
        resolveDuplicates(typeElem)
        addChild(typeElem)

        val typeElemInfo = new ScalaSourceFieldElementInfo
        typeElemInfo.setFlags0(mapModifiers(t.mods))
        val start = t.symbol.pos.offset.getOrElse(0)
        val end = start + t.name.length - 1
        typeElemInfo.setNameSourceStart0(start)
        typeElemInfo.setNameSourceEnd0(end)
        newElements0.put(typeElem, typeElemInfo)
        
        if(t.rhs.symbol == compiler.NoSymbol) {
          println("Type is abstract")
          val tn = manager.intern("java.lang.Object".toArray)
          typeElemInfo.setTypeName(tn)
        } else {
          println("Type has type: "+t.rhs.symbol.fullNameString)
          val tn = manager.intern(mapType(t.rhs).toArray)
          typeElemInfo.setTypeName(tn)
        }
        
        new Builder {
          val parent = self
          val element = typeElem
          val elementInfo = typeElemInfo
        } 
      }
    }

    trait DefOwner extends Owner { self =>
      override def addDef(d : DefDef) : Owner = {
        println("Def defn: "+d.name+" ["+this+"]")
        val isCtor0 = d.name.toString == "<init>"
        val nm =
          if(isCtor0)
            currentOwner.simpleName
          else
            d.name
        
        val fps = for(vps <- d.vparamss; vp <- vps) yield vp
        
        val paramTypes = Array(fps.map(v => Signature.createTypeSignature(mapType(v.tpt), false)) : _*)
        val paramNames = Array(fps.map(n => compiler.nme.getterName(n.name).toString.toArray) : _*)
        
        val defElem = 
          if(d.mods.isAccessor)
            new ScalaAccessorElement(element, nm.toString, paramTypes)
          else if(isTemplate)
            new ScalaDefElement(element, nm.toString, paramTypes)
          else
            new ScalaFunctionElement(template.element, element, nm.toString, paramTypes)
        resolveDuplicates(defElem)
        addChild(defElem)
        
        val defElemInfo : DefInfo =
          if(isCtor0)
            new ScalaSourceConstructorInfo
          else
            new ScalaSourceMethodInfo
        
        if(d.symbol.isGetter) {
          for (child <- JavaElementInfoUtils.getChildren(elementInfo)) {
            child match {
              case f : ScalaFieldElement if f.getElementName == d.name.toString => {
                val fInfo = f.getElementInfo.asInstanceOf[ScalaSourceFieldElementInfo]
                fInfo.setFlags0(mapModifiers(d.mods))
              }
              case c => println("Skipping: >"+c.getElementName+"< != >"+d.name.toString+"<")
            }
          }
        }
        
        defElemInfo.setArgumentNames(paramNames)
        defElemInfo.setExceptionTypeNames(new Array[Array[Char]](0))
        val tn = manager.intern(mapType(d.tpt).toArray)
        defElemInfo.asInstanceOf[DefInfo].setReturnType(tn)
        
        val mods =
          if(isTemplate)
            mapModifiers(d.mods)
          else
            ClassFileConstants.AccPrivate

        defElemInfo.setFlags0(mods)
        val start = d.symbol.pos.offset.getOrElse(0)
        val end = start + d.name.length - 1
        defElemInfo.setNameSourceStart0(start)
        defElemInfo.setNameSourceEnd0(end)
        
        newElements0.put(defElem, defElemInfo)
        
        new Builder {
          val parent = self
          val element = defElem
          val elementInfo = defElemInfo
          override def isCtor = isCtor0
        }
      }
    }
    
    class CompilationUnitBuilder extends PackageOwner with ClassOwner with ModuleOwner {
      val parent = null
      val element = self
      val elementInfo = unitInfo
      override def compilationUnitBuilder = this 
    }
    
    abstract class Builder extends PackageOwner with ClassOwner with ModuleOwner with ValOwner with TypeOwner with DefOwner
    
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
        if(dd.name != compiler.nme.MIXIN_CONSTRUCTOR) {
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
      case st : StubTree => {
        println("Stub tree")
        traverseTrees(st.underlying.asInstanceOf[file.ParseNode].lastTyped)
      }
      case u =>
        println("Unknown type: "+u.getClass.getSimpleName+" "+u)
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
