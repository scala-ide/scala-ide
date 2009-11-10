/*
 * Copyright 2005-2009 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse.contribution.weaving.jdt.core;

import java.util.Locale;
import java.util.Map;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.internal.compiler.CompilationResult;
import org.eclipse.jdt.internal.compiler.DefaultErrorHandlingPolicies;
import org.eclipse.jdt.internal.compiler.ast.AbstractMethodDeclaration;
import org.eclipse.jdt.internal.compiler.ast.AbstractVariableDeclaration;
import org.eclipse.jdt.internal.compiler.ast.CompilationUnitDeclaration;
import org.eclipse.jdt.internal.compiler.ast.TypeDeclaration;
import org.eclipse.jdt.internal.compiler.env.AccessRestriction;
import org.eclipse.jdt.internal.compiler.env.IBinaryType;
import org.eclipse.jdt.internal.compiler.env.INameEnvironment;
import org.eclipse.jdt.internal.compiler.env.ISourceType;
import org.eclipse.jdt.internal.compiler.impl.CompilerOptions;
import org.eclipse.jdt.internal.compiler.impl.ITypeRequestor;
import org.eclipse.jdt.internal.compiler.lookup.LookupEnvironment;
import org.eclipse.jdt.internal.compiler.lookup.PackageBinding;
import org.eclipse.jdt.internal.compiler.parser.SourceTypeConverter;
import org.eclipse.jdt.internal.compiler.problem.DefaultProblemFactory;
import org.eclipse.jdt.internal.compiler.problem.ProblemReporter;
import org.eclipse.jdt.internal.core.DefaultWorkingCopyOwner;
import org.eclipse.jdt.internal.core.JavaProject;
import org.eclipse.jdt.internal.core.SourceType;
import org.eclipse.jdt.internal.core.SourceTypeElementInfo;

import scala.tools.eclipse.contribution.weaving.jdt.IScalaCompilationUnit;

@SuppressWarnings("restriction")
public privileged aspect DOMAspect {
  pointcut internalCreateAST(ASTParser parser, IProgressMonitor monitor) :
    execution(ASTNode ASTParser.internalCreateAST(IProgressMonitor)) &&
    args(monitor) &&
    target(parser);
  
  ASTNode around(ASTParser parser, IProgressMonitor monitor) :
    internalCreateAST(parser, monitor) {
    try {
      if (!(parser.typeRoot instanceof IScalaCompilationUnit))
        return proceed(parser, monitor);
      
      ICompilationUnit cu = (ICompilationUnit)parser.typeRoot;
      org.eclipse.jdt.internal.compiler.env.ICompilationUnit sourceUnit = (org.eclipse.jdt.internal.compiler.env.ICompilationUnit)cu;
      
      IType[] topLevelTypes = cu.getTypes();
      int length = topLevelTypes.length;
      if (length == 0)
        throw new IllegalStateException();
      
      SourceTypeElementInfo[] topLevelInfos = new SourceTypeElementInfo[length];
      for (int i = 0; i < length; i++) {
        topLevelInfos[i] = (SourceTypeElementInfo) ((SourceType)topLevelTypes[i]).getElementInfo();
      }
      char[] fileName = ((ISourceType)topLevelInfos[0]).getFileName();
      
      CompilationResult result = new CompilationResult(fileName, 1, 1, 100);

      JavaProject javaProject = (JavaProject)cu.getJavaProject(); 
      Map compilerOptions0 = javaProject.getOptions(true);
      CompilerOptions compilerOptions = new CompilerOptions(compilerOptions0);
      compilerOptions.storeAnnotations = true;
      
      ProblemReporter problemReporter =
        new ProblemReporter(
          DefaultErrorHandlingPolicies.proceedWithAllProblems(),
          compilerOptions,
          new DefaultProblemFactory(Locale.getDefault()));
      
      int flags = SourceTypeConverter.FIELD_AND_METHOD | SourceTypeConverter.MEMBER_TYPE;
      
      CompilationUnitDeclaration unit =
        SourceTypeConverter.buildCompilationUnit(topLevelInfos, flags, problemReporter, result);
      
      INameEnvironment ne = javaProject.newSearchableNameEnvironment(DefaultWorkingCopyOwner.PRIMARY);
      final LookupEnvironment le = new LookupEnvironment(null, compilerOptions, problemReporter, ne);
      ITypeRequestor typeRequestor = new ITypeRequestor() {
        public void accept(IBinaryType binaryType, PackageBinding packageBinding, AccessRestriction accessRestriction) {
          le.createBinaryTypeFrom(binaryType, packageBinding, accessRestriction);
        }
        public void accept(org.eclipse.jdt.internal.compiler.env.ICompilationUnit unit, AccessRestriction accessRestriction) {}
        public void accept(ISourceType[] sourceType, PackageBinding packageBinding, AccessRestriction accessRestriction) {}
      };
      le.typeRequestor = typeRequestor;
      
      le.buildTypeBindings(unit, null);
      le.completeTypeBindings();
      fixTypes(unit.types);
      
      AST ast = AST.newAST(AST.JLS3);
      ast.setFlag(AST.RESOLVED_BINDINGS);
      ast.setDefaultNodeFlag(ASTNode.ORIGINAL);

      org.eclipse.jdt.core.dom.ASTConverter converter =
        new org.eclipse.jdt.core.dom.ASTConverter(compilerOptions0, true, monitor);
      
      org.eclipse.jdt.core.dom.BindingResolver resolver =
        new org.eclipse.jdt.core.dom.DefaultBindingResolver(
          unit.scope,
          DefaultWorkingCopyOwner.PRIMARY,
          new org.eclipse.jdt.core.dom.DefaultBindingResolver.BindingTables(),
          true);
      ast.setBindingResolver(resolver);
      converter.setAST(ast);
      
      ASTNode node = converter.convert(unit, sourceUnit.getContents());
      return node;
    } catch (JavaModelException ex) {
      throw new IllegalArgumentException(ex);
    }
  }
  
  private void fixTypes(TypeDeclaration[] types) {
    for(int i = 0, iLimit = types.length; i < iLimit ; ++i) {
      TypeDeclaration tpe = types[i];
      tpe.binding.getAnnotationTagBits();
      tpe.scope.buildFields();
      tpe.scope.buildMethods();
      fixFields(tpe.fields);
      fixMethods(tpe.methods);
      fixTypes(tpe.memberTypes);
    }
  }
  
  private void fixMethods(AbstractMethodDeclaration[] methods) {
    if (methods == null)
      return;
    
    for(int i = 0, iLimit = methods.length; i < iLimit; ++i) {
      AbstractMethodDeclaration m = methods[i];
      m.bodyStart = m.declarationSourceStart;
      m.bodyEnd = m.declarationSourceEnd;
      m.binding.getAnnotationTagBits();
    }
  }
  
  private void fixFields(AbstractVariableDeclaration[] fields) {
    if (fields == null)
      return;
    
    for(int i = 0, iLimit = fields.length; i < iLimit; ++i) {
      AbstractVariableDeclaration f = fields[i];
      f.declarationEnd = f.declarationSourceEnd;
    }
  }
}
