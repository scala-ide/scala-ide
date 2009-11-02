/*
 * Copyright 2005-2009 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse.contribution.weaving.jdt.core;

import org.eclipse.jface.viewers.IStructuredSelection;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.eclipse.core.resources.IResource;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.internal.corext.refactoring.RefactoringAvailabilityTester;
import org.eclipse.jdt.internal.corext.refactoring.reorg.ReorgUtils;
import org.eclipse.jdt.internal.ui.refactoring.actions.RenameResourceAction;
import org.eclipse.jface.viewers.IStructuredSelection;

import scala.tools.eclipse.contribution.weaving.jdt.IScalaElement;

@SuppressWarnings("restriction")
public aspect RefactoringAvailabilityAspect {
  pointcut isAvailable(Object arg) :
    execution(static boolean RefactoringAvailabilityTester.is*Available(!IResource)) &&
    args(arg);

  pointcut isAvailable2(IResource[] arg1, IJavaElement[] arg2) :
    execution(static boolean RefactoringAvailabilityTester.is*Available(IResource[], IJavaElement[])) &&
    args(arg1, arg2);

  pointcut getResources(Object[] elements) :
    execution(static IResource[] RefactoringAvailabilityTester.getResources(Object[])) &&
    args(elements);

  pointcut getJavaElements(Object[] elements) :
    execution(static IJavaElement[] RefactoringAvailabilityTester.getJavaElements(Object[])) &&
    args(elements);
  
  pointcut getResources2(List elements) :
    execution(static IResource[] ReorgUtils.getResources(List)) &&
    args(elements);

  pointcut getJavaElements2(List elements) :
    execution(static IJavaElement[] ReorgUtils.getJavaElements(List)) &&
    args(elements);
  
  pointcut getResource(IStructuredSelection selection) :
    execution(static IResource RenameResourceAction.getResource(IStructuredSelection)) &&
    args(selection);

  boolean around(Object arg) :
    isAvailable(arg) {
    if (arg instanceof IScalaElement)
      return false;
    else if (arg instanceof IStructuredSelection)
      for(Iterator i = ((IStructuredSelection)arg).iterator(); i.hasNext();)
        if (i.next() instanceof IScalaElement)
          return false;
     
    return proceed(arg);
  }
  
  boolean around(IResource[] resources, IJavaElement[] elements) :
    isAvailable2(resources, elements) {
    if (elements == null || elements.length == 0)
      return proceed(resources, elements);
    else if (resources == null || resources.length == 0) {
      if (elements == null || elements.length == 0)
        return false;
      
      int limit = elements.length;
      IResource[] newResources = new IResource[limit];
      for (int i = 0; i < limit; ++i) {
        IJavaElement elem = elements[i]; 
        if (elem instanceof IResource)
          newResources[i] = (IResource)elem;
        else
          return false;
      }
      return proceed(newResources, null);
    }
    else {
      int limit = elements.length;
      IResource[] newResources = new IResource[resources.length+limit];
      System.arraycopy(resources, 0, newResources, 0, resources.length);
      for (int i = 0, j = resources.length; i < limit; ++i, ++j) {
        IJavaElement elem = elements[i]; 
        if (elem instanceof IResource)
          newResources[j] = (IResource)elem;
        else
          return false;
      }
      return proceed(newResources, null);
    }
  }
  
  IResource[] around(Object[] elements) :
    getResources(elements) {
    List result= new ArrayList();
    for (int index= 0; index < elements.length; index++) {
      Object elem = elements[index]; 
      if (elem instanceof IResource)
        result.add(elem);
      else if (elem instanceof IScalaElement) {
        try {
          IResource resource = ((IJavaElement)elem).getCorrespondingResource();
          if (resource != null)
            result.add(resource);
        } catch (JavaModelException ex) {
          // Deliberately ignored
        }
      }
    }
    return (IResource[]) result.toArray(new IResource[result.size()]);
  }

  IJavaElement[] around(Object[] elements) :
    getJavaElements(elements) {
    List result= new ArrayList();
    for (int index= 0; index < elements.length; index++) {
      Object elem = elements[index]; 
      if ((elem instanceof IJavaElement) && !(elem instanceof IScalaElement))
        result.add(elem);
    }
    return (IJavaElement[]) result.toArray(new IJavaElement[result.size()]);
  }
  
  IResource[] around(List elements) :
    getResources2(elements) {
    List result= new ArrayList();
    for (Iterator i = elements.iterator(); i.hasNext(); ) {
      Object elem = i.next(); 
      if (elem instanceof IResource)
        result.add(elem);
      else if (elem instanceof IScalaElement) {
        try {
          IResource resource = ((IJavaElement)elem).getCorrespondingResource();
          if (resource != null)
            result.add(resource);
        } catch (JavaModelException ex) {
          // Deliberately ignored
        }
      }
    }
    return (IResource[]) result.toArray(new IResource[result.size()]);
  }

  IJavaElement[] around(List elements) :
    getJavaElements2(elements) {
    List result= new ArrayList();
    for (Iterator i = elements.iterator(); i.hasNext(); ) {
      Object elem = i.next(); 
      if ((elem instanceof IJavaElement) && !(elem instanceof IScalaElement))
        result.add(elem);
    }
    return (IJavaElement[]) result.toArray(new IJavaElement[result.size()]);
  }

  IResource around(IStructuredSelection selection) :
    getResource(selection) {
    if (selection.size() != 1)
      return null;
    Object first= selection.getFirstElement();
    if (first instanceof IScalaElement) {
      try {
        return ((IJavaElement)first).getCorrespondingResource();
      } catch (JavaModelException ex) {
        return null;
      }
    }
    else if (!(first instanceof IResource))
      return null;
    return (IResource)first;
  }
}
