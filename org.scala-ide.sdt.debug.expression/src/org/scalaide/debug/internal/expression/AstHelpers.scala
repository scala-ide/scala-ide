/*
 * Copyright (c) 2015 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression

import scala.reflect.runtime.universe._

import Names.Debugger._

trait AstHelpers {

  object ProxifiedPrimitive {
    def unapply(tree: Tree): Option[Literal] = tree match {
      case Apply(contextFun, List(literal: Literal)) if contextFun.toString() == s"$contextParamName.$proxyMethodName" => Some(literal)
      case literal: Literal => Some(literal)
      case _ => None
    }
  }

  protected def isPrimitive(tree: Tree): Boolean = TypeNames.fromTree(tree) match {
    case Some(treeType) => Names.Scala.primitives.allShorten.contains(treeType)
    case _ => ProxifiedPrimitive.unapply(tree).isDefined
  }

  /** Helper for creating Select on 'apply' method */
  protected def SelectApplyMethod(typeName: String): Select = SelectMethod(typeName, "apply")

  /** Helper for creating Select on given method */
  protected def SelectMethod(typeName: String, methodName: String): Select =
    Select(Ident(TermName(typeName)), TermName(methodName))

  object GenericSelect {
    def unapply(tree: Tree): Option[(Tree, Name)] = tree match {
      case Select(qualifier, name) => Some((qualifier, name))
      case TypeApply(Select(qualifier, name), _) => Some((qualifier, name))
      case _ => None
    }
  }

  object GenericApply {
    def unapply(tree: Tree): Option[(Tree, Name, List[Tree])] = tree match {
      case Apply(GenericSelect(qualifier, name), args) => Some((qualifier, name, args))
      case TypeApply(Apply(GenericSelect(qualifier, name), args), _) => Some((qualifier, name, args))
      case _ => None
    }
  }

  object GenericSelectOrApply {
    def unapply(tree: Tree): Option[(Tree, Name, List[Tree])] = tree match {
      case GenericApply(qualifier, name, args) => Some((qualifier, name, args))
      case GenericSelect(qualifier, name) => Some((qualifier, name, List()))
      case _ => None
    }
  }
}