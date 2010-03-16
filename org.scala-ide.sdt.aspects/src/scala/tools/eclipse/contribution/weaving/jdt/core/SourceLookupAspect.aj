/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse.contribution.weaving.jdt.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.sourcelookup.AbstractSourceLookupDirector;

public aspect SourceLookupAspect {
  pointcut findSourceElements(Object object) :
    execution(Object[] AbstractSourceLookupDirector.findSourceElements(Object)) &&
    args(object);
  
  Object[] around(Object object) throws CoreException :
    findSourceElements(object) {
    if (object instanceof String) {
      String sourceFile = (String)object;
      if (sourceFile.endsWith(".java")) {
        String scalaSourceFile = sourceFile.substring(0, sourceFile.length()-5)+".scala";
        List result = new ArrayList();
        Object[] scalaResults = proceed(scalaSourceFile);
        Object[] javaResults = proceed(sourceFile);
        result.addAll(Arrays.asList(scalaResults));
        result.addAll(Arrays.asList(javaResults));
        return result.toArray();
      }
    }
    
    return proceed(object);
  }
}
