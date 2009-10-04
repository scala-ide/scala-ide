/*
 * Copyright 2005-2009 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse.contribution.weaving.jdt.hierarchy;

import java.util.ArrayList;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.internal.core.JavaElement;
import org.eclipse.jdt.internal.core.hierarchy.ChangeCollector;

import scala.tools.eclipse.contribution.weaving.jdt.IScalaElement;

@SuppressWarnings("restriction")
public privileged aspect HierarchyAspect {
  
  pointcut getAllTypesFromElement(ChangeCollector cc, JavaElement element, ArrayList allTypes) :
    execution(void ChangeCollector.getAllTypesFromElement(JavaElement, ArrayList)) &&
    args(element, allTypes) &&
    target(cc);
  
  void around(ChangeCollector cc, JavaElement element, ArrayList allTypes) throws JavaModelException :
    getAllTypesFromElement(cc, element, allTypes) {
    switch (element.getElementType()) {
      case IJavaElement.COMPILATION_UNIT:
        IType[] types = ((ICompilationUnit)element).getTypes();
        for (int i = 0, length = types.length; i < length; i++) {
          IType type = types[i];
          allTypes.add(type);
          cc.getAllTypesFromElement(type, allTypes);
        }
        break;
      case IJavaElement.TYPE:
        types = ((IType)element).getTypes();
        for (int i = 0, length = types.length; i < length; i++) {
          IType type = types[i];
          allTypes.add(type);
          cc.getAllTypesFromElement(type, allTypes);
        }
        break;
      case IJavaElement.INITIALIZER:
      case IJavaElement.FIELD:
      case IJavaElement.METHOD:
        IJavaElement[] children = ((IMember)element).getChildren();
        for (int i = 0, length = children.length; i < length; i++) {
          IJavaElement child = children[i];
          if (child instanceof IType) {
            IType type = (IType)child;
            allTypes.add(type);
            cc.getAllTypesFromElement(type, allTypes);
          } else if(child instanceof IScalaElement) {
            cc.getAllTypesFromElement(child, allTypes);
          }
        }
        break;
    }
  }
}
