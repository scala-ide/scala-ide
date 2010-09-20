/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse.javaelements

import java.io.{ PrintWriter, StringWriter }
import java.util.{ Map => JMap }

import org.eclipse.core.resources.IFile
import org.eclipse.jdt.core.{ IAnnotation, ICompilationUnit, IJavaElement, IMemberValuePair, Signature }
import org.eclipse.jdt.core.compiler.CharOperation
import org.eclipse.jdt.internal.core.{
  Annotation, AnnotationInfo => JDTAnnotationInfo, AnnotatableInfo, CompilationUnit => JDTCompilationUnit, ImportContainer,
  ImportContainerInfo, ImportDeclaration, ImportDeclarationElementInfo, JavaElement, JavaElementInfo, JavaModelManager,
  MemberValuePair, OpenableElementInfo, SourceRefElement }
import org.eclipse.jdt.internal.compiler.classfmt.ClassFileConstants

import scala.collection.Map
import scala.collection.mutable.HashMap

import scala.tools.nsc.symtab.Flags
import scala.tools.nsc.util.{ NoPosition, Position }

import scala.tools.eclipse.ScalaPresentationCompiler
import scala.tools.eclipse.util.ReflectionUtils

trait ScalaStructureBuilder { self : ScalaPresentationCompiler =>

  class StructureBuilderTraverser(scu : ScalaCompilationUnit, unitInfo : OpenableElementInfo, newElements0 : JMap[AnyRef, AnyRef], sourceLength : Int) {
    private var currentBuilder : Owner = new CompilationUnitBuilder
    private val manager = JavaModelManager.getJavaModelManager
    
    trait Owner {
      def parent : Owner
      def jdtOwner = this

      def element : JavaElement
      def elementInfo : JavaElementInfo
      def compilationUnitBuilder : CompilationUnitBuilder = parent.compilationUnitBuilder
      
      def isPackage = false
      def isCtor = false
      def isTemplate = false
      def template : Owner = if (parent != null) parent.template else null

      def addPackage(p : PackageDef) : Owner = this
      def addImport(i : Import) : Owner = this
      def addClass(c : ClassDef) : Owner = this
      def addModule(m : ModuleDef) : Owner = this
      def addVal(v : ValDef) : Owner = this
      def addType(t : TypeDef) : Owner = this
      def addDef(d : DefDef) : Owner = this
      def addFunction(f : Function) : Owner = this
      
      def resetImportContainer {}
      
      def addChild(child : JavaElement) =
        elementInfo match {
          case scalaMember : ScalaMemberElementInfo => scalaMember.addChild0(child)
          case openable : OpenableElementInfo => OpenableElementInfoUtils.addChild(openable, child)
          case _ =>
        }
      
      def modules : Map[Symbol, ScalaElementInfo] = Map.empty 
      def classes : Map[Symbol, (ScalaElement, ScalaElementInfo)] = Map.empty
      
      def complete {
        def addForwarders(classElem : ScalaElement, classElemInfo : ScalaElementInfo, module: Symbol) {
          def conflictsIn(cls: Symbol, name: Name) =
            cls.info.nonPrivateMembers.exists(_.name == name)
          
          /** List of parents shared by both class and module, so we don't add forwarders
           *  for methods defined there - bug #1804 */
          lazy val commonParents = {
            val cps = module.info.baseClasses
            val mps = module.companionClass.info.baseClasses
            cps.filter(mps contains)
          }
          /* the setter doesn't show up in members so we inspect the name */
          def conflictsInCommonParent(name: Name) =
            commonParents exists { cp => name startsWith (cp.name + "$") }
                 
          /** Should method `m' get a forwarder in the mirror class? */
          def shouldForward(m: Symbol): Boolean =
            atPhase(currentRun.picklerPhase) (
              m.owner != definitions.ObjectClass 
              && m.isMethod
              && !m.hasFlag(Flags.CASE | Flags.PROTECTED | Flags.DEFERRED)
              && !m.isConstructor
              && !m.isStaticMember
              && !(m.owner == definitions.AnyClass) 
              && !module.isSubClass(module.companionClass)
              && !conflictsIn(definitions.ObjectClass, m.name)
              && !conflictsInCommonParent(m.name)
              && !conflictsIn(module.companionClass, m.name)
            )
          
          assert(module.isModuleClass)
          
          for (m <- module.info.nonPrivateMembers; if shouldForward(m))
            addForwarder(classElem, classElemInfo, module, m)
        }

        def addForwarder(classElem: ScalaElement, classElemInfo : ScalaElementInfo, module: Symbol, d: Symbol) {
          //val moduleName = javaName(module) // + "$"
          //val className = moduleName.substring(0, moduleName.length() - 1)

          val nm = d.name
          
          val fps = for(vps <- d.tpe.paramss; vp <- vps) yield vp
          
          def paramType(sym : Symbol) = {
            val tpe = sym.tpe
            if (sym.isType || tpe != null)
              uncurry.transformInfo(sym, tpe).typeSymbol
            else {
              NoSymbol
            }
          }
          
          val paramTypes = Array(fps.map(v => Signature.createTypeSignature(mapType(paramType(v)), false)) : _*)
          val paramNames = Array(fps.map(n => nme.getterName(n.name).toString.toArray) : _*)
          
          val defElem = 
            if(d.hasFlag(Flags.ACCESSOR))
              new ScalaAccessorElement(classElem, nm.toString, paramTypes)
            else
              new ScalaDefElement(classElem, nm.toString, paramTypes, true, nm.toString)
          resolveDuplicates(defElem)
          classElemInfo.addChild0(defElem)
          
          val defElemInfo = new ScalaSourceMethodInfo
          
          defElemInfo.setArgumentNames(paramNames)
          defElemInfo.setExceptionTypeNames(new Array[Array[Char]](0))
          val tn = manager.intern(mapType(d.tpe.finalResultType.typeSymbol).toArray)
          defElemInfo.asInstanceOf[FnInfo].setReturnType(tn)
  
          val annotsPos = addAnnotations(d, defElemInfo, defElem)
  
          defElemInfo.setFlags0(ClassFileConstants.AccPublic|ClassFileConstants.AccFinal|ClassFileConstants.AccStatic)
          
          val (start, point, end) =
            d.pos match {
              case NoPosition =>
                (module.pos.point, module.pos.point, module.pos.point)
              case pos =>
                (d.pos.startOrPoint, d.pos.point, d.pos.endOrPoint)
            }
            
          val nameEnd = point+defElem.labelName.length-1
            
          defElemInfo.setNameSourceStart0(point)
          defElemInfo.setNameSourceEnd0(nameEnd)
          defElemInfo.setSourceRangeStart0(start)
          defElemInfo.setSourceRangeEnd0(end)
          
          newElements0.put(defElem, defElemInfo)
        } 
        
        for ((m, mInfo) <- modules) {
          val c = m.companionClass
          if (c != NoSymbol) {
            classes.get(c) match {
              case Some((classElem, classElemInfo)) =>
                addForwarders(classElem, classElemInfo, m.moduleClass)
              case _ =>
            }
          } else {
            val className = m.nameString
            
            val classElem = new ScalaClassElement(element, className, true)
            resolveDuplicates(classElem)
            addChild(classElem)
            
            val classElemInfo = new ScalaElementInfo
            classElemInfo.setHandle(classElem)
            classElemInfo.setFlags0(ClassFileConstants.AccSuper|ClassFileConstants.AccFinal|ClassFileConstants.AccPublic)
            classElemInfo.setSuperclassName("java.lang.Object".toArray)
            classElemInfo.setSuperInterfaceNames(null)
            classElemInfo.setNameSourceStart0(mInfo.getNameSourceStart)
            classElemInfo.setNameSourceEnd0(mInfo.getNameSourceEnd)
            classElemInfo.setSourceRangeStart0(mInfo.getDeclarationSourceStart)
            classElemInfo.setSourceRangeEnd0(mInfo.getDeclarationSourceEnd)
            
            newElements0.put(classElem, classElemInfo)
            
            addForwarders(classElem, classElemInfo, m.moduleClass)
          }
        }
      }
    }
    
    trait PackageOwner extends Owner { self =>
      override def addPackage(p : PackageDef) : Owner = {
        //println("Package defn: "+p.name+" ["+this+"]")
        
        new Builder {
          val parent = self
          val element = compilationUnitBuilder.element
          val elementInfo = compilationUnitBuilder.elementInfo
          
          override def isPackage = true
          var completed = !compilationUnitBuilder.element.isInstanceOf[JDTCompilationUnit]
          override def addChild(child : JavaElement) = {
            if (!completed) {
              completed = true
              
              val pkgElem = JavaElementFactory.createPackageDeclaration(compilationUnitBuilder.element.asInstanceOf[JDTCompilationUnit], p.symbol.fullName)
              resolveDuplicates(pkgElem)
              compilationUnitBuilder.addChild(pkgElem)

              val pkgElemInfo = JavaElementFactory.createSourceRefElementInfo
              newElements0.put(pkgElem, pkgElemInfo)
            }
            
            compilationUnitBuilder.addChild(child)
          }
        }
      }
    }
    
    trait ImportContainerOwner extends Owner { self =>
      import SourceRefElementInfoUtils._
      import ImportContainerInfoUtils._
    
      var currentImportContainer : Option[(ImportContainer, ImportContainerInfo)] = None
      
      override def resetImportContainer : Unit = currentImportContainer = None
      
      override def addImport(i : Import) : Owner = {
        //println("Import "+i)
        val prefix = i.expr.symbol.fullName
        val pos = i.pos

        def isWildcard(s: ImportSelector) : Boolean = s.name == nme.WILDCARD

        def addImport(name : String, isWildcard : Boolean) {
          val path = prefix+"."+(if(isWildcard) "*" else name)
          
          val (importContainer, importContainerInfo) = currentImportContainer match {
            case Some(ci) => ci
            case None =>
              val importContainerElem = JavaElementFactory.createImportContainer(element)
              val importContainerElemInfo = new ImportContainerInfo
                
              resolveDuplicates(importContainerElem)
              addChild(importContainerElem)
              newElements0.put(importContainerElem, importContainerElemInfo)
              
              val ci = (importContainerElem, importContainerElemInfo)
              currentImportContainer = Some(ci)
              ci
          }
          
          val importElem = JavaElementFactory.createImportDeclaration(importContainer, path, isWildcard)
          resolveDuplicates(importElem)
        
          val importElemInfo = new ImportDeclarationElementInfo
          setSourceRangeStart(importElemInfo, pos.startOrPoint)
          setSourceRangeEnd(importElemInfo, pos.endOrPoint-1)
        
          val children = getChildren(importContainerInfo)
          if (children.isEmpty)
            setChildren(importContainerInfo, Array[IJavaElement](importElem))
          else
            setChildren(importContainerInfo, children ++ Seq(importElem))
            
          newElements0.put(importElem, importElemInfo)
        }

        i.selectors.foreach(s => addImport(s.name.toString, isWildcard(s)))
        
        self
      }
    }
    
    trait ClassOwner extends Owner { self =>
      override val classes = new HashMap[Symbol, (ScalaElement, ScalaElementInfo)]
    
      override def addClass(c : ClassDef) : Owner = {
        //println("Class defn: "+c.name+" ["+this+"]")
        //println("Parents: "+c.impl.parents)
        
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

        val classElem =
          if(c.mods.isTrait)
            new ScalaTraitElement(element, name)
          else if (isAnon) {
            val primaryTypeString = if (primaryType != null) primaryType.name.toString else null
            new ScalaAnonymousClassElement(element, primaryTypeString)
          }
          else
            new ScalaClassElement(element, name, false)
        
        resolveDuplicates(classElem)
        addChild(classElem)
        
        val classElemInfo = new ScalaElementInfo
        classes(c.symbol) = (classElem, classElemInfo)
        
        classElemInfo.setHandle(classElem)
        val mask = ~(if (isAnon) ClassFileConstants.AccPublic else 0)
        classElemInfo.setFlags0(mapModifiers(c.mods) & mask)
        
        val annotsPos = addAnnotations(c.symbol, classElemInfo, classElem)

        classElemInfo.setSuperclassName(superclassName)
        
        val interfaceTypes = interfaceTrees.map(t => (t, t.tpe))
        val interfaceNames = interfaceTypes.map({ case (tree, tpe) => (if (tpe ne null) tpe.typeSymbol.fullName else "null-"+tree).toCharArray })
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
        setSourceRange(classElemInfo, c, annotsPos)
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
      override val modules = new HashMap[Symbol, ScalaElementInfo]

      override def addModule(m : ModuleDef) : Owner = {
        //println("Module defn: "+m.name+" ["+this+"]")
        
        val isSynthetic = m.symbol.hasFlag(Flags.SYNTHETIC)
        val moduleElem = new ScalaModuleElement(element, m.name.toString, isSynthetic)
        resolveDuplicates(moduleElem)
        addChild(moduleElem)
        
        val moduleElemInfo = new ScalaElementInfo
        if (m.symbol.owner.isPackageClass)
          modules(m.symbol) = moduleElemInfo
        
        moduleElemInfo.setHandle(moduleElem)
        moduleElemInfo.setFlags0(mapModifiers(m.mods)|ClassFileConstants.AccFinal)
        
        val annotsPos = addAnnotations(m.symbol, moduleElemInfo, moduleElem)

        val start = m.pos.point
        val end = start+m.name.length-1
        
        moduleElemInfo.setNameSourceStart0(start)
        moduleElemInfo.setNameSourceEnd0(end)
        if (!isSynthetic)
          setSourceRange(moduleElemInfo, m, annotsPos)
        else {
          moduleElemInfo.setSourceRangeStart0(end)
          moduleElemInfo.setSourceRangeEnd0(end)
        }
        newElements0.put(moduleElem, moduleElemInfo)
        
        val parentTree = m.impl.parents.head
        val superclassType = parentTree.tpe
        val superclassName = (if (superclassType ne null) superclassType.typeSymbol.fullName else "null-"+parentTree).toCharArray
        moduleElemInfo.setSuperclassName(superclassName)
        
        val interfaceTrees = m.impl.parents.drop(1)
        val interfaceTypes = interfaceTrees.map(t => (t, t.tpe))
        val interfaceNames = interfaceTypes.map({ case (tree, tpe) => (if (tpe ne null) tpe.typeSymbol.fullName else "null-"+tree).toCharArray })
        moduleElemInfo.setSuperInterfaceNames(interfaceNames.toArray)
        
        val mb = new Builder {
          val parent = self
          val element = moduleElem
          val elementInfo = moduleElemInfo
          
          override def isTemplate = true
          override def template = this
        }

        val instanceElem = new ScalaModuleInstanceElement(moduleElem)
        resolveDuplicates(instanceElem)
        mb.addChild(instanceElem)
        
        val instanceElemInfo = new ScalaSourceFieldElementInfo
        instanceElemInfo.setFlags0(ClassFileConstants.AccPublic | ClassFileConstants.AccStatic | ClassFileConstants.AccFinal)
        instanceElemInfo.setTypeName(moduleElem.getFullyQualifiedName('.').toCharArray)
        setSourceRange(instanceElemInfo, m, annotsPos)
        instanceElemInfo.setNameSourceStart0(start)
        instanceElemInfo.setNameSourceEnd0(end)
        
        newElements0.put(instanceElem, instanceElemInfo)
        
        mb
      }
    }
    
    trait ValOwner extends Owner { self =>
      override def addVal(v : ValDef) : Owner = {
        //println("Val defn: >"+nme.getterName(v.name)+"< ["+this+"]")
        
        val elemName = nme.getterName(v.name)
        val display = elemName.toString+" : "+v.symbol.tpe.toString
        
        val valElem =
          if(v.mods.hasFlag(Flags.MUTABLE))
            new ScalaVarElement(element, elemName.toString, display)
          else
            new ScalaValElement(element, elemName.toString, display)
        resolveDuplicates(valElem)
        addChild(valElem)
        
        val valElemInfo = new ScalaSourceFieldElementInfo
        val jdtFinal = if(v.mods.hasFlag(Flags.MUTABLE)) 0 else ClassFileConstants.AccFinal
        valElemInfo.setFlags0(mapModifiers(v.mods)|jdtFinal)
        
        val annotsPos = addAnnotations(v.symbol, valElemInfo, valElem)
        
        val start = v.pos.point
        val end = start+elemName.length-1
        
        valElemInfo.setNameSourceStart0(start)
        valElemInfo.setNameSourceEnd0(end)
        setSourceRange(valElemInfo, v, annotsPos)
        newElements0.put(valElem, valElemInfo)

        val tn = manager.intern(mapType(v.tpt).toArray)
        valElemInfo.setTypeName(tn)
        
        new Builder {
          val parent = self
          val element = valElem
          val elementInfo = valElemInfo
          
          override def addVal(v: ValDef) = this
          override def addType(t : TypeDef) = this
        }
      }
    }
    
    trait TypeOwner extends Owner { self =>
      override def addType(t : TypeDef) : Owner = {
        //println("Type defn: >"+t.name.toString+"< ["+this+"]")
        
        val display = t.name.toString+" : "+t.symbol.tpe.toString

        val typeElem = new ScalaTypeElement(element, t.name.toString, display)
        resolveDuplicates(typeElem)
        addChild(typeElem)

        val typeElemInfo = new ScalaSourceFieldElementInfo
        typeElemInfo.setFlags0(mapModifiers(t.mods))

        val annotsPos = addAnnotations(t.symbol, typeElemInfo, typeElem)
        
        val start = t.pos.point
        val end = start+t.name.length-1

        typeElemInfo.setNameSourceStart0(start)
        typeElemInfo.setNameSourceEnd0(end)
        setSourceRange(typeElemInfo, t, annotsPos)
        newElements0.put(typeElem, typeElemInfo)
        
        if(t.rhs.symbol == NoSymbol) {
          //println("Type is abstract")
          val tn = manager.intern("java.lang.Object".toArray)
          typeElemInfo.setTypeName(tn)
        } else {
          //println("Type has type: "+t.rhs.symbol.fullName)
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
        val isCtor0 = d.symbol.isConstructor
        val nameString =
          if(isCtor0)
            d.symbol.owner.simpleName + (if (d.symbol.owner.isModuleClass) "$" else "")
          else
            d.name.toString
        
        val fps = for(vps <- d.vparamss; vp <- vps) yield vp
        
        def paramType(v : ValDef) = {
          val sym = v.symbol
          val tpt = v.tpt
          val tpe = tpt.tpe
          if (sym.isType || tpe != null)
            uncurry.transformInfo(sym, tpe).typeSymbol
          else {
            NoSymbol
          }
        }
        
        val paramTypes = Array(fps.map(v => Signature.createTypeSignature(mapType(paramType(v)), false)) : _*)
        val paramNames = Array(fps.map(n => nme.getterName(n.name).toString.toArray) : _*)
        
        val sw = new StringWriter
        val tp = newTreePrinter(new PrintWriter(sw))
        tp.print(tp.symName(d, d.name))
        tp.printTypeParams(d.tparams)
        d.vparamss foreach tp.printValueParams
        sw.write(" : ")
        tp.print(d.tpt)
        tp.flush
        val display = sw.toString
        
        val defElem = 
          if(d.mods.isAccessor)
            new ScalaAccessorElement(element, nameString, paramTypes)
          else if(isTemplate)
            new ScalaDefElement(element, nameString, paramTypes, d.symbol.hasFlag(Flags.SYNTHETIC), display)
          else
            new ScalaFunctionElement(template.element, element, nameString, paramTypes, display)
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

        val annotsPos = addAnnotations(d.symbol, defElemInfo, defElem)

        val mods =
          if(isTemplate)
            mapModifiers(d.mods)
          else
            ClassFileConstants.AccPrivate

        defElemInfo.setFlags0(mods)
        
        if (isCtor0) {
          elementInfo match {
            case smei : ScalaMemberElementInfo =>
              defElemInfo.setNameSourceStart0(smei.getNameSourceStart0)
              defElemInfo.setNameSourceEnd0(smei.getNameSourceEnd0)
              if (d.symbol.isPrimaryConstructor) {
                defElemInfo.setSourceRangeStart0(smei.getNameSourceEnd0)
                defElemInfo.setSourceRangeEnd0(smei.getDeclarationSourceEnd0)
              } else {
                defElemInfo.setSourceRangeStart0(smei.getDeclarationSourceStart0)
                defElemInfo.setSourceRangeEnd0(smei.getDeclarationSourceEnd0)
              }
            case _ =>
          }
        } else {
          val start = d.pos.point
          val end = start+defElem.labelName.length-1-(if (d.symbol.isSetter) 4 else 0)
          
          defElemInfo.setNameSourceStart0(start)
          defElemInfo.setNameSourceEnd0(end)
          setSourceRange(defElemInfo, d, annotsPos)
        }
        
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
      
    def resolveDuplicates(handle : SourceRefElement) {
      while (newElements0.containsKey(handle)) {
        handle.occurrenceCount += 1
      }
    }
    
    def addAnnotations(sym : Symbol, parentInfo : AnnotatableInfo, parentHandle : JavaElement) : Position =
      addAnnotations(try { sym.annotations } catch { case _ => Nil }, parentInfo, parentHandle)
    
    def addAnnotations(annots : List[AnnotationInfo], parentInfo : AnnotatableInfo, parentHandle : JavaElement) : Position = {
      import SourceRefElementInfoUtils._
      import JDTAnnotationUtils._
      
      def getMemberValuePairs(owner : JavaElement, memberValuePairs : List[(Name, ClassfileAnnotArg)]) : Array[IMemberValuePair] = {
        def getMemberValue(value : ClassfileAnnotArg) : (Int, Any) = {
          value match {
            case LiteralAnnotArg(const) => 
              const.tag match {
                case BooleanTag => (IMemberValuePair.K_BOOLEAN, const.booleanValue)
                case ByteTag    => (IMemberValuePair.K_BYTE, const.byteValue)
                case ShortTag   => (IMemberValuePair.K_SHORT, const.shortValue)
                case CharTag    => (IMemberValuePair.K_CHAR, const.charValue)
                case IntTag     => (IMemberValuePair.K_INT, const.intValue)
                case LongTag    => (IMemberValuePair.K_LONG, const.longValue)
                case FloatTag   => (IMemberValuePair.K_FLOAT, const.floatValue)
                case DoubleTag  => (IMemberValuePair.K_DOUBLE, const.doubleValue)
                case StringTag  => (IMemberValuePair.K_STRING, const.stringValue)
                case ClassTag   => (IMemberValuePair.K_CLASS, const.typeValue.typeSymbol.fullName)
                case EnumTag    => (IMemberValuePair.K_QUALIFIED_NAME, const.tpe.typeSymbol.fullName+"."+const.symbolValue.name.toString)
              }
            case ArrayAnnotArg(args) =>
              val taggedValues = args.map(getMemberValue)
              val firstTag = taggedValues.head._1
              val tag = if (taggedValues.exists(_._1 != firstTag)) IMemberValuePair.K_UNKNOWN else firstTag
              val values = taggedValues.map(_._2)
              (tag, values)
            case NestedAnnotArg(annInfo) =>
              (IMemberValuePair.K_ANNOTATION, addAnnotations(List(annInfo), null, owner))
          }
        }
        
        for ((name, value) <- memberValuePairs.toArray) yield { 
          val (kind, jdtValue) = getMemberValue(value)
          new MemberValuePair(name.toString, jdtValue, kind)
        }
      }

      annots.foldLeft(NoPosition : Position) { (pos, annot) => {
        if (!annot.pos.isOpaqueRange)
          pos
        else {
          //val nameString = annot.atp.typeSymbol.fullName
          val nameString = annot.atp.typeSymbol.nameString
          val handle = new Annotation(parentHandle, nameString)
          resolveDuplicates(handle)
  
          val info = new JDTAnnotationInfo
          newElements0.put(handle, info)
  
          info.nameStart = annot.pos.startOrPoint
          info.nameEnd = annot.pos.endOrPoint-1
          setSourceRangeStart(info, info.nameStart-1)
          setSourceRangeEnd(info, info.nameEnd)
  
          val memberValuePairs = annot.assocs
          val membersLength = memberValuePairs.length
          if (membersLength == 0)
            info.members = Annotation.NO_MEMBER_VALUE_PAIRS
          else
            info.members = getMemberValuePairs(handle, memberValuePairs)
        
          if (parentInfo != null) {
            val annotations0 = getAnnotations(parentInfo)
            val length = annotations0.length
            val annotations = new Array[IAnnotation](length+1)
            Array.copy(annotations0, 0, annotations, 0, length)
            annotations(length) = handle
            setAnnotations(parentInfo, annotations)
          }
          
          annot.pos union pos
        }
      }}
    }
    
    class CompilationUnitBuilder extends PackageOwner with ImportContainerOwner with ClassOwner with ModuleOwner {
      val parent = null
      val element = scu
      val elementInfo = unitInfo
      override def compilationUnitBuilder = this 
    }
    
    abstract class Builder extends PackageOwner with ImportContainerOwner with ClassOwner with ModuleOwner with ValOwner with TypeOwner with DefOwner
    
    def setSourceRange(info : ScalaMemberElementInfo, tree : Tree, annotsPos : Position) {
      import Math.{ max, min }
      
      val pos = tree.pos
      val (start, end) =
        if (pos.isDefined) {
          val pos0 = if (annotsPos.isOpaqueRange) pos union annotsPos else pos
          val start0 = if (tree.symbol == NoSymbol)
            pos0.startOrPoint
          else
            try {
              docCommentPos(tree.symbol) match {
                case NoPosition => pos0.startOrPoint
                case cpos => cpos.startOrPoint
              }
            } catch {
              case _ => pos0.startOrPoint
            }
          (start0, pos0.endOrPoint-1)
        }
        else
          (-1, -1)
      
      info.setSourceRangeStart0(start)
      info.setSourceRangeEnd0(end)
    }
    
    def traverse(tree : Tree) {
      val builder = new CompilationUnitBuilder
      traverse(tree, builder)
      builder.complete
    }

    private def traverse(tree: Tree, builder : Owner) : Unit = {
      val (newBuilder, children) = ask{ () =>
        tree match {
          case _ : Import => ()
          case _ => builder.resetImportContainer
        }

        tree match {
          case pd : PackageDef => { () => (builder.addPackage(pd), pd.stats) }
          case i : Import => { () => (builder.addImport(i), Nil) }
          case cd : ClassDef => { () => (builder.addClass(cd), List(cd.impl)) }
          case md : ModuleDef => { () => (builder.addModule(md), List(md.impl)) }
          case vd : ValDef => { () => (builder.addVal(vd), List(vd.rhs)) }
          case dd : DefDef if dd.name != nme.MIXIN_CONSTRUCTOR => { () => (builder.addDef(dd), List(dd.tpt, dd.rhs)) }
          case t : Template => { () => (builder, t.body) }
          case f : Function => { () => (builder, Nil) }
          case _ => { () => (builder, tree.children) } 
        }
      }()

      children.foreach( traverse(_, newBuilder) )
      if (builder != newBuilder) ask { () => newBuilder.complete }
    }
  }
}

object JDTAnnotationUtils extends ReflectionUtils {
  val aiClazz = classOf[AnnotatableInfo]
  val annotationsField = getDeclaredField(aiClazz, "annotations")

  def getAnnotations(ai : AnnotatableInfo) = annotationsField.get(ai).asInstanceOf[Array[IAnnotation]]
  def setAnnotations(ai : AnnotatableInfo, annotations : Array[IAnnotation]) = annotationsField.set(ai, annotations)
}
