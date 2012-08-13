/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse.javaelements

import java.util.{ HashMap => JHashMap, Map => JMap }

import org.eclipse.core.resources.{ IMarker, IResource }
import org.eclipse.core.runtime.{ IAdaptable, IPath, IProgressMonitor, IStatus }
import org.eclipse.core.runtime.jobs.ISchedulingRule
import org.eclipse.jdt.core.{
  BufferChangedEvent, CompletionRequestor, IBuffer, IBufferFactory, ICodeCompletionRequestor, ICompletionRequestor, ICompilationUnit,
  IImportContainer, IImportDeclaration, IJavaElement, IJavaModel, IJavaProject, IOpenable, IPackageDeclaration,
  IProblemRequestor, ISourceRange, IType, ITypeRoot, JavaModelException, WorkingCopyOwner }
import org.eclipse.jdt.core.dom.CompilationUnit
import org.eclipse.jdt.internal.compiler.env
import org.eclipse.jdt.internal.core.{ BufferManager, DefaultWorkingCopyOwner, JavaElement, Openable, OpenableElementInfo, PackageFragmentRoot }
import org.eclipse.jdt.internal.core.util.MementoTokenizer
import org.eclipse.text.edits.{ TextEdit, UndoEdit }

class CompilationUnitAdapter(classFile : ScalaClassFile) extends Openable(classFile.getParent.asInstanceOf[JavaElement]) with ICompilationUnit with env.ICompilationUnit {
  override def getAdapter(adapter : Class[_]) : AnyRef = (classFile : IAdaptable).getAdapter(adapter)
  
  override def equals(o : Any) = classFile.equals(o)
  override def hashCode() = classFile.hashCode()
  
  def getFileName() : Array[Char] = classFile.getFileName()
  def getContents() : Array[Char] = classFile.getContents()
  def getMainTypeName() : Array[Char] = classFile.getMainTypeName()
  def getPackageName() : Array[Array[Char]] = classFile.getPackageName()
  
  override def getHandleMementoDelimiter() : Char = classFile.getHandleMementoDelimiter0()
  override def getHandleFromMemento(token : String, memento : MementoTokenizer, owner : WorkingCopyOwner) : IJavaElement =
    classFile.getHandleFromMemento(token, memento, owner)
  
  override def bufferChanged(e : BufferChangedEvent) { classFile.bufferChanged(e) }
  override def buildStructure(info : OpenableElementInfo, pm : IProgressMonitor, newElements : JMap[_, _], underlyingResource : IResource) : Boolean =
    classFile.buildStructure(info, pm, newElements, underlyingResource)
  override def canBeRemovedFromCache() : Boolean = classFile.canBeRemovedFromCache()
  override def canBufferBeRemovedFromCache(buffer : IBuffer) : Boolean = classFile.canBufferBeRemovedFromCache(buffer)
  override def closeBuffer() { classFile.closeBuffer0() }
  override def closing(info : AnyRef) { classFile.closing0(info) }
  override def codeComplete(
    cu : env.ICompilationUnit,
    unitToSkip : env.ICompilationUnit,
    position : Int,
    requestor : CompletionRequestor,
    owner : WorkingCopyOwner,
    typeRoot : ITypeRoot,
    monitor : IProgressMonitor) {
    classFile.codeComplete(cu, unitToSkip, position, requestor, owner, typeRoot, monitor)
  }
  override def codeSelect(cu : env.ICompilationUnit, offset : Int, length : Int, owner : WorkingCopyOwner) : Array[IJavaElement] =
    classFile.codeSelect(cu, offset, length, owner)
  override def createElementInfo() : AnyRef = classFile.createElementInfo0()
  override def generateInfos(info : AnyRef, newElements : JHashMap[_, _], monitor : IProgressMonitor) =
    classFile.generateInfos0(info, newElements, monitor)
  override def getBufferFactory() : IBufferFactory = classFile.getBufferFactory()
  override def getBufferManager() : BufferManager = classFile.getBufferManager0()
  override def hasBuffer() : Boolean = classFile.hasBuffer0()
  override def isSourceElement() : Boolean = classFile.isSourceElement0()
  override def openBuffer(pm : IProgressMonitor, info : Object) : IBuffer = classFile.openBuffer0(pm, info)
  override def resource() : IResource = classFile.resource()
  override def resource(root : PackageFragmentRoot) : IResource = classFile.resource(root) 
  override def resourceExists(underlyingResource : IResource) : Boolean = classFile.resourceExists0(underlyingResource) 
  override def getPackageFragmentRoot() : PackageFragmentRoot = classFile.getPackageFragmentRoot()
  override def validateExistence(underlyingResource : IResource) : IStatus = classFile.validateExistence0(underlyingResource)
  override def openAncestors(newElements : JHashMap[_, _], monitor : IProgressMonitor) { classFile.openAncestors0(newElements, monitor) }
  
  override def exists() = classFile.exists()
  override def getAncestor(ancestorType : Int) : IJavaElement = classFile.getAncestor(ancestorType)
  override def getAttachedJavadoc(monitor : IProgressMonitor) : String = classFile.getAttachedJavadoc(monitor)
  override def getCorrespondingResource() : IResource = classFile.getCorrespondingResource()
  override def getElementName() : String = classFile.getElementName()
  override def getElementType() : Int = classFile.getElementType()
  override def getHandleIdentifier() : String = classFile.getHandleIdentifier()
  override def getJavaModel() : IJavaModel = classFile.getJavaModel()
  override def getJavaProject() : IJavaProject = classFile.getJavaProject()
  override def getOpenable() : IOpenable = classFile.getOpenable()
  override def getParent() : IJavaElement = classFile.getParent()
  override def getPath() : IPath = classFile.getPath()
  override def getPrimaryElement() : IJavaElement = classFile.getPrimaryElement()
  override def getResource() : IResource = classFile.getResource()
  override def getSchedulingRule() : ISchedulingRule = classFile.getSchedulingRule()
  override def getUnderlyingResource() : IResource = classFile.getUnderlyingResource()
  override def isReadOnly() : Boolean = classFile.isReadOnly()
  override def isStructureKnown() : Boolean = classFile.isStructureKnown()
  
  override def getChildren() : Array[IJavaElement] = classFile.getChildren()
  override def hasChildren() : Boolean = classFile.hasChildren()
  
  override def close() { classFile.close() }
  override def findRecommendedLineSeparator() : String = classFile.findRecommendedLineSeparator()
  override def getBuffer() : IBuffer = classFile.getBuffer()
  override def hasUnsavedChanges() : Boolean = classFile.hasUnsavedChanges()
  override def isConsistent() : Boolean = classFile.isConsistent()
  override def isOpen() : Boolean = classFile.isOpen()
  override def makeConsistent(progress : IProgressMonitor) { classFile.makeConsistent(progress) }
  override def open(progress : IProgressMonitor) { classFile.open(progress) }
  override def save(progress : IProgressMonitor, force : Boolean) { classFile.save(progress, force) }
  
  def getSource() : String = classFile.getSource()
  def getSourceRange() : ISourceRange = classFile.getSourceRange()
  
  def codeComplete(offset : Int, requestor : ICodeCompletionRequestor) { classFile.codeComplete(offset, requestor) }
  def codeComplete(offset : Int, requestor : ICompletionRequestor) { classFile.codeComplete(offset, requestor) }
  def codeComplete(offset : Int, requestor : CompletionRequestor) { classFile.codeComplete(offset, requestor) }
  def codeComplete(offset : Int, requestor : CompletionRequestor, monitor : IProgressMonitor) { classFile.codeComplete(offset, requestor, monitor) }
  def codeComplete(offset : Int, requestor : ICompletionRequestor, owner : WorkingCopyOwner) { classFile.codeComplete(offset, requestor, owner) }
  def codeComplete(offset : Int, requestor : CompletionRequestor, owner : WorkingCopyOwner) { classFile.codeComplete(offset, requestor, owner) }
  def codeComplete(offset : Int, requestor : CompletionRequestor, owner : WorkingCopyOwner, monitor : IProgressMonitor) { classFile.codeComplete(offset, requestor, owner, monitor) }
  def codeSelect(offset : Int, length : Int) : Array[IJavaElement] = classFile.codeSelect(offset, length)
  def codeSelect(offset : Int, length : Int, owner : WorkingCopyOwner) : Array[IJavaElement] = classFile.codeSelect(offset, length, owner)
  
  def findPrimaryType() : IType = classFile.findPrimaryType()
  def getElementAt(position : Int) : IJavaElement = classFile.getElementAt(position)
  def getWorkingCopy(owner : WorkingCopyOwner, monitor : IProgressMonitor) : ICompilationUnit = classFile.getWorkingCopy(owner, monitor)

  def commit(force : Boolean, monitor : IProgressMonitor) { throw new UnsupportedOperationException }
  def destroy() { throw new UnsupportedOperationException }
  def findSharedWorkingCopy(bufferFactory : IBufferFactory) : IJavaElement = { throw new UnsupportedOperationException }
  def getOriginal(workingCopyElement : IJavaElement) : IJavaElement = { throw new UnsupportedOperationException }
  def getOriginalElement() : IJavaElement = throw new UnsupportedOperationException
  def findElements(element : IJavaElement) : Array[IJavaElement] = throw new UnsupportedOperationException
  def getSharedWorkingCopy(
    monitor : IProgressMonitor,
    factory : IBufferFactory,
    problemRequestor : IProblemRequestor) : IJavaElement = classFile.getWorkingCopy(null, null : IProgressMonitor)
  def getWorkingCopy() : IJavaElement = classFile.getWorkingCopy(null, null : IProgressMonitor)
  def getWorkingCopy(
    monitor : IProgressMonitor,
    factory : IBufferFactory,
    problemRequestor : IProblemRequestor) : IJavaElement = classFile.getWorkingCopy(monitor, factory)
  def isBasedOn(resource : IResource) : Boolean = throw new UnsupportedOperationException
  def isWorkingCopy() : Boolean = false
  def reconcile() : Array[IMarker] = throw new UnsupportedOperationException
  def reconcile(forceProblemDetection : Boolean, monitor : IProgressMonitor) { throw new UnsupportedOperationException }
  def restore() { throw new UnsupportedOperationException }
  
  def copy(container : IJavaElement, sibling : IJavaElement, rename : String, replace : Boolean, monitor : IProgressMonitor) { throw new UnsupportedOperationException }
  def delete(force : Boolean, monitor : IProgressMonitor) { throw new UnsupportedOperationException }
  def move(container : IJavaElement, sibling : IJavaElement, rename : String, replace : Boolean, monitor : IProgressMonitor) { throw new UnsupportedOperationException }
  def rename(name : String, replace : Boolean, monitor : IProgressMonitor) { throw new UnsupportedOperationException }
  
  def applyTextEdit(edit : TextEdit, monitor : IProgressMonitor) : UndoEdit = throw new UnsupportedOperationException
  def becomeWorkingCopy(problemRequestor : IProblemRequestor, monitor : IProgressMonitor) { classFile.becomeWorkingCopy(problemRequestor, null, monitor) }
  def becomeWorkingCopy(monitor : IProgressMonitor) { classFile.becomeWorkingCopy(null, null, monitor) }
  def commitWorkingCopy(force : Boolean, monitor : IProgressMonitor) {}
  def createImport(name : String, sibling : IJavaElement, monitor : IProgressMonitor) : IImportDeclaration = throw new UnsupportedOperationException
  def createImport(name : String, sibling : IJavaElement, flags : Int, monitor : IProgressMonitor) : IImportDeclaration = throw new UnsupportedOperationException
  def createPackageDeclaration(name : String, monitor : IProgressMonitor) : IPackageDeclaration = throw new UnsupportedOperationException
  def createType(contents : String, sibling : IJavaElement, force : Boolean, monitor : IProgressMonitor) : IType = throw new UnsupportedOperationException
  def discardWorkingCopy() {}
  def findWorkingCopy(owner : WorkingCopyOwner) : ICompilationUnit = null
  def getAllTypes() : Array[IType] = Array(classFile.getType())
  def getImport(name : String) : IImportDeclaration = throw new UnsupportedOperationException
  def getImportContainer() : IImportContainer = throw new UnsupportedOperationException
  def getImports() : Array[IImportDeclaration] = Array()
  def getPrimary() : ICompilationUnit = this
  def getOwner() : WorkingCopyOwner = DefaultWorkingCopyOwner.PRIMARY
  def getPackageDeclaration(name : String) : IPackageDeclaration = throw new UnsupportedOperationException
  def getPackageDeclarations() : Array[IPackageDeclaration] = throw new UnsupportedOperationException
  def getType(name : String) : IType = if (name == classFile.getTypeName()) classFile.getType() else throw new UnsupportedOperationException
  def getTypes() : Array[IType] = Array(classFile.getType())
  def getWorkingCopy(monitor : IProgressMonitor) : ICompilationUnit = classFile.getWorkingCopy(null, monitor)
  def getWorkingCopy(owner : WorkingCopyOwner, problemRequestor : IProblemRequestor, monitor : IProgressMonitor) : ICompilationUnit = classFile.getWorkingCopy(owner, monitor)
  def hasResourceChanged() : Boolean = false
  def reconcile(astLevel : Int, forceProblemDetection : Boolean, owner : WorkingCopyOwner, monitor : IProgressMonitor) : CompilationUnit = null
  def reconcile(astLevel : Int, forceProblemDetection : Boolean, enableStatementsRecovery : Boolean, owner : WorkingCopyOwner, monitor : IProgressMonitor) : CompilationUnit = null
  def reconcile(astLevel : Int, reconcileFlags : Int, owner : WorkingCopyOwner, monitor : IProgressMonitor) : CompilationUnit = null
  def getNameRange() : ISourceRange = throw new UnsupportedOperationException
  def ignoreOptionalProblems() : Boolean = false
}
