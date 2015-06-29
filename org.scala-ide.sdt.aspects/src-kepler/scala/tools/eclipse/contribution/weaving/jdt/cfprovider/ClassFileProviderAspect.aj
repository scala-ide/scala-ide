/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse.contribution.weaving.jdt.cfprovider;

import java.util.HashSet;

import org.eclipse.core.filebuffers.FileBuffers;
import org.eclipse.core.filebuffers.ITextFileBuffer;
import org.eclipse.core.filebuffers.ITextFileBufferManager;
import org.eclipse.core.filebuffers.LocationKind;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.IBuffer;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.IImportDeclaration;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IOpenable;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.search.TypeNameMatch;
import org.eclipse.jdt.internal.compiler.CompilationResult;
import org.eclipse.jdt.internal.compiler.ast.ASTNode;
import org.eclipse.jdt.internal.compiler.ast.CompilationUnitDeclaration;
import org.eclipse.jdt.internal.compiler.ast.ImportReference;
import org.eclipse.jdt.internal.compiler.ast.TypeDeclaration;
import org.eclipse.jdt.internal.compiler.ast.TypeReference;
import org.eclipse.jdt.internal.compiler.classfmt.ClassFileConstants;
import org.eclipse.jdt.internal.compiler.env.IBinaryType;
import org.eclipse.jdt.internal.compiler.env.ICompilationUnit;
import org.eclipse.jdt.internal.compiler.env.IGenericType;
import org.eclipse.jdt.internal.compiler.env.ISourceImport;
import org.eclipse.jdt.internal.compiler.env.ISourceType;
import org.eclipse.jdt.internal.compiler.env.NameEnvironmentAnswer;
import org.eclipse.jdt.internal.compiler.lookup.ReferenceBinding;
import org.eclipse.jdt.internal.compiler.lookup.SourceTypeBinding;
import org.eclipse.jdt.internal.compiler.parser.Parser;
import org.eclipse.jdt.internal.compiler.parser.SourceTypeConverter;
import org.eclipse.jdt.internal.core.BinaryType;
import org.eclipse.jdt.internal.core.ClassFile;
import org.eclipse.jdt.internal.core.CompilationUnitElementInfo;
import org.eclipse.jdt.internal.core.ImportDeclaration;
import org.eclipse.jdt.internal.core.JavaElement;
import org.eclipse.jdt.internal.core.BufferManager;
import org.eclipse.jdt.internal.core.NameLookup;
import org.eclipse.jdt.internal.core.Openable;
import org.eclipse.jdt.internal.core.PackageFragment;
import org.eclipse.jdt.internal.core.SearchableEnvironment;
import org.eclipse.jdt.internal.core.SourceMapper;
import org.eclipse.jdt.internal.core.SourceType;
import org.eclipse.jdt.internal.core.SourceTypeElementInfo;
import org.eclipse.jdt.internal.core.hierarchy.HierarchyResolver;
import org.eclipse.jdt.internal.core.hierarchy.HierarchyType;
import org.eclipse.jdt.internal.core.util.Util;
import org.eclipse.jdt.internal.corext.util.OpenTypeHistory;
import org.eclipse.jdt.internal.ui.filters.InnerClassFilesFilter;
import org.eclipse.jdt.ui.SharedASTProvider;
import org.eclipse.jface.viewers.Viewer;

import scala.tools.eclipse.contribution.weaving.jdt.IScalaClassFile;
import scala.tools.eclipse.contribution.weaving.jdt.IScalaElement;

@SuppressWarnings("restriction")
public privileged aspect ClassFileProviderAspect {

  pointcut classFileCreations(PackageFragment parent, String name) : 
    call(protected ClassFile.new(PackageFragment, String)) &&
    within(org.eclipse.jdt..*) &&
    args(parent, name);
  
  pointcut getTypeName(ClassFile cf) :
    execution(public String ClassFile.getTypeName()) &&
    target(cf);

  // For 3.6
  pointcut mapSource(ClassFile cf, SourceMapper mapper, IBinaryType info, IClassFile owner) :
    execution(IBuffer ClassFile.mapSource(SourceMapper, IBinaryType, IClassFile)) &&
    target(cf) &&
    target(IScalaClassFile) &&
    args(mapper, info, owner);

  // For 3.5
  pointcut mapSource2(ClassFile cf, SourceMapper mapper, IBinaryType info) :
    execution(IBuffer ClassFile.mapSource(SourceMapper, IBinaryType)) &&
    target(cf) &&
    target(IScalaClassFile) &&
    args(mapper, info);

  pointcut getSourceFileName(BinaryType bt) :
    execution(String BinaryType.getSourceFileName(IBinaryType)) &&
    target(bt);
  
  pointcut select(Viewer viewer, Object parentElement, Object element) :
    execution(boolean InnerClassFilesFilter.select(Viewer, Object, Object)) &&
    args(viewer, parentElement, element);
  
  pointcut getChildren(BinaryType bt) :
    execution(IJavaElement[] BinaryType.getChildren()) &&
    target(bt);
  
  pointcut getAST(ITypeRoot element, SharedASTProvider.WAIT_FLAG waitFlag, IProgressMonitor progressMonitor) : 
    execution(CompilationUnit SharedASTProvider.getAST(ITypeRoot, SharedASTProvider.WAIT_FLAG, IProgressMonitor)) &&
    args(element, waitFlag, progressMonitor);
  
  pointcut resolve(HierarchyResolver hr, IGenericType suppliedType) :
    execution(void HierarchyResolver.resolve(IGenericType)) &&
    target(hr) &&
    args(suppliedType);
  
  pointcut remember(HierarchyResolver hr, IType type, ReferenceBinding typeBinding) :
    execution(void HierarchyResolver.remember(IType, ReferenceBinding)) &&
    target(hr) &&
    args(type, typeBinding);
  
  pointcut acceptType(IType type, int acceptFlags, boolean isSourceType) :
    execution(boolean NameLookup.acceptType(IType, int, boolean)) &&
    args(type, acceptFlags, isSourceType);
  
  pointcut find(SearchableEnvironment se, String typeName, String packageName) :
    execution(NameEnvironmentAnswer find(String, String)) &&
    target(se) &&
    args(typeName, packageName);
  
  pointcut convert(SourceTypeConverter stc, ISourceType[] sourceTypes, CompilationResult compilationResult) :
    execution(CompilationUnitDeclaration SourceTypeConverter.convert(ISourceType[], CompilationResult)) &&
    target(stc) &&
    args(sourceTypes, compilationResult);
  
  pointcut getAncestor(JavaElement je, int ancestorType) :
    execution(IJavaElement JavaElement.getAncestor(int)) &&
    target(je) &&
    args(ancestorType);
  
  pointcut isContainerDirty(TypeNameMatch match) :
    execution(boolean OpenTypeHistory.isContainerDirty(TypeNameMatch)) &&
    args(match);
  
  ClassFile around(PackageFragment parent, String name) : 
    classFileCreations(parent, name) {

    ClassFile javaClassFile = proceed(parent, name);
    byte[] bytes;
    
    try {
        for (IClassFileProvider provider : ClassFileProviderRegistry.getInstance().getProviders()) {
          if (provider.isInteresting(javaClassFile)) {
            bytes = javaClassFile.getBytes();
            ClassFile cf = provider.create(bytes, parent, name);
            if (cf != null)
              return cf;
          }
        }
    } catch(Throwable t) {
      return javaClassFile; 
    }
    
    return javaClassFile;
  }
  
  IBuffer around(ClassFile cf, SourceMapper mapper, IBinaryType info, IClassFile owner) :
    mapSource(cf, mapper, info, owner) {
    return mapSourceSubst(cf, mapper, info);
  }

  IBuffer around(ClassFile cf, SourceMapper mapper, IBinaryType info) :
    mapSource2(cf, mapper, info) {
    return mapSourceSubst(cf, mapper, info);
  }

  IBuffer mapSourceSubst(ClassFile cf, SourceMapper mapper, IBinaryType info) {
    char[] contents = mapper.findSource(cf.getType(), info);
    IBuffer buffer;
    if (contents != null) {
      buffer = BufferManager.createBuffer(cf);
      buffer.setContents(contents);
    } else {
      buffer = BufferManager.createNullBuffer(cf);
    }
    BufferManager bufManager = cf.getBufferManager();
    bufManager.addBuffer(buffer);
    buffer.addBufferChangedListener(cf);
    return buffer;
  }

  String around(ClassFile cf) :
    getTypeName(cf) {
    if(cf instanceof IScalaClassFile) {
      int lastDollar = cf.name.lastIndexOf('$');
      return (lastDollar == -1 || lastDollar == cf.name.length()-1) ? cf.name : Util.localTypeName(cf.name, lastDollar, cf.name.length());
    } else
      return proceed(cf);
  }
  
  boolean around(Viewer viewer, Object parentElement, Object element) :
    select(viewer, parentElement, element) {
    if (element instanceof IScalaClassFile) {
      IClassFile classFile = (IClassFile) element;
      String name = classFile.getElementName(); 
      int dollarIndex = name.indexOf('$');
      return dollarIndex == -1 || dollarIndex == name.length()-7; // Trailing '$' implies object rather than inner class 
    }
    return proceed(viewer, parentElement, element);
  }
  
  IJavaElement[] around(BinaryType bt) throws JavaModelException :
    getChildren(bt) && 
    target(BinaryType) {
    ClassFile cf = (ClassFile)bt.parent;
    if (cf instanceof IScalaClassFile)
      return cf.getChildren();
    else
      return proceed(bt);
  }
  
  CompilationUnit around(ITypeRoot element, SharedASTProvider.WAIT_FLAG waitFlag, IProgressMonitor progressMonitor) :
    getAST(element, waitFlag, progressMonitor) {
      if (element instanceof IScalaElement)
        return null;
      else
        return proceed(element, waitFlag, progressMonitor);
  }
  
  void around(HierarchyResolver hr, IGenericType suppliedType) :
    resolve(hr, suppliedType) {
    if (!suppliedType.isBinaryType()) {
      IType tpe = ((SourceTypeElementInfo)suppliedType).getHandle();
      IClassFile cf = tpe.getClassFile();
      if (cf instanceof IScalaClassFile) {
        hr.resolve(new Openable[]{(Openable)cf}, new HashSet(), null);
        return;
      }
    }
      
    proceed(hr, suppliedType);
  }
  
  void around(HierarchyResolver hr, IType type, ReferenceBinding typeBinding) :
    remember(hr, type, typeBinding) {
    if (((IOpenable)type.getCompilationUnit()).isOpen()) {
      try {
        IGenericType genericType = (IGenericType)((JavaElement)type).getElementInfo();
        hr.remember(genericType, typeBinding);
      } catch (JavaModelException e) {
        // cannot happen since element is open
        return;
      }
    } else {
      if (typeBinding == null) return;

      TypeDeclaration typeDeclaration = ((SourceTypeBinding)typeBinding).scope.referenceType();

      // simple super class name
      char[] superclassName = null;
      TypeReference superclass;
      if ((typeDeclaration.bits & ASTNode.IsAnonymousType) != 0) {
        superclass = typeDeclaration.allocation.type;
      } else {
        superclass = typeDeclaration.superclass;
      }
      if (superclass != null) {
        char[][] typeName = superclass.getTypeName();
        superclassName = typeName == null ? null : typeName[typeName.length-1];
      }

      // simple super interface names
      char[][] superInterfaceNames = null;
      TypeReference[] superInterfaces = typeDeclaration.superInterfaces;
      if (superInterfaces != null) {
        int length = superInterfaces.length;
        superInterfaceNames = new char[length][];
        for (int i = 0; i < length; i++) {
          TypeReference superInterface = superInterfaces[i];
          char[][] typeName = superInterface.getTypeName();
          superInterfaceNames[i] = typeName[typeName.length-1];
        }
      }

      HierarchyType hierarchyType = new HierarchyType(
        type,
        typeDeclaration.name,
        typeDeclaration.binding.modifiers,
        superclassName,
        superInterfaceNames);
      hr.remember(hierarchyType, typeDeclaration.binding);
    }
  }
  
  String around(BinaryType bt) :
    getSourceFileName(bt) {
    IJavaElement parent = bt.getTypeRoot(); 
    if (parent instanceof IScalaClassFile) {
      return ((IScalaClassFile)parent).getSourceFileName();
    }
    else
      return proceed(bt);
  }
  
  boolean around(IType type, int acceptFlags, boolean isSourceType) :
    acceptType(type, acceptFlags, isSourceType) {
    if (!isSourceType && (type instanceof SourceType)) {
      IJavaElement parent = type.getParent(); 
      if (parent instanceof IScalaClassFile)
        return proceed(type, acceptFlags, true);
    }
    
    return proceed(type, acceptFlags, isSourceType);
  }
  
  CompilationUnitDeclaration around(SourceTypeConverter stc, ISourceType[] sourceTypes, CompilationResult compilationResult) throws JavaModelException :
    convert(stc, sourceTypes, compilationResult) {
    stc.unit = new CompilationUnitDeclaration(stc.problemReporter, compilationResult, 0);
    // not filled at this point

    if (sourceTypes.length == 0 || sourceTypes[0] == null) return stc.unit;
    SourceTypeElementInfo topLevelTypeInfo = (SourceTypeElementInfo) sourceTypes[0];
    org.eclipse.jdt.core.ICompilationUnit cuHandle = topLevelTypeInfo.getHandle().getCompilationUnit();
    stc.cu = (ICompilationUnit) cuHandle;

    CompilationUnitElementInfo cuei = null;
    Object info = ((JavaElement)stc.cu).getElementInfo();
    if (info instanceof CompilationUnitElementInfo)
      cuei = (CompilationUnitElementInfo)info;
    
    
    if (stc.has1_5Compliance && cuei != null && cuei.annotationNumber > 10) { // experimental value
      // if more than 10 annotations, diet parse as this is faster
      return new Parser(stc.problemReporter, true).dietParse(stc.cu, compilationResult);
    }

    /* only positions available */
    int start = topLevelTypeInfo.getNameSourceStart();
    int end = topLevelTypeInfo.getNameSourceEnd();

    /* convert package and imports */
    String[] packageName = ((PackageFragment) cuHandle.getParent()).names;
    if (packageName.length > 0)
      // if its null then it is defined in the default package
      stc.unit.currentPackage =
        stc.createImportReference(packageName, start, end, false, ClassFileConstants.AccDefault);
    IImportDeclaration[] importDeclarations = topLevelTypeInfo.getHandle().getCompilationUnit().getImports();
    int importCount = importDeclarations.length;
    stc.unit.imports = new ImportReference[importCount];
    for (int i = 0; i < importCount; i++) {
      ImportDeclaration importDeclaration = (ImportDeclaration) importDeclarations[i];
      ISourceImport sourceImport = (ISourceImport) importDeclaration.getElementInfo();
      String nameWithoutStar = importDeclaration.getNameWithoutStar();
      stc.unit.imports[i] = stc.createImportReference(
        Util.splitOn('.', nameWithoutStar, 0, nameWithoutStar.length()),
        sourceImport.getDeclarationSourceStart(),
        sourceImport.getDeclarationSourceEnd(),
        importDeclaration.isOnDemand(),
        sourceImport.getModifiers());
    }
    /* convert type(s) */
    try {
      int typeCount = sourceTypes.length;
      final TypeDeclaration[] types = new TypeDeclaration[typeCount];
      /*
       * We used a temporary types collection to prevent this.unit.types from being null during a call to
       * convert(...) when the source is syntactically incorrect and the parser is flushing the unit's types.
       * See https://bugs.eclipse.org/bugs/show_bug.cgi?id=97466
       */
      for (int i = 0; i < typeCount; i++) {
        SourceTypeElementInfo typeInfo = (SourceTypeElementInfo) sourceTypes[i];
        types[i] = stc.convert((SourceType) typeInfo.getHandle(), compilationResult);
      }
      stc.unit.types = types;
      return stc.unit;
    } catch (SourceTypeConverter.AnonymousMemberFound e) {
      return new Parser(stc.problemReporter, true).parse(stc.cu, compilationResult);
    }
  }
  
  IJavaElement around(JavaElement je, int ancestorType) :
    getAncestor(je, ancestorType) {
    IJavaElement element = je;
    while (element != null) {
      if (element.getElementType() == ancestorType)
        return element;
      else if (element instanceof IScalaClassFile && ancestorType == IJavaElement.COMPILATION_UNIT)
        return ((JavaElement)element).getCompilationUnit();
      element= element.getParent();
    }
    return null;
  }

  boolean around(TypeNameMatch match) :
    isContainerDirty(match) {
    org.eclipse.jdt.core.ICompilationUnit cu = match.getType().getCompilationUnit();
    if (cu == null) {
      return false;
    }
    IResource resource= cu.getResource();
    if (resource == null)
      return false;
    
    ITextFileBufferManager manager= FileBuffers.getTextFileBufferManager();
    ITextFileBuffer textFileBuffer= manager.getTextFileBuffer(resource.getFullPath(), LocationKind.IFILE);
    if (textFileBuffer != null) {
      return textFileBuffer.isDirty();
    }
    return false;
  }
}
