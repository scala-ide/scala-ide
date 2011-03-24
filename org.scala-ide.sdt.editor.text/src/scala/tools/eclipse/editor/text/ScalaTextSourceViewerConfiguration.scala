package scala.tools.eclipse
package editor.text

import org.eclipse.jdt.core.ICodeAssist
import org.eclipse.jdt.ui.text.IColorManager
import org.eclipse.jdt.ui.text.IJavaColorConstants
import org.eclipse.jface.preference.IPreferenceStore
import org.eclipse.jface.text.IAutoEditStrategy
import org.eclipse.jface.text.IDocument
import org.eclipse.jface.text.TextAttribute
import org.eclipse.jface.text.contentassist.ContentAssistant
import org.eclipse.jface.text.contentassist.IContentAssistant
import org.eclipse.jface.text.presentation.IPresentationReconciler
import org.eclipse.jface.text.presentation.PresentationReconciler
import org.eclipse.jface.text.rules.DefaultDamagerRepairer
import org.eclipse.jface.text.source.ISourceViewer
import org.eclipse.ui.editors.text.TextSourceViewerConfiguration
import scala.tools.eclipse.lexical.ScalaCodeScanner
import scala.tools.eclipse.lexical.ScalaPartitions
import org.eclipse.jface.text.rules.ITokenScanner
import org.eclipse.jface.text.formatter.ContentFormatter
import scala.tools.eclipse.formatter.ScalaFormattingStrategy
import scala.tools.eclipse.text.scala.ScalaCompletionProcessor
import org.eclipse.jdt.internal.ui.text.java.SmartSemicolonAutoEditStrategy
import org.eclipse.jdt.internal.ui.text.java.JavaStringAutoIndentStrategy
import org.eclipse.jdt.internal.ui.text.javadoc.JavaDocAutoIndentStrategy
import scala.tools.eclipse.lexical.XmlPIScanner
import scala.tools.eclipse.lexical.XmlCDATAScanner
import scala.tools.eclipse.lexical.XmlTagScanner
import scala.tools.eclipse.lexical.XmlCommentScanner
import org.eclipse.ui.IEditorPart
import org.eclipse.jface.text.reconciler.IReconciler
import org.eclipse.jface.text.reconciler.MonoReconciler
import org.eclipse.jface.text.source.IAnnotationHover

protected class ScalaTextSourceViewerConfiguration(val editor : IEditorPart, val colorManager: IColorManager, val jdtPreferenceStore: IPreferenceStore) extends TextSourceViewerConfiguration(jdtPreferenceStore){

  private val _codeScanner = new ScalaCodeScanner(colorManager, jdtPreferenceStore)

  override def getPresentationReconciler(sv: ISourceViewer) = {

    val reconciler = super.getPresentationReconciler(sv) match {
      case null => new PresentationReconciler()
      case v: PresentationReconciler => v
      case _ => new PresentationReconciler()
    }

    reconciler.setDocumentPartitioning(getConfiguredDocumentPartitioning(sv))

    def handlePartition(partitionType: String, tokenScanner: ITokenScanner) {
      val dr = new DefaultDamagerRepairer(tokenScanner)
      reconciler.setDamager(dr, partitionType)
      reconciler.setRepairer(dr, partitionType)
    }
    def handlePartition2(partitionType: String, dr : NonRuleBasedDamagerRepairer) {
      reconciler.setDamager(dr, partitionType)
      reconciler.setRepairer(dr, partitionType)
    }
    handlePartition(IDocument.DEFAULT_CONTENT_TYPE, _codeScanner)

    handlePartition2(Scanner4Partition.MULTILINE_COMMENT, new NonRuleBasedDamagerRepairer(new TextAttribute(colorManager.getColor(IJavaColorConstants.JAVADOC_DEFAULT))))
    handlePartition2(Scanner4Partition.SINGLELINE_COMMENT, new NonRuleBasedDamagerRepairer(new TextAttribute(colorManager.getColor(IJavaColorConstants.JAVA_SINGLE_LINE_COMMENT))))
    handlePartition2(Scanner4Partition.STRING, new NonRuleBasedDamagerRepairer(new TextAttribute(colorManager.getColor(IJavaColorConstants.JAVA_STRING))))

    //TODO handlePartition(ScalaPartitions.SCALA_MULTI_LINE_STRING, getStringScanner())

    val scalaPreferenceStore = ScalaPlugin.plugin.getPreferenceStore
    handlePartition(ScalaPartitions.XML_TAG, new XmlTagScanner(colorManager, scalaPreferenceStore))
    handlePartition(ScalaPartitions.XML_COMMENT, new XmlCommentScanner(colorManager, scalaPreferenceStore))
    handlePartition(ScalaPartitions.XML_CDATA, new XmlCDATAScanner(colorManager, scalaPreferenceStore))
    handlePartition(ScalaPartitions.XML_PI, new XmlPIScanner(colorManager, scalaPreferenceStore))

    reconciler
  }

  override def getConfiguredContentTypes(sv: ISourceViewer) = Array[String](
    IDocument.DEFAULT_CONTENT_TYPE,
    Scanner4Partition.MULTILINE_COMMENT,
    Scanner4Partition.SINGLELINE_COMMENT,
    Scanner4Partition.STRING,
    ScalaPartitions.SCALA_MULTI_LINE_STRING,
    ScalaPartitions.XML_TAG,
    ScalaPartitions.XML_COMMENT,
    ScalaPartitions.XML_CDATA,
    ScalaPartitions.XML_PI)

  //  override def getAutoEditStrategies(sourceViewer : ISourceViewer, contentType : String) : Array[IAutoEditStrategy] = {
  //    //If the TabsToSpacesConverter is not configured here the editor field in the template
  //    //preference page will not work correctly. Without this configuration the tab key
  //    //will be not work correctly and instead change the focus
  //    val tabWidth= getTabWidth(sourceViewer)
  //    val tabToSpacesConverter = new TabsToSpacesConverter()
  //    tabToSpacesConverter.setLineTracker(new DefaultLineTracker())
  //    tabToSpacesConverter.setNumberOfSpacesPerTab(tabWidth)
  //
  //    if( IDocument.DEFAULT_CONTENT_TYPE.equals(contentType) ){
  //      return new IAutoEditStrategy[] {
  //                    new AutoInsertPair(Activator.getDefault().getPreferenceStore()),
  //                    new AutoIndentCodeStrategy(),
  //                    tabToSpacesConverter,
  //                    //new DefaultIndentLineAutoEditStrategy(),
  //            };
  //        } else if (Scanner4Partition.MULTILINE_COMMENT.equals(contentType)) {
  //            return new IAutoEditStrategy[] {
  //                    new AutoInsertPair(Activator.getDefault().getPreferenceStore()),
  //                    new AutoIndentDocStrategy(),
  //                    tabToSpacesConverter,
  //                    //new DefaultIndentLineAutoEditStrategy(),
  //            };
  //        } else {
  //            return new IAutoEditStrategy[]{ new DefaultIndentLineAutoEditStrategy() };
  //        }
  //    }

  /**
   * Replica of JavaSourceViewerConfiguration#getAutoEditStrategies that returns
   * a ScalaAutoIndentStrategy instead of a JavaAutoIndentStrategy.
   *
   * Goals  (on typing, on pasting):
   * * auto-insert  end of pair (), [], {}, "", (open/close comment)
   * * auto-convert tab to space
   * * auto-indent new line
   * * auto-insert double new line for pair
   * 
   * @see org.eclipse.jface.text.source.SourceViewerConfiguration#getAutoEditStrategies(org.eclipse.jface.text.source.ISourceViewer, java.lang.String)
   */
  override def getAutoEditStrategies(sourceViewer: ISourceViewer, contentType: String): Array[IAutoEditStrategy] = {
    val partitioning = getConfiguredDocumentPartitioning(sourceViewer)

    def defaultIndenters = Array[IAutoEditStrategy](
      //new ScalaAutoIndentStrategy(partitioning, project, sourceViewer, new JdtPreferenceProvider(project))
      //new AutoInsertPair(Activator.getDefault().getPreferenceStore()),
      new AutoIndentCodeStrategy()
      //tabToSpacesConverter,
      //new DefaultIndentLineAutoEditStrategy(),
    )
    contentType match {
      case Scanner4Partition.MULTILINE_COMMENT => Array(new JavaDocAutoIndentStrategy(partitioning))
      case Scanner4Partition.STRING => Array(new SmartSemicolonAutoEditStrategy(partitioning), new JavaStringAutoIndentStrategy(partitioning))
      case IDocument.DEFAULT_CONTENT_TYPE => new SmartSemicolonAutoEditStrategy(partitioning) +: defaultIndenters
      case _ => defaultIndenters
    }
  }

  override def getContentFormatter(sourceViewer: ISourceViewer) = {
    val contentFormatter = new ContentFormatter()
    contentFormatter.enablePartitionAwareFormatting(false)
    contentFormatter.setFormattingStrategy(new ScalaFormattingStrategy(sourceViewer), IDocument.DEFAULT_CONTENT_TYPE)
    contentFormatter
  }

  //
  override def getContentAssistant(sourceViewer: ISourceViewer): IContentAssistant = {
    val assistant = super.getContentAssistant(sourceViewer) match {
      case null => new ContentAssistant();
      case v :ContentAssistant => v
    }
    assistant.setDocumentPartitioning(getConfiguredDocumentPartitioning(sourceViewer))

    val scalaProcessor = new ScalaCompletionProcessor(editor, assistant, IDocument.DEFAULT_CONTENT_TYPE)
    assistant.setContentAssistProcessor(null, IDocument.DEFAULT_CONTENT_TYPE) // set null to remove previously set
    assistant.setContentAssistProcessor(scalaProcessor, IDocument.DEFAULT_CONTENT_TYPE)
    assistant.setProposalPopupOrientation(IContentAssistant.PROPOSAL_OVERLAY)
    assistant.setContextInformationPopupOrientation(IContentAssistant.CONTEXT_INFO_ABOVE)
    assistant.setInformationControlCreator(getInformationControlCreator(sourceViewer))
    
    //        IContentAssistProcessor processor= new MyTemplateCompletionProcessor();
    //        assistant.setContentAssistProcessor(processor, IDocument.DEFAULT_CONTENT_TYPE);
    //        assistant.setContextInformationPopupOrientation(IContentAssistant.CONTEXT_INFO_ABOVE);
    //        assistant.setInformationControlCreator(getInformationControlCreator(sourceViewer));
//    if (assistant == null) {
//        assistant = new ContentAssistant();
//        assistant.setDocumentPartitioning(getConfiguredDocumentPartitioning(sourceViewer));
//        assistant.setContentAssistProcessor(getMyAssistProcessor(), MyPartitionScanner.DESIRED_PARTITION_FOR_MY_ASSISTANCE);
//        assistant.enableAutoActivation(true);
//        assistant.setAutoActivationDelay(500);
//       assistant.setProposalPopupOrientation(IContentAssistant.PROPOSAL_OVERLAY);
//       assistant.setContextInformationPopupOrientation(IContentAssistant.CONTEXT_INFO_ABOVE);
//    }

    assistant
  }

  override def getTabWidth(sourceViewer: ISourceViewer) = 2 //TODO read from prefs
  //  
  //        IPreferenceStore prefs = Activator.getDefault().getPreferenceStore();
  //        return prefs.getInt(PreferenceConstants.FORMATTER_INDENTATION_SIZE.name());

  /**
   * This methods is necessary for proper implementation of Shift Left and Shift Right.
   *
   * This implementation overrides the default implementation to ensure that only spaces
   * are inserted and not tabs.
   *
   * @returns An array of prefixes. The prefix at position 0 is used when shifting right.
   * When shifting left all the prefixes are checked and one of the matches that prefix is
   * removed from the line.
   */
  override def getIndentPrefixes(sourceViewer: ISourceViewer, contentType: String): Array[String] = {
    val tabWidth = getTabWidth(sourceViewer)
    val indentPrefixes = new Array[String](tabWidth)
    //TODO scalaize
    var prefixLength = 1
    while (prefixLength <= tabWidth) {
      indentPrefixes(tabWidth - prefixLength) = " " * prefixLength 
      prefixLength += 1
    }
    indentPrefixes.toArray
  }

  /**
   * Returns the prefixes used when doing prefix operations (eg ToggleComment).
   * Without overriding this method ToggleComment will not work.
   */
  override def getDefaultPrefixes(sourceViewer: ISourceViewer, contentType: String) = Array("//", "")
  
//  /**
//   * Returns the quick assist assistant ready to be used with the given
//   * source viewer.
//   * This implementation always returns <code>null</code>.
//   *
//   * @param sourceViewer the source viewer to be configured by this configuration
//   * @return a quick assist assistant or <code>null</code> if quick assist should not be supported
//   * @since 3.2
//   */
//  public IQuickAssistAssistant getQuickAssistAssistant(ISourceViewer sourceViewer) {
//    return null;
//  }

  /**
   * Returns the reconciler ready to be used with the given source viewer.
   * This implementation always returns <code>null</code>.
   *
   * @param sourceViewer the source viewer to be configured by this configuration
   * @return a reconciler or <code>null</code> if reconciling should not be supported
   */
  override def getReconciler(sourceViewer : ISourceViewer) : IReconciler = new MonoReconciler(new ScalaTextReconcilingStrategy(sourceViewer), false)
  
//  /**
//   * Direct copy+paste of getProject from SourceViewerConfiguration.
//   * <grumble>No need for this to be _private_ in the parent class</grumble>
//   */
//  def project : IJavaProject = {
//    if (editor == null)
//      return null;
//
//    val input = editor.getEditorInput();
//    val provider = editor.getDocumentProvider();
//
//    val element = if (provider.isInstanceOf[ICompilationUnitDocumentProvider]) {
//      provider.asInstanceOf[ICompilationUnitDocumentProvider].getWorkingCopy(input)
//    } else if (input.isInstanceOf[IClassFileEditorInput]) {
//      input.asInstanceOf[IClassFileEditorInput].getClassFile()
//    } else {
//      null
//    }
//
//    if (element == null) {
//      return null;
//    }
//
//    return element.getJavaProject();
//  }
  override def getAnnotationHover(sourceViewer : ISourceViewer) : IAnnotationHover =  new org.eclipse.jface.text.source.DefaultAnnotationHover()
  
  override def getTextHover(sv : ISourceViewer, contentType : String, stateMask : Int) = new ScalaHover(getCodeAssist)

  override def getHyperlinkDetectors(sv : ISourceViewer) = {
    val shd = new ScalaHyperlinkDetector()
    shd.setContext(editor)
    Array(shd)
  }
  
  private def getCodeAssist : Option[ICodeAssist] = None
//  Option(editor) flatMap { editor =>
//    val input = editor.getEditorInput
//    val provider = editor.getDocumentProvider
//
//    (provider, input) match {
//      case (icudp : ICompilationUnitDocumentProvider, _) => icudp getWorkingCopy input
//      case (_, icfei : IClassFileEditorInput) => icfei.getClassFile
//      case _ => null
//    }
//  }
}
