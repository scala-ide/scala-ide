package scala.tools.eclipse
package editor.text

import org.eclipse.ui.editors.text.TextEditor
import org.eclipse.ui.editors.text.FileDocumentProvider
import org.eclipse.jface.text.rules.RuleBasedPartitionScanner
import scala.collection.mutable.ArrayBuffer
import org.eclipse.jface.text.rules.IPredicateRule
import org.eclipse.jface.text.rules.Token
import org.eclipse.jface.text.rules.MultiLineRule
import org.eclipse.jface.text.rules.EndOfLineRule
import org.eclipse.jface.text.rules.SingleLineRule
import org.eclipse.jdt.ui.{JavaUI, PreferenceConstants}
import org.eclipse.ui.texteditor.DocumentProviderRegistry
import org.eclipse.core.resources.IFile

class ScalaTextEditor extends TextEditor {

  override protected def initializeEditor() {
    super.initializeEditor()
    setSourceViewerConfiguration(new ScalaTextSourceViewerConfiguration(this, JavaUI.getColorManager, PreferenceConstants.getPreferenceStore))
    val dp = DocumentProviderRegistry.getDefault().getDocumentProvider("scala")
    println(">>>>>>>>>> dp : " + dp.getClass + " .. " + dp)
    setDocumentProvider(dp)
    setPartName("Scala Editor") // like ScalaSourceFileEcitor
  }

  /**
   * Initializes the key binding scopes of this editor.
   */
  override protected def initializeKeyBindingScopes() {
    super.initializeKeyBindingScopes()
    setKeyBindingScopes(Array("org.eclipse.ui.textEditorScope", ScalaSourceFileEditor.SCALA_EDITOR_SCOPE))
  }

  override protected def createActions() {
    super.createActions()

//    val cutAction = new TextOperationAction(bundleForConstructedKeys, "Editor.Cut.", this, ITextOperationTarget.CUT) //$NON-NLS-1$
//    cutAction.setHelpContextId(IAbstractTextEditorHelpContextIds.CUT_ACTION)
//    cutAction.setActionDefinitionId(IWorkbenchActionDefinitionIds.CUT)
//    setAction(ITextEditorActionConstants.CUT, cutAction)
//
//    val copyAction = new TextOperationAction(bundleForConstructedKeys, "Editor.Copy.", this, ITextOperationTarget.COPY, true) //$NON-NLS-1$
//    copyAction.setHelpContextId(IAbstractTextEditorHelpContextIds.COPY_ACTION)
//    copyAction.setActionDefinitionId(IWorkbenchActionDefinitionIds.COPY)
//    setAction(ITextEditorActionConstants.COPY, copyAction)
//
//    val pasteAction = new TextOperationAction(bundleForConstructedKeys, "Editor.Paste.", this, ITextOperationTarget.PASTE) //$NON-NLS-1$
//    pasteAction.setHelpContextId(IAbstractTextEditorHelpContextIds.PASTE_ACTION)
//    pasteAction.setActionDefinitionId(IWorkbenchActionDefinitionIds.PASTE)
//    setAction(ITextEditorActionConstants.PASTE, pasteAction)
//
//    val selectionHistory = new SelectionHistory(this)

//    val historyAction = new StructureSelectHistoryAction(this, selectionHistory)
//    historyAction.setActionDefinitionId(IJavaEditorActionDefinitionIds.SELECT_LAST)
//    setAction(StructureSelectionAction.HISTORY, historyAction)
//    selectionHistory.setHistoryAction(historyAction)

//    val selectEnclosingAction = new ScalaStructureSelectEnclosingAction(this, selectionHistory)
//    selectEnclosingAction.setActionDefinitionId(IJavaEditorActionDefinitionIds.SELECT_ENCLOSING)
//    setAction(StructureSelectionAction.ENCLOSING, selectEnclosingAction)

  }

  //    protected ResourceBundle getBundle() {
  //        //return JavaEditorMessages.getBundleForConstructedKeys();
  //        return Messages.getResourceBundle();
  //    }
  //    /*
  //     * @see AbstractTextEditor#createActions()
  //     */
  //    protected void createActions() {
  //
  //        super.createActions();
  //
  //        IAction action= getAction(ITextEditorActionConstants.CONTENT_ASSIST_CONTEXT_INFORMATION);
  //        if (action != null) {
  //            PlatformUI.getWorkbench().getHelpSystem().setHelp(action, IJavaHelpContextIds.PARAMETER_HINTS_ACTION);
  //            action.setText(getBundle().getString("ContentAssistContextInformation.label")); //$NON-NLS-1$
  //            action.setToolTipText(getBundle().getString("ContentAssistContextInformation.tooltip")); //$NON-NLS-1$
  //            action.setDescription(getBundle().getString("ContentAssistContextInformation.description")); //$NON-NLS-1$
  //        }
  //
  //        action= new TextOperationAction(getBundle(), "Uncomment.", this, ITextOperationTarget.STRIP_PREFIX); //$NON-NLS-1$
  //        action.setActionDefinitionId(IJavaEditorActionDefinitionIds.UNCOMMENT);
  //        setAction("Uncomment", action); //$NON-NLS-1$
  //        markAsStateDependentAction("Uncomment", true); //$NON-NLS-1$
  //        PlatformUI.getWorkbench().getHelpSystem().setHelp(action, IJavaHelpContextIds.UNCOMMENT_ACTION);
  //
  //        action= new ToggleCommentAction(getBundle(), "ToggleComment.", this); //$NON-NLS-1$
  //        action.setActionDefinitionId(IJavaEditorActionDefinitionIds.TOGGLE_COMMENT);
  //        setAction("ToggleComment", action); //$NON-NLS-1$
  //        markAsStateDependentAction("ToggleComment", true); //$NON-NLS-1$
  //        PlatformUI.getWorkbench().getHelpSystem().setHelp(action, IJavaHelpContextIds.TOGGLE_COMMENT_ACTION);
  //        configureToggleCommentAction();
  //
  //        action= new AddBlockCommentAction(getBundle(), "AddBlockComment.", this);  //$NON-NLS-1$
  //        action.setActionDefinitionId(IJavaEditorActionDefinitionIds.ADD_BLOCK_COMMENT);
  //        setAction("AddBlockComment", action); //$NON-NLS-1$
  //        markAsStateDependentAction("AddBlockComment", true); //$NON-NLS-1$
  //        markAsSelectionDependentAction("AddBlockComment", true); //$NON-NLS-1$
  //        PlatformUI.getWorkbench().getHelpSystem().setHelp(action, IJavaHelpContextIds.ADD_BLOCK_COMMENT_ACTION);
  //
  //        action= new RemoveBlockCommentAction(getBundle(), "RemoveBlockComment.", this);  //$NON-NLS-1$
  //        action.setActionDefinitionId(IJavaEditorActionDefinitionIds.REMOVE_BLOCK_COMMENT);
  //        setAction("RemoveBlockComment", action); //$NON-NLS-1$
  //        markAsStateDependentAction("RemoveBlockComment", true); //$NON-NLS-1$
  //        markAsSelectionDependentAction("RemoveBlockComment", true); //$NON-NLS-1$
  //        PlatformUI.getWorkbench().getHelpSystem().setHelp(action, IJavaHelpContextIds.REMOVE_BLOCK_COMMENT_ACTION);
  //
  //        action= new IndentAction(getBundle(), "Indent.", this, false); //$NON-NLS-1$
  //        action.setActionDefinitionId(IJavaEditorActionDefinitionIds.INDENT);
  //        setAction("Indent", action); //$NON-NLS-1$
  //        markAsStateDependentAction("Indent", true); //$NON-NLS-1$
  //        markAsSelectionDependentAction("Indent", true); //$NON-NLS-1$
  //        PlatformUI.getWorkbench().getHelpSystem().setHelp(action, IJavaHelpContextIds.INDENT_ACTION);
  //
  //        action= new IndentAction(getBundle(), "Indent.", this, true); //$NON-NLS-1$
  //        setAction("IndentOnTab", action); //$NON-NLS-1$
  //        markAsStateDependentAction("IndentOnTab", true); //$NON-NLS-1$
  //        markAsSelectionDependentAction("IndentOnTab", true); //$NON-NLS-1$
  //
  //
  //        if (getPreferenceStore().getBoolean(PreferenceConstants.EDITOR_SMART_TAB)) {
  //            // don't replace Shift Right - have to make sure their enablement is mutually exclusive
  ////      removeActionActivationCode(ITextEditorActionConstants.SHIFT_RIGHT);
  //            setActionActivationCode("IndentOnTab", '\t', -1, SWT.NONE); //$NON-NLS-1$
  //        }
  //
  ////    fGenerateActionGroup= new GenerateActionGroup(this, ITextEditorActionConstants.GROUP_EDIT);
  ////    fRefactorActionGroup= new RefactorActionGroup(this, ITextEditorActionConstants.GROUP_EDIT, false);
  ////    ActionGroup surroundWith= new SurroundWithActionGroup(this, ITextEditorActionConstants.GROUP_EDIT);
  ////
  ////    fActionGroups.addGroup(surroundWith);
  ////    fActionGroups.addGroup(fRefactorActionGroup);
  ////    fActionGroups.addGroup(fGenerateActionGroup);
  //
  ////    // We have to keep the context menu group separate to have better control over positioning
  ////    fContextMenuGroup= new CompositeActionGroup(new ActionGroup[] {
  ////      fGenerateActionGroup,
  ////      fRefactorActionGroup,
  ////      surroundWith,
  ////      new LocalHistoryActionGroup(this, ITextEditorActionConstants.GROUP_EDIT)});
  ////
  ////    fCorrectionCommands= new CorrectionCommandInstaller(); // allow shortcuts for quick fix/assist
  ////    fCorrectionCommands.registerCommands(this);
  //    }
  //
  //    /**
  //     * Configures the toggle comment action
  //     *
  //     * @since 3.0
  //     */
  //    private void configureToggleCommentAction() {
  //        IAction action= getAction("ToggleComment"); //$NON-NLS-1$
  //        if (action instanceof ToggleCommentAction) {
  //            ISourceViewer sourceViewer= getSourceViewer();
  //            SourceViewerConfiguration configuration= getSourceViewerConfiguration();
  //            ((ToggleCommentAction)action).configure(sourceViewer, configuration);
  //        }
  //    }
  //
  //    /*
  //     * @see org.eclipse.ui.texteditor.AbstractTextEditor#installTabsToSpacesConverter()
  //     * @since 3.3
  //     */
  //    protected void installTabsToSpacesConverter() {
  //        ISourceViewer sourceViewer= getSourceViewer();
  //        SourceViewerConfiguration config= getSourceViewerConfiguration();
  //        if (config != null && sourceViewer instanceof ITextViewerExtension7) {
  //            int tabWidth= config.getTabWidth(sourceViewer);
  //            TabsToSpacesConverter tabToSpacesConverter= new TabsToSpacesConverter();
  //            tabToSpacesConverter.setNumberOfSpacesPerTab(tabWidth);
  //            IDocumentProvider provider= getDocumentProvider();
  //            if (provider instanceof ICompilationUnitDocumentProvider) {
  //                ICompilationUnitDocumentProvider cup= (ICompilationUnitDocumentProvider) provider;
  //                tabToSpacesConverter.setLineTracker(cup.createLineTracker(getEditorInput()));
  //            } else
  //                tabToSpacesConverter.setLineTracker(new DefaultLineTracker());
  //            ((ITextViewerExtension7)sourceViewer).setTabsToSpacesConverter(tabToSpacesConverter);
  //            updateIndentPrefixes();
  //        }
  //    }
  //
  //    /*
  //     * @see org.eclipse.ui.texteditor.AbstractDecoratedTextEditor#isTabsToSpacesConversionEnabled()
  //     * @since 3.3
  //     */
  //    protected boolean isTabsToSpacesConversionEnabled() {
  //        return true;
  //    }

  //  /**
  //   * Returns the refactor action group.
  //   *
  //   * @return the refactor action group, or <code>null</code> if there is none
  //   * @since 3.5
  //   */
  //  protected RefactorActionGroup getRefactorActionGroup() {
  //    return fRefactorActionGroup;
  //  }
  //
  //  /**
  //   * Returns the generate action group.
  //   *
  //   * @return the generate action group, or <code>null</code> if there is none
  //   * @since 3.5
  //   */
  //  protected GenerateActionGroup getGenerateActionGroup() {
  //    return fGenerateActionGroup;
  //  }
}

class ScalaDocumentProvider extends FileDocumentProvider {
  import org.eclipse.core.runtime.CoreException
  import org.eclipse.jface.text.IDocument
  import org.eclipse.jface.text.IDocumentPartitioner
  import org.eclipse.jface.text.rules.FastPartitioner

  override protected def createDocument(element: Object): IDocument = {
    val document = super.createDocument(element)
    if (document != null) {
      val partitioner = new FastPartitioner(new Scanner4Partition(), Scanner4Partition.PARTITION_TYPES)
      partitioner.connect(document)
      document.setDocumentPartitioner(partitioner)
    }
    document
  }
  
//  def getFile(d: IDocument) : Option[IFile] = {
//    import scala.collection.JavaConversions._
//    import org.eclipse.ui.IFileEditorInput
//    //val it : Iterator[Object] = asScalaIterator[Object](getConnectedElements().asInstanceOf[java.util.Iterator[Object]])
//    getConnectedElements().collect{
//      case x : IFileEditorInput if ( getElementInfo( x ).fDocument  == d) => x.getFile
//    }.toList.headOption
//  } 
}

protected class Scanner4Partition extends RuleBasedPartitionScanner {
  import Scanner4Partition._
  init()
  //string constants for different partition types

  def init() {
    import java.util.ArrayList

    val rules = new ArrayBuffer[IPredicateRule]()

    // Add rules for multi-line comments and scaladoc.
    val multilinecomment = new Token(MULTILINE_COMMENT);
    rules += new MultiLineRule("/*", "*/", multilinecomment, 0, true)

    // Add rule for single line comments.
    val singlelinecomment = new Token(SINGLELINE_COMMENT)
    rules += new EndOfLineRule("//", singlelinecomment)

    // Add rule for strings and character constants.
    val string = new Token(STRING)
    rules += new MultiLineRule("\"\"\"", "\"\"\"", string)
    rules += new SingleLineRule("\"", "\"", string, '\\')
    rules += new SingleLineRule("'", "'", string, '\\')

    setPredicateRules(rules.toArray)
  }
}

// TODO merge with scala.tools.eclipse.lexical.ScalaPartitions
// TODO replace by org.eclipse.jdt.ui.text.IJavaPartitions ??

object Scanner4Partition {
  val MULTILINE_COMMENT = "multiline_comment" //$NON-NLS-1$
  val SINGLELINE_COMMENT = "singleline_comment" //$NON-NLS-1$
  val STRING = "string" //$NON-NLS-1$
  val PARTITION_TYPES = Array[String](MULTILINE_COMMENT, SINGLELINE_COMMENT, STRING)
}