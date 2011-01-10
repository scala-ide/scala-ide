/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse.contribution.weaving.jdt.hierarchy;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.internal.core.hierarchy.ChangeCollector;
import org.eclipse.jdt.internal.ui.javaeditor.JavaSelectAnnotationRulerAction;
import org.eclipse.jdt.internal.ui.javaeditor.JavaSelectMarkerRulerAction2;
import org.eclipse.jdt.internal.ui.javaeditor.OverrideIndicatorImageProvider;
import org.eclipse.jface.text.Position;
import org.eclipse.jface.text.source.Annotation;
import org.eclipse.jface.text.source.IAnnotationModelExtension;
import org.eclipse.jface.text.source.VerticalRulerEvent;
import org.eclipse.swt.widgets.Event;

import scala.tools.eclipse.contribution.weaving.jdt.IScalaCompilationUnit;
import scala.tools.eclipse.contribution.weaving.jdt.IScalaElement;
import scala.tools.eclipse.contribution.weaving.jdt.IScalaOverrideIndicator;
import scala.tools.eclipse.contribution.weaving.jdt.util.ReflectionUtils;

@SuppressWarnings("restriction")
public privileged aspect HierarchyAspect {
  
  pointcut getAllTypesFromElement(ChangeCollector cc, IJavaElement element, ArrayList allTypes) :
    execution(void ChangeCollector.getAllTypesFromElement(IJavaElement, ArrayList)) &&
    args(element, allTypes) &&
    target(cc);
  
  pointcut updateAnnotations(org.eclipse.jdt.internal.ui.javaeditor.OverrideIndicatorManager oim, CompilationUnit ast, IProgressMonitor progressMonitor) :
    execution(void org.eclipse.jdt.internal.ui.javaeditor.OverrideIndicatorManager.updateAnnotations(CompilationUnit, IProgressMonitor)) &&
    args(ast, progressMonitor) &&
    target(oim);
  
  pointcut isOverwriting(Annotation a) :
    execution(boolean OverrideIndicatorImageProvider.isOverwriting(Annotation)) &&
    args(a);
  
  pointcut annotationDefaultSelected(VerticalRulerEvent event) :
    execution(void JavaSelectMarkerRulerAction2.annotationDefaultSelected(VerticalRulerEvent)) &&
    args(event);
  
  pointcut update(JavaSelectAnnotationRulerAction jsara) :
    execution(void JavaSelectAnnotationRulerAction.update()) &&
    target(jsara);
  
  pointcut runWithEvent(JavaSelectAnnotationRulerAction jsara, Event event) :
    execution(void JavaSelectAnnotationRulerAction.runWithEvent(Event)) &&
    args(event) &&
    target(jsara);
  
  void around(ChangeCollector cc, IJavaElement element, ArrayList allTypes) throws JavaModelException :
    getAllTypesFromElement(cc, element, allTypes) {
    getAllTypesFromElement0(cc, element, allTypes);
  }

  public void getAllTypesFromElement0(ChangeCollector cc, IJavaElement element, ArrayList allTypes) throws JavaModelException {
    switch (element.getElementType()) {
      case IJavaElement.COMPILATION_UNIT:
        IType[] types = ((ICompilationUnit)element).getTypes();
        for (int i = 0, length = types.length; i < length; i++) {
          IType type = types[i];
          allTypes.add(type);
          getAllTypesFromElement0(cc, type, allTypes);
        }
        break;
      case IJavaElement.TYPE:
        types = ((IType)element).getTypes();
        for (int i = 0, length = types.length; i < length; i++) {
          IType type = types[i];
          allTypes.add(type);
          getAllTypesFromElement0(cc, type, allTypes);
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
            getAllTypesFromElement0(cc, type, allTypes);
          } else if(child instanceof IScalaElement) {
            getAllTypesFromElement0(cc, child, allTypes);
          }
        }
        break;
    }
  }
  
  void around(org.eclipse.jdt.internal.ui.javaeditor.OverrideIndicatorManager oim, CompilationUnit ast, IProgressMonitor progressMonitor) :
    updateAnnotations(oim, ast, progressMonitor) {
    if (!(oim.fJavaElement instanceof IScalaCompilationUnit)) {
      proceed(oim, ast, progressMonitor);
      return;
    }
    
    HashMap annotationMap = new HashMap();
    ((IScalaCompilationUnit)oim.fJavaElement).createOverrideIndicators(annotationMap);
    
    if (progressMonitor.isCanceled())
      return;

    synchronized (oim.fAnnotationModelLockObject) {
      if (oim.fAnnotationModel instanceof IAnnotationModelExtension) {
        ((IAnnotationModelExtension)oim.fAnnotationModel).replaceAnnotations(oim.fOverrideAnnotations, annotationMap);
      } else {
        oim.removeAnnotations();
        Iterator iter= annotationMap.entrySet().iterator();
        while (iter.hasNext()) {
          Map.Entry mapEntry= (Map.Entry)iter.next();
          oim.fAnnotationModel.addAnnotation((Annotation)mapEntry.getKey(), (Position)mapEntry.getValue());
        }
      }
      
      oim.fOverrideAnnotations = (Annotation[])annotationMap.keySet().toArray(new Annotation[annotationMap.keySet().size()]);
    }
  }
  
  boolean around(Annotation a) :
    isOverwriting(a) {
    if (a instanceof IScalaOverrideIndicator)
      return false;
    else
      return proceed(a);
  }

  void around(VerticalRulerEvent event) :
    annotationDefaultSelected(event) {
    Annotation annotation = event.getSelectedAnnotation();
    if(annotation instanceof IScalaOverrideIndicator) 
      ((IScalaOverrideIndicator)annotation).open();
    else
      proceed(event);
  }
  
  void around(JavaSelectAnnotationRulerAction jsara) :
    update(jsara) {
    jsara.findJavaAnnotation();
    jsara.setEnabled(true);

    if (jsara.fAnnotation instanceof IScalaOverrideIndicator) {
      JavaSelectAnnotationRulerActionUtils.initialize(jsara, jsara.fBundle, "JavaSelectAnnotationRulerAction.OpenSuperImplementation.");
      //JavaSelectAnnotationRulerActionUtils.setEnabled(jsara, JavaSelectAnnotationRulerActionUtils.hasMarkers(jsara));
    }
    else
      proceed(jsara);
  }
  
  void around(JavaSelectAnnotationRulerAction jsara, Event event) :
    runWithEvent(jsara, event) {
    if (jsara.fAnnotation instanceof IScalaOverrideIndicator)
      ((IScalaOverrideIndicator)jsara.fAnnotation).open();
    else
      proceed(jsara, event);
  }
}
