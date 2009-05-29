/*
 * Copyright 2005-2009 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse.javaelements

import java.io.{ PrintWriter, StringWriter }
import java.util.{ Map => JMap }

import org.eclipse.core.resources.IFile
import org.eclipse.jdt.internal.core.{ CompilationUnit => JDTCompilationUnit, JavaElement, JavaElementInfo, JavaModelManager, OpenableElementInfo, SourceRefElement }
import org.eclipse.jdt.internal.compiler.classfmt.ClassFileConstants
import org.eclipse.jdt.core.Signature

import scala.collection.immutable.Map
import scala.tools.nsc.symtab.Flags
import scala.tools.nsc.util.{ NameTransformer, NoPosition, Position }

import scala.tools.eclipse.ScalaPresentationCompiler

trait ScalaStructureBuilder { self : ScalaPresentationCompiler =>

  class StructureBuilderTraverser(scu : ScalaCompilationUnit, unitInfo : ScalaCompilationUnitElementInfo, newElements0 : JMap[AnyRef, AnyRef], sourceLength : Int) extends Traverser {
    private var currentBuilder : Owner = new CompilationUnitBuilder
    private val manager = JavaModelManager.getJavaModelManager
    
    trait Owner {
      def parent : Owner

      def element : JavaElement
      def elementInfo : JavaElementInfo
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
        elementInfo match {
          case scalaMember : ScalaMemberElementInfo => scalaMember.addChild0(child)
          case openable : OpenableElementInfo => OpenableElementInfoUtils.addChild(openable, child)
          case _ =>
        }
      
      def resolveDuplicates(handle : SourceRefElement) {
        while (newElements0.containsKey(handle)) {
          handle.occurrenceCount += 1
        }
      }
    }
    
    trait PackageOwner extends Owner { self =>
      override def addPackage(p : PackageDef) : Owner = {
        //println("Package defn: "+p.name+" ["+this+"]")
        
        val pkgElem = JavaElementFactory.createPackageDeclaration(compilationUnitBuilder.element.asInstanceOf[JDTCompilationUnit], p.symbol.fullNameString)
        resolveDuplicates(pkgElem)
        compilationUnitBuilder.addChild(pkgElem)
        
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
        //println("Class defn: "+c.name+" ["+this+"]")
        //println("Parents: "+c.impl.parents)
        
        val owner = if (isPackage) compilationUnitBuilder else this
        val name0 = c.name.toString
        val isAnon = name0 == "$anon"
        val name = if (isAnon) "" else name0
        
        val parentTree = c.impl.parents.head
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
                (null, interfaceTrees0.head.tpe.typeSymbol, interfaceTrees0)
            }
            else
              (superclassName0.toCharArray, superclassType.typeSymbol, interfaceTrees0)   
          }

        val classElem =
          if(c.mods.isTrait)
            new ScalaTraitElement(owner.element, name)
          else if (isAnon) {
            val primaryTypeString = if (primaryType != null) primaryType.name.toString else null
            new ScalaAnonymousClassElement(owner.element, primaryTypeString)
          }
          else
            new ScalaClassElement(owner.element, name)
        
        resolveDuplicates(classElem)
        owner.addChild(classElem)
        
        val classElemInfo = new ScalaElementInfo
        classElemInfo.setHandle(classElem)
        val mask = ~(if (isAnon) ClassFileConstants.AccPublic else 0)
        classElemInfo.setFlags0(mapModifiers(c.mods) & mask)
        
        classElemInfo.setSuperclassName(superclassName)
        
        val interfaceTypes = interfaceTrees.map(t => (t, t.tpe))
        val interfaceNames = interfaceTypes.map({ case (tree, tpe) => (if (tpe ne null) tpe.typeSymbol.fullNameString else "null-"+tree).toCharArray })
        classElemInfo.setSuperInterfaceNames(interfaceNames.toArray)
        
        val (start, end) = if (!isAnon) {
          val start0 = c.pos.point 
          (start0, start0 + name.length - 1)
        } else if (primaryType != null) {
          val start0 = parentTree.pos.point
          (start0, start0 + primaryType.name.length - 1)
        } else {
          val start0 = parentTree.pos.point
          (start0, start0 - 1)
        }
        
        classElemInfo.setNameSourceStart0(start)
        classElemInfo.setNameSourceEnd0(end)
        setSourceRange(classElemInfo, c)
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
        //println("Module defn: "+m.name+" ["+this+"]")
        
        val owner = if (isPackage) compilationUnitBuilder else this
        val moduleElem = new ScalaModuleElement(owner.element, m.name.toString, m.symbol.hasFlag(Flags.SYNTHETIC))
        resolveDuplicates(moduleElem)
        owner.addChild(moduleElem)
        
        val moduleElemInfo = new ScalaElementInfo
        moduleElemInfo.setHandle(moduleElem)
        moduleElemInfo.setFlags0(mapModifiers(m.mods))
        
        val start = m.pos.point
        val end = start+m.name.length-1
        
        moduleElemInfo.setNameSourceStart0(start)
        moduleElemInfo.setNameSourceEnd0(end)
        setSourceRange(moduleElemInfo, m)
        newElements0.put(moduleElem, moduleElemInfo)
        
        val parentTree = m.impl.parents.head
        val superclassType = parentTree.tpe
        val superclassName = (if (superclassType ne null) superclassType.typeSymbol.fullNameString else "null-"+parentTree).toCharArray
        moduleElemInfo.setSuperclassName(superclassName)
        
        val interfaceTrees = m.impl.parents.drop(1)
        val interfaceTypes = interfaceTrees.map(t => (t, t.tpe))
        val interfaceNames = interfaceTypes.map({ case (tree, tpe) => (if (tpe ne null) tpe.typeSymbol.fullNameString else "null-"+tree).toCharArray })
        moduleElemInfo.setSuperInterfaceNames(interfaceNames.toArray)
        
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
        instanceElemInfo.setFlags0(ClassFileConstants.AccPublic | ClassFileConstants.AccStatic | ClassFileConstants.AccFinal)
        instanceElemInfo.setTypeName(moduleElem.getFullyQualifiedName('.').toCharArray)
        newElements0.put(instanceElem, instanceElemInfo)
        
        mb
      }
    }
    
    trait ValOwner extends Owner { self =>
      override def addVal(v : ValDef) : Owner = {
        //println("Val defn: >"+nme.getterName(v.name)+"< ["+this+"]")
        
        val elemName = nme.getterName(v.name)
        
        val valElem =
          if(v.mods.hasFlag(Flags.MUTABLE))
            new ScalaVarElement(element, elemName.toString)
          else
            new ScalaValElement(element, elemName.toString)
        resolveDuplicates(valElem)
        addChild(valElem)
        
        val valElemInfo = new ScalaSourceFieldElementInfo
        valElemInfo.setFlags0(mapModifiers(v.mods))
        
        val start = v.pos.point
        val end = start+elemName.length-1
        
        valElemInfo.setNameSourceStart0(start)
        valElemInfo.setNameSourceEnd0(end)
        setSourceRange(valElemInfo, v)
        newElements0.put(valElem, valElemInfo)

        val tn = manager.intern(mapType(v.tpt).toArray)
        valElemInfo.setTypeName(tn)
        
        new Builder {
          val parent = self
          val element = valElem
          val elementInfo = valElemInfo
          
          override def addDef(d : DefDef) = this
          override def addVal(v: ValDef) = this
          override def addType(t : TypeDef) = this
        }
      }
    }
    
    trait TypeOwner extends Owner { self =>
      override def addType(t : TypeDef) : Owner = {
        //println("Type defn: >"+t.name.toString+"< ["+this+"]")
        
        val typeElem = new ScalaTypeElement(element, t.name.toString)
        resolveDuplicates(typeElem)
        addChild(typeElem)

        val typeElemInfo = new ScalaSourceFieldElementInfo
        typeElemInfo.setFlags0(mapModifiers(t.mods))
        
        val start = t.pos.point
        val end = start+t.name.length-1

        typeElemInfo.setNameSourceStart0(start)
        typeElemInfo.setNameSourceEnd0(end)
        setSourceRange(typeElemInfo, t)
        newElements0.put(typeElem, typeElemInfo)
        
        if(t.rhs.symbol == NoSymbol) {
          //println("Type is abstract")
          val tn = manager.intern("java.lang.Object".toArray)
          typeElemInfo.setTypeName(tn)
        } else {
          //println("Type has type: "+t.rhs.symbol.fullNameString)
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
        //println("Def defn: "+d.name+" ["+this+"]")
        val isCtor0 = d.name.toString == "<init>"
        val nm =
          if(isCtor0)
            currentOwner.simpleName
          else
            d.name
        
        val fps = for(vps <- d.vparamss; vp <- vps) yield vp
        
        val paramTypes = Array(fps.map(v => Signature.createTypeSignature(mapType(v.tpt), false)) : _*)
        val paramNames = Array(fps.map(n => nme.getterName(n.name).toString.toArray) : _*)
        
        val sw = new StringWriter
        val tp = treePrinters.create(new PrintWriter(sw))
        tp.print(tp.symName(d, d.name))
        tp.printTypeParams(d.tparams)
        d.vparamss foreach tp.printValueParams
        tp.flush
        val display = sw.toString
        
        val defElem = 
          if(d.mods.isAccessor)
            new ScalaAccessorElement(element, nm.toString, paramTypes)
          else if(isTemplate)
            new ScalaDefElement(element, nm.toString, paramTypes, d.symbol.hasFlag(Flags.SYNTHETIC), d.mods.isOverride, display)
          else
            new ScalaFunctionElement(template.element, element, nm.toString, paramTypes, display)
        resolveDuplicates(defElem)
        addChild(defElem)
        
        val defElemInfo : FnInfo =
          if(isCtor0)
            new ScalaSourceConstructorInfo
          else
            new ScalaSourceMethodInfo
        
        if(d.symbol.isGetter || d.symbol.isSetter) {
          elementInfo.getChildren.
            dropWhile(x => !x.isInstanceOf[ScalaFieldElement] || x.getElementName != d.name.toString).
            headOption match {
            case Some(f : ScalaFieldElement) => {
              val fInfo = f.getElementInfo.asInstanceOf[ScalaSourceFieldElementInfo]
              fInfo.setFlags0(mapModifiers(d.mods))
            }
            case _ =>
          }
        }
        
        defElemInfo.setArgumentNames(paramNames)
        defElemInfo.setExceptionTypeNames(new Array[Array[Char]](0))
        val tn = manager.intern(mapType(d.tpt).toArray)
        defElemInfo.asInstanceOf[FnInfo].setReturnType(tn)
        
        val mods =
          if(isTemplate)
            mapModifiers(d.mods)
          else
            ClassFileConstants.AccPrivate

        defElemInfo.setFlags0(mods)
        
        val start = d.pos.point
        val end = start+defElem.labelName.length-1
        
        defElemInfo.setNameSourceStart0(start)
        defElemInfo.setNameSourceEnd0(end)
        setSourceRange(defElemInfo, d)
        newElements0.put(defElem, defElemInfo)
        
        new Builder {
          val parent = self
          val element = defElem
          val elementInfo = defElemInfo

          override def isCtor = isCtor0
          override def addVal(v : ValDef) = this
          override def addType(t : TypeDef) = this
        }
      }
    }
    
    class CompilationUnitBuilder extends PackageOwner with ClassOwner with ModuleOwner {
      val parent = null
      val element = scu
      val elementInfo = unitInfo
      override def compilationUnitBuilder = this 
    }
    
    abstract class Builder extends PackageOwner with ClassOwner with ModuleOwner with ValOwner with TypeOwner with DefOwner
    
    def setSourceRange(info : ScalaMemberElementInfo, tree : Tree) {
      import Math.{ max, min }
      
      val start = tree.pos.startOrElse(-1)
      val end = tree.pos.endOrElse(start)
      
      info.setSourceRangeStart0(start)
      info.setSourceRangeEnd0(end)
    }
    
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
        //println("Template: "+parents)
        //traverseTrees(parents)
        //if (!self.isEmpty) traverse(self)
        traverseStats(body, tree.symbol)
      }
      case Function(vparams, body) => {
        //println("Anonymous function: "+tree.symbol.simpleName)
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
