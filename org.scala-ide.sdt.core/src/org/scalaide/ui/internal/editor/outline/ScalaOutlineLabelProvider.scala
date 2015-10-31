package org.scalaide.ui.internal.editor.outline

import org.eclipse.jface.viewers.ILabelProvider
import org.eclipse.swt.graphics.Image
import org.eclipse.jface.viewers.ILabelProviderListener
import org.scalaide.core.internal.ScalaPlugin

class ScalaOutlineLabelProvider extends ILabelProvider {

  override def getImage(o: Object): Image = {
    import org.scalaide.ui.ScalaImages._

    val reg = ScalaPlugin().imageDescriptorRegistry
    o match {
      case n: ClassNode => if (n.isTrait) reg.get(SCALA_TRAIT) else reg.get(SCALA_CLASS)
      case n: TypeNode => reg.get(SCALA_TYPE)
      case n: ValNode => if (n.isPrivate) reg.get(PRIVATE_VAL) else if (n.isProtected) reg.get(PROTECTED_VAL) else reg.get(PUBLIC_VAL)
      case n: VarNode => if (n.isPrivate) reg.get(PRIVATE_VAR) else if (n.isProtected) reg.get(PROTECTED_VAR) else reg.get(PUBLIC_VAR)
      case n: MethodNode =>
        if (n.parent.isInstanceOf[MethodNode]) reg.get(DESC_INNER_METHOD)
        else if (n.isPrivate) reg.get(PRIVATE_DEF) else if (n.isProtected) reg.get(PROTECTED_DEF) else reg.get(PUBLIC_DEF)
      case n: ObjectNode => reg.get(SCALA_OBJECT)
      case n: PackageNode => reg.get(PACKAGE)
      case n: ImportsNode => reg.get(DESC_OBJS_IMPCONT)
      case n: ImportNode => reg.get(DESC_OBJS_IMPDECL)
      case _ => null
    }
  }

  override def getText(o: Object): String = {
    o match {
      case c: MethodNode =>
        val sb = new StringBuilder
        def renderArgList() = {
          c.argTypes.foreach(list => {
            sb.append("(")
            list.foreach { s => sb.append(s); sb.append(", ") }
            if (!list.isEmpty)
              sb.setLength(sb.length - 2)
            sb.append(")")
          })
        }
        sb.append(c.name)
        sb.append(c.typePar)
        renderArgList
        sb.append(c.returnType.map(": " + _).getOrElse(""))
        sb.toString
      case c: ClassNode => c.name + c.typePar
      case c: ValNode => c.name + c.returnType.map(": " + _).getOrElse("")
      case c: VarNode => c.name + c.returnType.map(": " + _).getOrElse("")
      case c: Node => c.name
      case _ => ""
    }

  }

  override def addListener(arg0: ILabelProviderListener) = {

  }

  override def dispose() = {

  }

  override def isLabelProperty(arg0: Object, arg1: String): Boolean = {
    false
  }

  override def removeListener(arg0: ILabelProviderListener) = {

  }
}