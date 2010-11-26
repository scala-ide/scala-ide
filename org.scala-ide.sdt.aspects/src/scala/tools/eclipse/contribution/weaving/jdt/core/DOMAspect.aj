/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse.contribution.weaving.jdt.core;

import java.util.Locale;
import java.util.Map;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.compiler.CharOperation;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.internal.compiler.CompilationResult;
import org.eclipse.jdt.internal.compiler.DefaultErrorHandlingPolicies;
import org.eclipse.jdt.internal.compiler.ast.AbstractMethodDeclaration;
import org.eclipse.jdt.internal.compiler.ast.AbstractVariableDeclaration;
import org.eclipse.jdt.internal.compiler.ast.AnnotationMethodDeclaration;
import org.eclipse.jdt.internal.compiler.ast.Argument;
import org.eclipse.jdt.internal.compiler.ast.CompilationUnitDeclaration;
import org.eclipse.jdt.internal.compiler.ast.ConstructorDeclaration;
import org.eclipse.jdt.internal.compiler.ast.Expression;
import org.eclipse.jdt.internal.compiler.ast.MethodDeclaration;
import org.eclipse.jdt.internal.compiler.ast.QualifiedAllocationExpression;
import org.eclipse.jdt.internal.compiler.ast.Statement;
import org.eclipse.jdt.internal.compiler.ast.TypeDeclaration;
import org.eclipse.jdt.internal.compiler.ast.TypeParameter;
import org.eclipse.jdt.internal.compiler.ast.TypeReference;
import org.eclipse.jdt.internal.compiler.classfmt.ClassFileConstants;
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
import org.eclipse.jdt.internal.core.SourceAnnotationMethodInfo;
import org.eclipse.jdt.internal.core.SourceMethod;
import org.eclipse.jdt.internal.core.SourceMethodElementInfo;
import org.eclipse.jdt.internal.core.SourceType;
import org.eclipse.jdt.internal.core.SourceTypeElementInfo;

import scala.tools.eclipse.contribution.weaving.jdt.IScalaCompilationUnit;

@SuppressWarnings("restriction")
public privileged aspect DOMAspect {
  pointcut internalCreateAST(ASTParser parser, IProgressMonitor monitor) :
    execution(ASTNode ASTParser.internalCreateAST(IProgressMonitor)) &&
    args(monitor) &&
    target(parser);
  
  pointcut convert(SourceTypeConverter stc, SourceMethod methodHandle, SourceMethodElementInfo methodInfo, CompilationResult compilationResult) :
    execution(AbstractMethodDeclaration SourceTypeConverter.convert(SourceMethod, SourceMethodElementInfo, CompilationResult)) &&
    args(methodHandle, methodInfo, compilationResult) &&
    target(stc);
  
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
      if (tpe != null) {
        //BACK-2.8.0 possible NPE with tpe.binding, tpe.scope
        if (tpe.binding != null) {
          tpe.binding.getAnnotationTagBits();
        }
        if (tpe.scope != null) {
          tpe.scope.buildFields();
          tpe.scope.buildMethods();
        }
        fixFields(tpe.fields);
        fixMethods(tpe.methods);
        fixTypes(tpe.memberTypes);
      }
    }
  }
  
  private void fixMethods(AbstractMethodDeclaration[] methods) {
    if (methods == null)
      return;
    
    for(int i = 0, iLimit = methods.length; i < iLimit; ++i) {
      AbstractMethodDeclaration m = methods[i];
      m.bodyStart = m.declarationSourceStart;
      m.bodyEnd = m.declarationSourceEnd;
      if (m.binding != null) {
        m.binding.getAnnotationTagBits();
      }
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

  AbstractMethodDeclaration around(SourceTypeConverter stc, SourceMethod methodHandle, SourceMethodElementInfo methodInfo, CompilationResult compilationResult) throws JavaModelException :
    convert(stc, methodHandle, methodInfo, compilationResult) {
    AbstractMethodDeclaration method;

    /* only source positions available */
    int start = methodInfo.getNameSourceStart();
    int end = methodInfo.getNameSourceEnd();

    // convert 1.5 specific constructs only if compliance is 1.5 or above
    TypeParameter[] typeParams = null;
    if (stc.has1_5Compliance) {
      /* convert type parameters */
      char[][] typeParameterNames = methodInfo.getTypeParameterNames();
      if (typeParameterNames != null) {
        int parameterCount = typeParameterNames.length;
        if (parameterCount > 0) { // method's type parameters must be null if no type parameter
          char[][][] typeParameterBounds = methodInfo.getTypeParameterBounds();
          typeParams = new TypeParameter[parameterCount];
          for (int i = 0; i < parameterCount; i++) {
            typeParams[i] = stc.createTypeParameter(typeParameterNames[i], typeParameterBounds[i], start, end);
          }
        }
      }
    }

    int modifiers = methodInfo.getModifiers();
    if (methodInfo.isConstructor()) {
      ConstructorDeclaration decl = new ConstructorDeclaration(compilationResult);
      decl.bits &= ~org.eclipse.jdt.internal.compiler.ast.ASTNode.IsDefaultConstructor;
      method = decl;
      decl.typeParameters = typeParams;
    } else {
      MethodDeclaration decl;
      if (methodInfo.isAnnotationMethod()) {
        AnnotationMethodDeclaration annotationMethodDeclaration = new AnnotationMethodDeclaration(compilationResult);

        /* conversion of default value */
        SourceAnnotationMethodInfo annotationMethodInfo = (SourceAnnotationMethodInfo) methodInfo;
        boolean hasDefaultValue = annotationMethodInfo.defaultValueStart != -1 || annotationMethodInfo.defaultValueEnd != -1;
        if ((stc.flags & SourceTypeConverter.FIELD_INITIALIZATION) != 0) {
          if (hasDefaultValue) {
            char[] defaultValueSource = CharOperation.subarray(stc.getSource(), annotationMethodInfo.defaultValueStart, annotationMethodInfo.defaultValueEnd+1);
            if (defaultValueSource != null) {
                Expression expression =  stc.parseMemberValue(defaultValueSource);
                if (expression != null) {
                  annotationMethodDeclaration.defaultValue = expression;
                }
            } else {
              // could not retrieve the default value
              hasDefaultValue = false;
            }
          }
        }
        if (hasDefaultValue)
          modifiers |= ClassFileConstants.AccAnnotationDefault;
        decl = annotationMethodDeclaration;
      } else {
        decl = new MethodDeclaration(compilationResult);
      }

      // convert return type
      decl.returnType = stc.createTypeReference(methodInfo.getReturnTypeName(), start, end);

      // type parameters
      decl.typeParameters = typeParams;

      method = decl;
    }
    method.selector = methodHandle.getElementName().toCharArray();
    boolean isVarargs = (modifiers & ClassFileConstants.AccVarargs) != 0;
    method.modifiers = modifiers & ~ClassFileConstants.AccVarargs;
    method.sourceStart = start;
    method.sourceEnd = end;
    method.declarationSourceStart = methodInfo.getDeclarationSourceStart();
    method.declarationSourceEnd = methodInfo.getDeclarationSourceEnd();

    // convert 1.5 specific constructs only if compliance is 1.5 or above
    if (stc.has1_5Compliance) {
      /* convert annotations */
      method.annotations = stc.convertAnnotations(methodHandle);
    }

    /* convert arguments */
    String[] argumentTypeSignatures = methodHandle.getParameterTypes();
    char[][] argumentNames = methodInfo.getArgumentNames();
    int argumentCount = argumentTypeSignatures == null ? 0 : argumentTypeSignatures.length;
    if (argumentCount > 0) {
      long position = ((long) start << 32) + end;
      method.arguments = new Argument[argumentCount];
      for (int i = 0; i < argumentCount; i++) {
        TypeReference typeReference = stc.createTypeReference(argumentTypeSignatures[i], start, end);
        if (isVarargs && i == argumentCount-1) {
          typeReference.bits |= org.eclipse.jdt.internal.compiler.ast.ASTNode.IsVarArgs;
        }
        method.arguments[i] =
          new Argument(
            argumentNames[i],
            position,
            typeReference,
            ClassFileConstants.AccDefault);
        // do not care whether was final or not
      }
    }

    /* convert thrown exceptions */
    char[][] exceptionTypeNames = methodInfo.getExceptionTypeNames();
    int exceptionCount = exceptionTypeNames == null ? 0 : exceptionTypeNames.length;
    if (exceptionCount > 0) {
      method.thrownExceptions = new TypeReference[exceptionCount];
      for (int i = 0; i < exceptionCount; i++) {
        method.thrownExceptions[i] =
          stc.createTypeReference(exceptionTypeNames[i], start, end);
      }
    }

    /* convert local and anonymous types */
    if ((stc.flags & SourceTypeConverter.LOCAL_TYPE) != 0) {
      IJavaElement[] children = methodInfo.getChildren();
      int typesLength = 0;
      int childrenLength = children.length;
      for (int i = 0; i < childrenLength; ++i)
        if (children[i] instanceof SourceType)
          ++typesLength;
      
      if (typesLength != 0) {
        Statement[] statements = new Statement[typesLength];
        int typeIndex = 0;
        for (int i = 0; i < childrenLength; i++) {
          if (children[i] instanceof SourceType) {
            SourceType type = (SourceType) children[i];
            TypeDeclaration localType = stc.convert(type, compilationResult);
            if ((localType.bits & org.eclipse.jdt.internal.compiler.ast.ASTNode.IsAnonymousType) != 0) {
              QualifiedAllocationExpression expression = new QualifiedAllocationExpression(localType);
              expression.type = localType.superclass;
              localType.superclass = null;
              localType.superInterfaces = null;
              localType.allocation = expression;
              statements[typeIndex] = expression;
            } else {
              statements[typeIndex] = localType;
            }
            ++typeIndex;
          }
        }
        method.statements = statements;
      }
    }

    return method;
  }
}
