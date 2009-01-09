/*
 * Copyright 2005-2009 LAMP/EPFL
 * @author Sean McDirmid
 */
// $Id$

package scala.tools.eclipse.debug;
import org.eclipse.core.resources.{IFile,IResource,ResourcesPlugin}
import org.eclipse.core.runtime.{CoreException,IAdaptable,IProgressMonitor,IStatus,Status,IAdapterFactory}
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.debug.core.{DebugException,DebugPlugin,IBreakpointManager}
import org.eclipse.debug.core.model.IBreakpoint;
import org.eclipse.debug.ui.actions.{IToggleBreakpointsTargetExtension,IToggleBreakpointsTarget}
import org.eclipse.jdt.ui.{IWorkingCopyManager,JavaUI}
import org.eclipse.jdt.core.{Flags,IClassFile,ICompilationUnit,IField,IJavaElement,IMember,IMethod,ISourceRange,IType,JavaModelException,Signature}
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.debug.core.{IJavaBreakpoint,IJavaFieldVariable,IJavaLineBreakpoint,IJavaMethodBreakpoint,IJavaType,IJavaWatchpoint,JDIDebugModel}
import org.eclipse.jdt.internal.debug.core.JavaDebugUtils;
import org.eclipse.jdt.internal.debug.ui.{BreakpointUtils,JDIDebugUIPlugin,LocalFileStorageEditorInput,ZipEntryStorageEditorInput}
import org.eclipse.jdt.internal.debug.ui.actions.{ActionMessages,BreakpointMethodLocator}
import org.eclipse.jface.text.{BadLocationException,IDocument,IRegion,ITextSelection}
import org.eclipse.jface.viewers.{ISelection,IStructuredSelection,StructuredSelection}
import org.eclipse.ui.{IEditorInput,IEditorPart,IWorkbenchPart}
import org.eclipse.ui.texteditor.{IDocumentProvider,IEditorStatusLine,ITextEditor}
import org.eclipse.ui.editors.text.ILocationProvider
import scala.collection.jcl._

class BreakpointAdapterFactory extends IAdapterFactory {
  def getAdapter(adaptableObject : AnyRef, adapterType : java.lang.Class[_]) = adaptableObject match {
  case editor : scala.tools.eclipse.Editor => 
    //val input = editor.getEditorInput
    //val resource = input.getAdapter(classOf[IResource]).asInstanceOf[IResource]
    //if (resource != null) 
    new ToggleBreakpointAdapter
    //else null
  case _ => null
  }
  def getAdapterList = Array(classOf[IToggleBreakpointsTarget])
  class ToggleBreakpointAdapter extends IToggleBreakpointsTargetExtension {
    def canToggleBreakpoints(part : IWorkbenchPart, selection : ISelection) = true
    def canToggleMethodBreakpoints(part : IWorkbenchPart, selection : ISelection) = false
    def canToggleWatchpoints(part : IWorkbenchPart, selection : ISelection) = selection match {
    case selection : ITextSelection if !isRemote(part, selection) => true
    case _ => false
    }
    private def getTextEditor(part : IWorkbenchPart) = part match {
    case part : Editor => part
    case part => part.getAdapter(classOf[ITextEditor]) match {
      case part : Editor => part
      case _ => null
      }
    }
    def canToggleLineBreakpoints(part : IWorkbenchPart, selection : ISelection) = {
      if (isRemote(part, selection)) false
      else getTextEditor(part) != null
    }
    def toggleBreakpoints(part : IWorkbenchPart, selection : ISelection) = toggleLineBreakpoints(part, selection)

    /**
     * Returns whether the given part/selection is remote (viewing a repsitory)
     */
    private def isRemote(part : IWorkbenchPart, selection : ISelection) : Boolean = {
      selection match {
      case selection : IStructuredSelection => selection.getFirstElement match {
        case element : IAdaptable => 
          val javaElement = element.getAdapter(classOf[IJavaElement]).asInstanceOf[IJavaElement]
          return javaElement == null || !javaElement.getJavaProject.exists
        case _ =>
        }
      case _ => 
      }
      val editor = getTextEditor(part)
      if (editor == null) return false
      val element = editor.getEditorInput.getAdapter(classOf[IJavaElement]).asInstanceOf[IJavaElement]
      if (element != null) return false
      // try to determine remote vs local but not in the workspace
      editor.getEditorInput match {
      case _ : LocalFileStorageEditorInput | _ : ZipEntryStorageEditorInput => return false
      case _ => 
      }
      val provider = editor.getEditorInput.getAdapter(classOf[ILocationProvider]).asInstanceOf[ILocationProvider]
      if (provider != null && provider.getPath(editor.getEditorInput) != null) return false
      return true
    }
    def toggleLineBreakpoints(part : IWorkbenchPart, selection : ISelection) = {
      val job = new Job("Toggle line breakpoint") {
        protected def run(monitor : IProgressMonitor) : IStatus = {
          (getTextEditor(part),selection) match {
          case (null,_) => 
          case (editor,selection:ITextSelection) => 
            val editorInput = editor.getEditorInput();
            val documentProvider = editor.getDocumentProvider();
            if (documentProvider == null || (monitor != null && monitor.isCanceled)) 
              return Status.CANCEL_STATUS
            val document = documentProvider.getDocument(editorInput)
            val lineNumber = selection.getStartLine + 1
            val offset = selection.getOffset
            try {
              report(null, part)
              val attributes = new LinkedHashMap[Object,Object]
              val plugin = ScalaUIPlugin.plugin
              editorInput match {
              case input : plugin.ClassFileInput => 
                BreakpointUtils.addJavaBreakpointAttributesWithMemberDetails(attributes.underlying, input.classFile, -1, -1)
              case _ => 
              }
              val resource = (editor) : IResource
              val name = getTopLevelTypeName(editor, offset)
              if (resource != null && name != null) {
                val existing = JDIDebugModel
                  .lineBreakpointExists(resource, name, lineNumber)                
                if (existing != null) {
                  removeBreakpoint(existing,true)
                  return Status.OK_STATUS
                }
                createLineBreakpoint(resource, name, lineNumber, -1, -1, 0,
                    true, attributes, document, editor)
              }
            } catch {
              case ce : CoreException => 
                ScalaPlugin.plugin.logError(ce)
                return ce.getStatus
            }
            
            ()
          }
          Status.OK_STATUS
        }
      }
      job.setSystem(true)
      job.schedule
    }
    private def report(msg : String,  part : IWorkbenchPart ) = {}
    
    def toggleWatchpoints(part : IWorkbenchPart, selection : ISelection) = {}
    def toggleMethodBreakpoints(part : IWorkbenchPart, selection : ISelection) = {}

  }
  implicit private def getResource(editor : IEditorPart): IResource = {
    editor.getEditorInput.getAdapter(classOf[IFile]) match {
      case null => ResourcesPlugin.getWorkspace.getRoot
      case resource : IResource => resource
    }
  }
  private def getTopLevelTypeName(editor : ITextEditor, offset : Int) : String = editor match {
  case editor : eclipse.Editor => 
    val viewer = editor.getSourceViewer0
    val file = viewer.file.get
    import scala.tools.editor._
    val project = file.project.asInstanceOf[TypersPresentations#ProjectImpl]
    val file0 = file.asInstanceOf[project.FileImpl].self
    import org.eclipse.swt.widgets.Display
    var answer : String = null
    Display.getDefault.syncExec(new Runnable {
      def run = {
        val tok = file0.tokenForFuzzy(offset)
        answer = tok.enclosingDefinition match {
      case Some(sym) =>  
        val sym0 = sym.toplevelClass
        var name = sym0.fullNameString('.') 
        if (sym0.isModuleClass) name = name + "$"
        name
      case None => null
      }}
    })
    answer
  }
  private def createLineBreakpoint(resource : IResource, typeName : String, lineNumber : Int, charStart : Int, charEnd : Int, hitCount : Int,
       register : Boolean, attributes : LinkedHashMap[Object,Object], document : IDocument, editorPart : IEditorPart) = {
    val breakpoint = JDIDebugModel.createLineBreakpoint(
        resource, typeName, lineNumber, charStart, charEnd, hitCount, register,
        attributes.underlying)
    new BreakpointLocationVerifierJob(document, breakpoint, lineNumber,
        typeName, resource, editorPart).schedule()
  }

  private def removeBreakpoint(breakpoint : IBreakpoint, delete : Boolean) = 
    DebugPlugin.getDefault.getBreakpointManager.removeBreakpoint(breakpoint, delete)
    
  private class BreakpointLocationVerifierJob(document : IDocument, breakpoint : Any, lineNumber : Int, typeName : String, resource : IResource, editorPart : IEditorPart) extends Job("Add breakpoint")  {
    def run(monitor : IProgressMonitor) : IStatus = Status.OK_STATUS
  }
    
}
