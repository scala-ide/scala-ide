package org.scalaide.core.internal.jdt.model

import java.util.{ HashMap => JHashMap }
import java.util.{ Map => JMap }
import org.eclipse.core.resources.IMarker
import org.eclipse.core.resources.IResource
import org.eclipse.core.runtime.IAdaptable
import org.eclipse.core.runtime.IPath
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.core.runtime.IStatus
import org.eclipse.core.runtime.jobs.ISchedulingRule
import org.eclipse.jdt.core.BufferChangedEvent
import org.eclipse.jdt.core.CompletionRequestor
import org.eclipse.jdt.core.IBuffer
import org.eclipse.jdt.core.ICompilationUnit
import org.eclipse.jdt.core.IImportContainer
import org.eclipse.jdt.core.IImportDeclaration
import org.eclipse.jdt.core.IJavaElement
import org.eclipse.jdt.core.IJavaModel
import org.eclipse.jdt.core.IJavaProject
import org.eclipse.jdt.core.IOpenable
import org.eclipse.jdt.core.IPackageDeclaration
import org.eclipse.jdt.core.IProblemRequestor
import org.eclipse.jdt.core.ISourceRange
import org.eclipse.jdt.core.IType
import org.eclipse.jdt.core.ITypeRoot
import org.eclipse.jdt.core.WorkingCopyOwner
import org.eclipse.jdt.core.dom.CompilationUnit
import org.eclipse.jdt.internal.compiler.env
import org.eclipse.jdt.internal.core.BufferManager
import org.eclipse.jdt.internal.core.DefaultWorkingCopyOwner
import org.eclipse.jdt.internal.core.JavaElement
import org.eclipse.jdt.internal.core.Openable
import org.eclipse.jdt.internal.core.OpenableElementInfo
import org.eclipse.jdt.internal.core.PackageFragmentRoot
import org.eclipse.jdt.internal.core.util.MementoTokenizer
import org.eclipse.text.edits.TextEdit
import org.eclipse.text.edits.UndoEdit
import org.scalaide.util.internal.Suppress

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

  override def bufferChanged(e : BufferChangedEvent): Unit = { classFile.bufferChanged(e) }
  override def buildStructure(info : OpenableElementInfo, pm : IProgressMonitor, newElements : JMap[_, _], underlyingResource : IResource) : Boolean =
    classFile.buildStructure(info, pm, newElements, underlyingResource)
  override def canBeRemovedFromCache() : Boolean = classFile.canBeRemovedFromCache()
  override def canBufferBeRemovedFromCache(buffer : IBuffer) : Boolean = classFile.canBufferBeRemovedFromCache(buffer)
  override def closeBuffer(): Unit = { classFile.closeBuffer0() }
  override def closing(info : AnyRef): Unit = { classFile.closing0(info) }
  override def codeComplete(
    cu : env.ICompilationUnit,
    unitToSkip : env.ICompilationUnit,
    position : Int,
    requestor : CompletionRequestor,
    owner : WorkingCopyOwner,
    typeRoot : ITypeRoot,
    monitor : IProgressMonitor): Unit = {
    classFile.codeComplete(cu, unitToSkip, position, requestor, owner, typeRoot, monitor)
  }
  override def codeSelect(cu : env.ICompilationUnit, offset : Int, length : Int, owner : WorkingCopyOwner) : Array[IJavaElement] =
    classFile.codeSelect(cu, offset, length, owner)
  override def createElementInfo() : AnyRef = classFile.createElementInfo0()
  override def generateInfos(info : AnyRef, newElements : JHashMap[_, _], monitor : IProgressMonitor) =
    classFile.generateInfos0(info, newElements, monitor)
  override def getBufferFactory() : Suppress.DeprecatedWarning.IBufferFactory = Suppress.DeprecatedWarning.getBufferFactory(classFile)
  override def getBufferManager() : BufferManager = classFile.getBufferManager0()
  override def hasBuffer() : Boolean = classFile.hasBuffer0()
  override def isSourceElement() : Boolean = classFile.isSourceElement0()
  override def openBuffer(pm : IProgressMonitor, info : Object) : IBuffer = classFile.openBuffer0(pm, info)
  override def resource() : IResource = classFile.resource()
  override def resource(root : PackageFragmentRoot) : IResource = classFile.resource(root)
  override def resourceExists(underlyingResource : IResource) : Boolean = classFile.resourceExists0(underlyingResource)
  override def getPackageFragmentRoot() : PackageFragmentRoot = classFile.getPackageFragmentRoot()
  override def validateExistence(underlyingResource : IResource) : IStatus = classFile.validateExistence0(underlyingResource)
  override def openAncestors(newElements : JHashMap[_, _], monitor : IProgressMonitor): Unit = { classFile.openAncestors0(newElements, monitor) }

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

  override def close(): Unit = { classFile.close() }
  override def findRecommendedLineSeparator() : String = classFile.findRecommendedLineSeparator()
  override def getBuffer() : IBuffer = classFile.getBuffer()
  override def hasUnsavedChanges() : Boolean = classFile.hasUnsavedChanges()
  override def isConsistent() : Boolean = classFile.isConsistent()
  override def isOpen() : Boolean = classFile.isOpen()
  override def makeConsistent(progress : IProgressMonitor): Unit = { classFile.makeConsistent(progress) }
  override def open(progress : IProgressMonitor): Unit = { classFile.open(progress) }
  override def save(progress : IProgressMonitor, force : Boolean): Unit = { classFile.save(progress, force) }

  def getSource() : String = classFile.getSource()
  def getSourceRange() : ISourceRange = classFile.getSourceRange()

  def codeComplete(offset : Int, requestor : Suppress.DeprecatedWarning.ICodeCompletionRequestor): Unit = { Suppress.DeprecatedWarning.codeComplete(classFile, offset, requestor) }
  def codeComplete(offset : Int, requestor : Suppress.DeprecatedWarning.ICompletionRequestor): Unit = { Suppress.DeprecatedWarning.codeComplete(classFile, offset, requestor) }
  def codeComplete(offset : Int, requestor : CompletionRequestor): Unit = { classFile.codeComplete(offset, requestor) }
  def codeComplete(offset : Int, requestor : CompletionRequestor, monitor : IProgressMonitor): Unit = { classFile.codeComplete(offset, requestor, monitor) }
  def codeComplete(offset : Int, requestor : Suppress.DeprecatedWarning.ICompletionRequestor, owner : WorkingCopyOwner): Unit = { Suppress.DeprecatedWarning.codeComplete(classFile, offset, requestor, owner) }
  def codeComplete(offset : Int, requestor : CompletionRequestor, owner : WorkingCopyOwner): Unit = { classFile.codeComplete(offset, requestor, owner) }
  def codeComplete(offset : Int, requestor : CompletionRequestor, owner : WorkingCopyOwner, monitor : IProgressMonitor): Unit = { classFile.codeComplete(offset, requestor, owner, monitor) }
  def codeSelect(offset : Int, length : Int) : Array[IJavaElement] = classFile.codeSelect(offset, length)
  def codeSelect(offset : Int, length : Int, owner : WorkingCopyOwner) : Array[IJavaElement] = classFile.codeSelect(offset, length, owner)

  def findPrimaryType() : IType = classFile.findPrimaryType()
  def getElementAt(position : Int) : IJavaElement = classFile.getElementAt(position)
  def getWorkingCopy(owner : WorkingCopyOwner, monitor : IProgressMonitor) : ICompilationUnit = classFile.getWorkingCopy(owner, monitor)

  def commit(force : Boolean, monitor : IProgressMonitor): Unit = { throw new UnsupportedOperationException }
  def destroy(): Unit = { throw new UnsupportedOperationException }
  def findSharedWorkingCopy(bufferFactory : Suppress.DeprecatedWarning.IBufferFactory) : IJavaElement = { throw new UnsupportedOperationException }
  def getOriginal(workingCopyElement : IJavaElement) : IJavaElement = { throw new UnsupportedOperationException }
  def getOriginalElement() : IJavaElement = throw new UnsupportedOperationException
  def findElements(element : IJavaElement) : Array[IJavaElement] = throw new UnsupportedOperationException
  def getSharedWorkingCopy(
    monitor : IProgressMonitor,
    factory : Suppress.DeprecatedWarning.IBufferFactory,
    problemRequestor : IProblemRequestor) : IJavaElement = classFile.getWorkingCopy(null, null : IProgressMonitor)
  def getWorkingCopy() : IJavaElement = classFile.getWorkingCopy(null, null : IProgressMonitor)
  def getWorkingCopy(
    monitor : IProgressMonitor,
    factory : Suppress.DeprecatedWarning.IBufferFactory,
    problemRequestor : IProblemRequestor) : IJavaElement = Suppress.DeprecatedWarning.getWorkingCopy(classFile, monitor, factory)
  def isBasedOn(resource : IResource) : Boolean = throw new UnsupportedOperationException
  def isWorkingCopy() : Boolean = false
  def reconcile() : Array[IMarker] = throw new UnsupportedOperationException
  def reconcile(forceProblemDetection : Boolean, monitor : IProgressMonitor): Unit = { throw new UnsupportedOperationException }
  def restore(): Unit = { throw new UnsupportedOperationException }

  def copy(container : IJavaElement, sibling : IJavaElement, rename : String, replace : Boolean, monitor : IProgressMonitor): Unit = { throw new UnsupportedOperationException }
  def delete(force : Boolean, monitor : IProgressMonitor): Unit = { throw new UnsupportedOperationException }
  def move(container : IJavaElement, sibling : IJavaElement, rename : String, replace : Boolean, monitor : IProgressMonitor): Unit = { throw new UnsupportedOperationException }
  def rename(name : String, replace : Boolean, monitor : IProgressMonitor): Unit = { throw new UnsupportedOperationException }

  def applyTextEdit(edit : TextEdit, monitor : IProgressMonitor) : UndoEdit = throw new UnsupportedOperationException
  def becomeWorkingCopy(problemRequestor : IProblemRequestor, monitor : IProgressMonitor): Unit = { classFile.becomeWorkingCopy(problemRequestor, null, monitor) }
  def becomeWorkingCopy(monitor : IProgressMonitor): Unit = { classFile.becomeWorkingCopy(null, null, monitor) }
  def commitWorkingCopy(force : Boolean, monitor : IProgressMonitor): Unit = {}
  def createImport(name : String, sibling : IJavaElement, monitor : IProgressMonitor) : IImportDeclaration = throw new UnsupportedOperationException
  def createImport(name : String, sibling : IJavaElement, flags : Int, monitor : IProgressMonitor) : IImportDeclaration = throw new UnsupportedOperationException
  def createPackageDeclaration(name : String, monitor : IProgressMonitor) : IPackageDeclaration = throw new UnsupportedOperationException
  def createType(contents : String, sibling : IJavaElement, force : Boolean, monitor : IProgressMonitor) : IType = throw new UnsupportedOperationException
  def discardWorkingCopy(): Unit = {}
  def findWorkingCopy(owner : WorkingCopyOwner) : ICompilationUnit = null
  def getAllTypes() : Array[IType] = Array(classFile.getType())
  def getImport(name : String) : IImportDeclaration = throw new UnsupportedOperationException
  def getImportContainer() : IImportContainer = throw new UnsupportedOperationException
  def getImports() : Array[IImportDeclaration] = Array()
  def getPrimary() : ICompilationUnit = this
  def getOwner() : WorkingCopyOwner = DefaultWorkingCopyOwner.PRIMARY
  def getPackageDeclaration(name : String) : IPackageDeclaration = throw new UnsupportedOperationException
  def getPackageDeclarations() : Array[IPackageDeclaration] = Array(new CompilationUnitAdapter.ScalaPackageDeclaration(classFile.getPackage))
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

object CompilationUnitAdapter {
  import org.eclipse.jdt.internal.core.PackageFragment
  import org.eclipse.jdt.core.IAnnotation

  private class ScalaPackageDeclaration(_package: PackageFragment) extends IPackageDeclaration {
    override def getElementName(): String = _package.getElementName()

    override def getAnnotation(name: String): IAnnotation = throw new UnsupportedOperationException
    override def getAnnotations(): Array[IAnnotation] = throw new UnsupportedOperationException
    override def getNameRange: ISourceRange = throw new UnsupportedOperationException
    override def getSourceRange: ISourceRange = throw new UnsupportedOperationException
    override def getSource: String = throw new UnsupportedOperationException
    override def exists(): Boolean = throw new UnsupportedOperationException

    override def isStructureKnown: Boolean = throw new UnsupportedOperationException
    override def isReadOnly: Boolean = throw new UnsupportedOperationException
    override def getUnderlyingResource: IResource = throw new UnsupportedOperationException
    override def getSchedulingRule: ISchedulingRule = throw new UnsupportedOperationException
    override def getResource: IResource = throw new UnsupportedOperationException
    override def getPrimaryElement: IJavaElement = throw new UnsupportedOperationException
    override def getPath: IPath = throw new UnsupportedOperationException
    override def getParent: IJavaElement = throw new UnsupportedOperationException
    override def getOpenable: IOpenable = throw new UnsupportedOperationException
    override def getJavaProject: IJavaProject = throw new UnsupportedOperationException
    override def getJavaModel: IJavaModel = throw new UnsupportedOperationException
    override def getHandleIdentifier: String = throw new UnsupportedOperationException
    override def getElementType: Int = throw new UnsupportedOperationException
    override def getCorrespondingResource: IResource = throw new UnsupportedOperationException
    override def getAttachedJavadoc(monitor: IProgressMonitor): String = throw new UnsupportedOperationException
    override def getAncestor(ancestorType: Int): IJavaElement = throw new UnsupportedOperationException

    override def getAdapter(adapter: Class[_]): AnyRef = throw new UnsupportedOperationException
  }
}
