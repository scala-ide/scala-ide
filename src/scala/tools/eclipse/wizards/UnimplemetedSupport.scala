/*
 * Copyright 2010 LAMP/EPFL
 * 
 * @author Tim Clendenen
 * 
 */
package scala.tools.eclipse.wizards

import org.eclipse.jdt.core.{ Flags, IMethod, IType, ITypeHierarchy, Signature }

import scala.reflect.NameTransformer
import scala.collection.mutable.ListBuffer
	
trait UnimplemetedSupport extends QualifiedNameSupport with BufferSupport {
	
  def unimplemetedConstructors(typeHierarchy: ITypeHierarchy, 
		  newType: IType): Unit
		  
  def unimplemetedMethods(typeHierarchy: ITypeHierarchy, newType: IType): Unit
}

object UnimplemetedSupport {
	
  import CodeBuilder._
	
  def apply(imports: ImportSupport, superTypes: List[String]): UnimplemetedSupport = {
    new UnimplemetedSupportImpl(imports, superTypes)
  } 
      
  private val modifiers = (k: IMethod) => {
    val s = Flags.toString(k.getFlags & ~Flags.AccPublic & ~Flags.AccAbstract)
    toOption(s, {s.length > 0}) map (_ + " ")
  }
  
  private val elementName = (k: IMethod) => toOption(k.getElementName) 
  
  private val returnType = (k: IMethod) => toOption(k.getReturnType)
  
  private val returnValue = (k: IMethod) => toOption(resultValue(k.getReturnType))

  private def similarMethod(ma: IMethod, xs: ListBuffer[IMethod]): Boolean = {
    def similar(m: IMethod) = {
      ma.getElementName == m.getElementName &&
      ma.getNumberOfParameters == m.getNumberOfParameters &&
      (ma.getParameterTypes.mkString == m.getParameterTypes.mkString ||
       ma.getParameterNames.mkString == m.getParameterNames.mkString)
    }
    xs exists similar
  }
  
  private val constructorIMethodOrdering = new math.Ordering[IMethod] {
    def compare(x: IMethod, y: IMethod) = {
      (x.getNumberOfParameters, y.getNumberOfParameters) match {
        case (l, r) if l > r => -1
        case (l, r) if l < r =>  1
        case _ =>  0
      }
    }
  }

  private class UnimplemetedSupportImpl(val imports: ImportSupport, 
		                                superTypes: List[String]) 
  	extends UnimplemetedSupport 
  	with CodeBuilder {
	
	private val allSuperTypes = superTypes map createSuperType
	
	private val generatedMethods: ListBuffer[String] = ListBuffer()
	private val generatedConstructors: ListBuffer[String] = ListBuffer()
	
	protected def contents(implicit ld: String) = 
	  (generatedConstructors map (s => ld+"  " +s+ld) 
	    ++= generatedMethods map (s => ld+"  " +s+ld)) mkString
	  
	def unimplemetedConstructors(typeHierarchy: ITypeHierarchy, newType: IType) {
		
	  val astc: ListBuffer[IMethod] = ListBuffer()
	  
	  typeHierarchy.getSuperclass(newType).getMethods.foreach { scm =>
	    if(scm.isConstructor)
	      astc += scm
	  }
	   
	  val sastc = astc.sorted(constructorIMethodOrdering)
	  for {
	 	stc <- sastc.headOption
	     pn =  stc.getParameterNames map (pn => Name(pn))
	     pt =  stc.getParameterTypes map (pt => Type(Name(convertAndAdd(pt))))
	     nt =  pn zip pt map (nt => Arg(nt._1, nt._2))
	     ag =  Args(nt.toList)
	  } addConstructorArgs(ag)
	   
	  for {
	    stc <- sastc
	     pn =  stc.getParameterNames.map (pn => Name(pn)).toList
	     pt =  stc.getParameterTypes map (pt => Type(Name(convertAndAdd(pt))))
	     nt =  pn zip pt map (nt => Arg(nt._1, nt._2))
	     if(nt.nonEmpty)
	     ag =  Args(nt.init.toList)
	     pl =  ParamNames(pn.dropRight(1) :+ Name("null"))
	  } generatedConstructors + eval(AuxCons(ag, ConsBody(pl)))
	}

	def unimplemetedMethods(typeHierarchy: ITypeHierarchy, newType: IType) {
	   
	  def superTypeMethods(theType: IType) {
	    val astm: ListBuffer[IMethod] = ListBuffer()
	    val istm: ListBuffer[IMethod] = ListBuffer()
	    typeHierarchy getAllSupertypes(newType) foreach { st =>
	      st.getMethods.foreach { stm =>
	        if(Flags.isAbstract(stm.getFlags) && !similarMethod(stm, astm)
	        		                          && !similarMethod(stm, istm))
	          astm += stm
	        else 
	          istm += stm
	      }
	    }
	    
	    for {
	      stm <- astm
	       md =  Mods(modifiers(stm))
	       nm =  Name(NameTransformer.decode(elementName(stm).get))
	       tp =  stm.getTypeParameters map (tp => TypeParam(Name(tp.getElementName),TypeBounds()))
	       ts =  TypeParams(toOption(tp.toList, tp.length > 0))
	       pn =  stm.getParameterNames map (pn => Name(pn))
	       pt =  stm.getParameterTypes map (pt => Type(Name(convertAndAdd(pt))))
	       nt =  pn zip pt map (nt => Arg(nt._1, nt._2))
	       ag =  Args(nt.toList)
	        r =  Result(Type(Name(convertAndAdd(returnType(stm).get))), Value(returnValue(stm).get))
	    } generatedMethods + eval(Func(md,nm,ts,ag,r))
	  }
	  superTypeMethods(newType)
	}
  }
}
