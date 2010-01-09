/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse.contribution.weaving.jdt.core;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.internal.core.IJavaElementRequestor;
import org.eclipse.jdt.internal.core.NameLookup;

import scala.tools.eclipse.contribution.weaving.jdt.IScalaCompilationUnit;

@SuppressWarnings("restriction")
public privileged aspect NameLookupAspect {
  pointcut seekTypesInSourcePackage(
    NameLookup nl,
    String name,
    IPackageFragment pkg,
    int firstDot,
    boolean partialMatch,
    String topLevelTypeName,
    int acceptFlags,
    IJavaElementRequestor requestor) :
    execution(void NameLookup.seekTypesInSourcePackage(
      String,
      IPackageFragment,
      int,
      boolean,
      String,
      int,
      IJavaElementRequestor)) &&
    target(nl) &&
    args(
      name,
      pkg,
      firstDot,
      partialMatch,
      topLevelTypeName,
      acceptFlags,
      requestor);
       
  void around(
    NameLookup nl,
    String name,
    IPackageFragment pkg,
    int firstDot,
    boolean partialMatch,
    String topLevelTypeName,
    int acceptFlags,
    IJavaElementRequestor requestor) :
      seekTypesInSourcePackage(
        nl,
        name,
        pkg,
        firstDot,
        partialMatch,
        topLevelTypeName,
        acceptFlags,
        requestor) {

    long start = -1;
    if (NameLookup.VERBOSE)
      start = System.currentTimeMillis();
    try {
      if (!partialMatch) {
        try {
          IJavaElement[] compilationUnits = pkg.getChildren();
          for (int i = 0, length = compilationUnits.length; i < length; i++) {
            if (requestor.isCanceled())
              return;
            IJavaElement cu = compilationUnits[i];
            String cuName = cu.getElementName();
            int lastDot = cuName.lastIndexOf('.');
            if (!(cu instanceof IScalaCompilationUnit) && (lastDot != topLevelTypeName.length() || !topLevelTypeName.regionMatches(0, cuName, 0, lastDot)))
              continue;
            IType type = ((ICompilationUnit) cu).getType(topLevelTypeName);
            type = nl.getMemberType(type, name, firstDot);
            if (nl.acceptType(type, acceptFlags, true/*a source type*/)) { // accept type checks for existence
              requestor.acceptType(type);
              break;  // since an exact match was requested, no other matching type can exist
            }
          }
        } catch (JavaModelException e) {
          // package doesn't exist -> ignore
        }
      } else {
        try {
          String cuPrefix = firstDot == -1 ? name : name.substring(0, firstDot);
          IJavaElement[] compilationUnits = pkg.getChildren();
          for (int i = 0, length = compilationUnits.length; i < length; i++) {
            if (requestor.isCanceled())
              return;
            IJavaElement cu = compilationUnits[i];
            if (!(cu instanceof IScalaCompilationUnit) && !cu.getElementName().toLowerCase().startsWith(cuPrefix))
              continue;
            try {
              IType[] types = ((ICompilationUnit) cu).getTypes();
              for (int j = 0, typeLength = types.length; j < typeLength; j++)
                nl.seekTypesInTopLevelType(name, firstDot, types[j], requestor, acceptFlags);
            } catch (JavaModelException e) {
              // cu doesn't exist -> ignore
            }
          }
        } catch (JavaModelException e) {
          // package doesn't exist -> ignore
        }
      }
    } finally {
      if (NameLookup.VERBOSE)
        nl.timeSpentInSeekTypesInSourcePackage += System.currentTimeMillis()-start;
    }
  }
}
