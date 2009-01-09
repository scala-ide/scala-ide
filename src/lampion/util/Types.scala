/*
 * Copyright 2005-2009 LAMP/EPFL
 * @author Sean McDirmid
 */
// $Id$

package lampion.util
import scala.collection.jcl._
import scala.collection.Set

/** memoize ad-hoc sub-type relationships */
trait Types {
  // a type is a set of symbols
  // if setA subsetOf setB, then setB is subtype of setA
  type Symbol
  
  
  def maskFor(sym : Symbol) = 1L << (sym.hashCode % 64)
  def maskFor(set : Set[Symbol]) : Long = set.foldLeft(0L)((x,y) => x | maskFor(y))

  private val types = new LinkedHashMap[Set[Symbol],Type] {
    override def default(set : Set[Symbol]) : Type = set match {
      case set : scala.collection.mutable.Set[t] => // safety
        default((new LinkedHashSet[Symbol] ++ set).readOnly)
      case set =>
        val ret = new Type(set)
        this(set) = ret 
        if (!set.isEmpty) root.insert(ret, new LinkedHashSet[Type])
        ret
    }
  }
  def typeFor(set : Set[Symbol]) : Type = types(set)

  // not inserted.
  case class PreType(set : Set[Symbol]) {
    val mask = maskFor(set)
  }
  final class Type private[Types] (set : Set[Symbol]) extends PreType(set) {
    override def toString = set.mkString("(", ",", ")")  
    private val subTypes = new LinkedHashMap[Symbol,Type]
    def allSubTypes = {
      val ret = new LinkedHashSet[Type]
      allSubTypes0(ret)
      ret.readOnly
    }
    private def allSubTypes0(set : LinkedHashSet[Type]) : Unit = subTypes.values.foreach{tpe =>
      if (set add tpe) tpe.allSubTypes0(set)
    }
    private[Types] def insert(tpe : Type, processed : LinkedHashSet[Type]) : Unit = if (processed add this) {
      tpe.set.elements.filter(!set.contains(_)).foreach{sym => subTypes.get(sym) match {
      case Some(other) if other == this => 
      case Some(other) if processed.contains(other) => 
      case Some(other) => 
        val inter = this * other
        if (inter == this) {
          assert(subTypes(sym) == other)
          // because we are kicking it out, we don't have to add it to our proessed
          tpe.set.elements.filter(!set.contains(_)).foreach{sym => subTypes.get(sym) match {
          case Some(`other`) => subTypes(sym) = this
          case Some(_) => // not yet
          case None =>
          }}
          tpe.insert(other, new LinkedHashSet[Type])
        } else { // other may no longer be there.
          assert(subTypes(sym) == inter)
          inter.insert(tpe, processed)
        }
      case None => subTypes(sym) = tpe
      }}
    }
    def *(other : Type) : Type = {
      assert((mask & other.mask) != 0)
      if (subTypeOf(other)) other
      else if (other.subTypeOf(this)) this
      else {
        val build = new LinkedHashSet[Symbol]
        build ++= set
        build.retainAll(other.set)
        typeFor(build.readOnly)
      }
    }
    def subTypeOf(other : Type) = {
      if ((mask & other.mask) == other.mask)
        set.subsetOf(other.set)
      else false
    }
    private[Types] def superTypes(what : PreType, results : LinkedHashSet[Type]) : Unit = {
      assert((this.mask & what.mask) == this.mask)
      results add this
      what.set.elements.filter(!set.contains(_)).foreach(subTypes.get(_) match {
      case Some(tpe) if ((tpe.mask & what.mask) == tpe.mask) && 
        !results.contains(tpe) && 
        tpe.set.subsetOf(what.set) && 
        tpe.set.size < what.set.size => tpe.superTypes(what, results)
      case _ =>
      })
    }
    def superTypes : Set[Type] = superTypesFor(set)
  }
  val root = typeFor((new LinkedHashSet[Symbol]).readOnly)
  def superTypesFor(set : Set[Symbol]) = {
    val results = new LinkedHashSet[Type]
    root.superTypes(PreType(set), results)
    results.readOnly
  }
}
