package org.scalaide.ui.internal.editor.sbt

import org.eclipse.core.filebuffers.IDocumentSetupParticipant
import org.eclipse.jdt.ui.PreferenceConstants
import org.eclipse.jdt.ui.text.IJavaPartitions
import org.eclipse.jface.preference.IPreferenceStore
import org.eclipse.jface.text.DefaultIndentLineAutoEditStrategy
import org.eclipse.jface.text.IAutoEditStrategy
import org.eclipse.jface.text.IDocument
import org.eclipse.jface.text.IDocumentExtension3
import org.eclipse.jface.text.ITextViewer
import org.eclipse.jface.text.Region
import org.eclipse.jface.text.contentassist.ContentAssistant
import org.eclipse.jface.text.contentassist.ICompletionProposal
import org.eclipse.jface.text.contentassist.IContentAssistProcessor
import org.eclipse.jface.text.contentassist.IContentAssistant
import org.eclipse.jface.text.contentassist.IContextInformation
import org.eclipse.jface.text.source.ISourceViewer
import org.eclipse.jface.text.source.SourceViewerConfiguration
import org.eclipse.ui.editors.text.ForwardingDocumentProvider
import org.eclipse.ui.editors.text.TextEditor
import org.eclipse.ui.editors.text.TextFileDocumentProvider
import org.eclipse.ui.texteditor.IDocumentProvider
import org.scalaide.core.IScalaPlugin
import org.scalaide.core.compiler.InteractiveCompilationUnit
import org.scalaide.core.completion.ScalaCompletions
import org.scalaide.core.lexical.ScalaCodePartitioner
import org.scalaide.core.lexical.ScalaPartitions
import org.scalaide.logging.HasLogger
import org.scalaide.ui.completion.ScalaCompletionProposal
import org.scalaide.ui.editor.DefaultScalaEditorConfiguration
import org.scalaide.ui.editor.InteractiveCompilationUnitEditor
import org.scalaide.ui.internal.editor.autoedits.BracketAutoEditStrategy
import org.scalaide.ui.internal.editor.autoedits.CommentAutoIndentStrategy
import org.scalaide.ui.internal.editor.autoedits.LiteralAutoEditStrategy
import org.scalaide.ui.internal.editor.autoedits.StringAutoEditStrategy
import org.scalaide.ui.internal.editor.autoedits.TabsToSpacesConverter
import org.scalaide.util.ScalaWordFinder

class SbtEditor extends TextEditor with InteractiveCompilationUnitEditor with HasLogger {
  private lazy val sourceViewConfiguration =
    new SbtEditorConfiguration(IScalaPlugin().getPreferenceStore, PreferenceConstants.getPreferenceStore, this)

  val preferenceStore = IScalaPlugin().getPreferenceStore

  setSourceViewerConfiguration(sourceViewConfiguration)
  setPartName("Sbt Editor")
  setDocumentProvider(new SbtDocumentProvider)

  override def getInteractiveCompilationUnit(): SbtCompilationUnit = {
    SbtCompilationUnit.fromEditor(this)
  }

  def getViewer(): ISourceViewer = getSourceViewer
}

class SbtEditorConfiguration(
    val pluginPreferenceStore: IPreferenceStore,
    val javaPreferenceStore: IPreferenceStore,
    val textEditor: SbtEditor) extends SourceViewerConfiguration with DefaultScalaEditorConfiguration {

  override def getContentAssistant(sourceViewer: ISourceViewer): IContentAssistant = {
    val assistant = new ContentAssistant
    assistant.setDocumentPartitioning(getConfiguredDocumentPartitioning(sourceViewer))
    assistant.setContentAssistProcessor(new SbtCompletionProposalComputer(textEditor), IDocument.DEFAULT_CONTENT_TYPE)
    assistant
  }

  //  override def getContentFormatter(viewer: ISourceViewer) = {
  //    val formatter = new MultiPassContentFormatter(getConfiguredDocumentPartitioning(viewer), IDocument.DEFAULT_CONTENT_TYPE)
  //    formatter.setMasterStrategy(new ScalaFormattingStrategy(textEditor))
  //    formatter
  //  }

  //  override def getHyperlinkDetectors(sv: ISourceViewer): Array[IHyperlinkDetector] = {
  //    val detector = DeclarationHyperlinkDetector()
  //    detector.setContext(textEditor)
  //    Array(detector)
  //  }

  override def getAutoEditStrategies(sourceViewer: ISourceViewer, contentType: String): Array[IAutoEditStrategy] = {
    val partitioning = getConfiguredDocumentPartitioning(sourceViewer)
    contentType match {
      case IJavaPartitions.JAVA_DOC | IJavaPartitions.JAVA_MULTI_LINE_COMMENT | ScalaPartitions.SCALADOC_CODE_BLOCK =>
        Array(new CommentAutoIndentStrategy(scalaPreferenceStore, partitioning), new TabsToSpacesConverter(scalaPreferenceStore))

      case IJavaPartitions.JAVA_STRING =>
        Array(new StringAutoEditStrategy(partitioning, scalaPreferenceStore))

      case _ =>
        Array(new BracketAutoEditStrategy(scalaPreferenceStore),
          new DefaultIndentLineAutoEditStrategy(),
          new LiteralAutoEditStrategy(scalaPreferenceStore))
    }
  }
}

object SbtPartitioning {
  val sbtFilePartitioning = "__scala_partitioning" //"__sbt_partitioning"
}

/** A Document provider for Scala scripts. It sets the Scala
 *  partitioner.
 */
class SbtDocumentProvider extends TextFileDocumentProvider {

  val provider: IDocumentProvider = new TextFileDocumentProvider()
  val fwd = new ForwardingDocumentProvider(SbtPartitioning.sbtFilePartitioning, new SbtDocumentSetupParticipant, provider)
  setParentDocumentProvider(fwd)
}

private class SbtDocumentSetupParticipant extends IDocumentSetupParticipant {
  override def setup(doc: IDocument): Unit = {
    doc match {
      case docExt: IDocumentExtension3 =>
        // TODO: maybe it's not necessary to be conservative. This makes the partitioner
        // always return 'true' when asked about changed partitioning. Fixes #82, that
        // lost colorization when entering a new line between an expression and its result
        val partitioner = ScalaCodePartitioner.documentPartitioner(conservative = true)
        docExt.setDocumentPartitioner(SbtPartitioning.sbtFilePartitioning, partitioner)
        partitioner.connect(doc)
    }
  }
}

class SbtCompletionProposalComputer(textEditor: SbtEditor) extends ScalaCompletions with IContentAssistProcessor {
  override def getCompletionProposalAutoActivationCharacters() = Array('.')
  override def getContextInformationAutoActivationCharacters() = Array[Char]()
  override def getErrorMessage = "No error"
  override def getContextInformationValidator = null

  override def computeCompletionProposals(viewer: ITextViewer, offset: Int): Array[ICompletionProposal] = {
    val icu = textEditor.getInteractiveCompilationUnit()
    val completions = findCompletions(viewer, offset, icu)
    completions.toArray
  }

  private def findCompletions(viewer: ITextViewer, position: Int, scu: InteractiveCompilationUnit): List[ICompletionProposal] = {
    val region = ScalaWordFinder.findCompletionPoint(viewer.getDocument.get, position)
    val mappedRegion = new Region(scu.lastSourceMap().scalaPos(region.getOffset), region.getLength)
    val mappedPos = scu.lastSourceMap.scalaPos(position)

    val res = getCompletions(mappedRegion, mappedPos, scu)
      .sortBy(_.relevance).reverse

    for (proposal <- res) yield {
      val newProp = proposal.copy(startPos = region.getOffset)
      ScalaCompletionProposal(newProp)
    }

  }

  override def computeContextInformation(viewer: ITextViewer, offset: Int): Array[IContextInformation] = {
    null
  }
}