/*
 * 
 */
package scala.tools.eclipse.templates

import org.eclipse.core.filebuffers.FileBuffers
import scala.tools.eclipse.ScalaImages
import scala.tools.eclipse.ScalaPlugin

import org.eclipse.ui.editors.text.templates.ContributionContextTypeRegistry
import org.eclipse.ui.editors.text.templates.ContributionTemplateStore

//TODO multi-line Template aren't indented

/**
 * Group template related information instead of being merged/flatten into ScalaPlugin.
 * 
 * @author david.bernard
 */
class ScalaTemplateManager {

  val CONTEXT_TYPE = ScalaPlugin.plugin.pluginId + ".templates"
  val TEMPLATE_STORE_ID = ScalaPlugin.plugin.pluginId + ".preferences.Templates"
    
  lazy val templateStore = {
    val b = new ContributionTemplateStore(contextTypeRegistry, ScalaPlugin.plugin.getPreferenceStore(), TEMPLATE_STORE_ID)
    b.load()
    b
  }

  lazy val contextTypeRegistry = {
    val b = new ContributionContextTypeRegistry()
    b.addContextType(CONTEXT_TYPE)
    b
  }
  
  def makeTemplateCompletionProcessor() = new ScalaTemplateCompletionProcessor(this)
}

import org.eclipse.jface.text.IDocument
import org.eclipse.jface.text.IRegion
import org.eclipse.jface.text.ITextViewer
import org.eclipse.jface.text.templates.Template
import org.eclipse.jface.text.templates.TemplateCompletionProcessor
import org.eclipse.jface.text.templates.TemplateContextType
import org.eclipse.swt.graphics.Image
import scala.tools.eclipse.lexical.ScalaPartitions

/**
 * Completion processor used for templates.
 * 
 * @author david.bernard
 */
class ScalaTemplateCompletionProcessor(val tm : ScalaTemplateManager) extends TemplateCompletionProcessor {

  protected override def getContextType(viewer : ITextViewer , region : IRegion) : TemplateContextType = {
    //TODO find a better way to detect if we are in Java or Scala Editor (as both use IDocument.DEFAULT_CONTENT_TYPE for code section and return same values for viewer.getClass, doc.getLegalContentTypes
    //TODO better scala editor should no longer use IDocument.DEFAULT_CONTENT_TYPE (may be side effect completion hover for the scala editor inherited from JDT/JavaSourceViewerConfiguration)
    val doc = viewer.getDocument
    val bufferManager = FileBuffers.getTextFileBufferManager()
    val fb = bufferManager.getTextFileBuffer(doc)
    ("scala" == fb.getLocation.getFileExtension)  match {
      case true => tm.contextTypeRegistry.getContextType(tm.CONTEXT_TYPE)
      case _ => null
    }
  }
    
  protected override def getImage(template : Template) : Image  = ScalaImages.TEMPLATE
    
  /**
   * @return All the templates 
   * @TODO take care of contextTypeId
   * @TODO provide a ScalaTemplate class with a match() method in template and get more sensible template matching.
   */
  protected override def getTemplates(contextTypeId : String) : Array[Template] = {
    (contextTypeId == tm.CONTEXT_TYPE) match {
      case true => tm.templateStore.getTemplates()
      case false => Array.empty[Template]
    }
  }

  protected override def getRelevance(template : Template, prefix : String) : Int = {
    (prefix == null || prefix.trim().length == 0) match {
      case true => 0
      case false if (template.getName().startsWith(prefix)) => 50
      case _ => -1
    }
  }
}

import org.eclipse.jface.text.templates.GlobalTemplateVariables
import org.eclipse.jface.text.templates.TemplateContextType


/**
 * Simple TemplateContextType for Scala.
 * 
 * @author david.bernard
 * @TODO add context resolver like java editor (near variable of some type,...)
 */
class ScalaTemplateContextType extends TemplateContextType {

  private def addGlobalResolvers() {
    addResolver(new GlobalTemplateVariables.Cursor())
    addResolver(new GlobalTemplateVariables.WordSelection())
    addResolver(new GlobalTemplateVariables.LineSelection())
    addResolver(new GlobalTemplateVariables.Dollar())
    addResolver(new GlobalTemplateVariables.Date())
    addResolver(new GlobalTemplateVariables.Year())
    addResolver(new GlobalTemplateVariables.Time())
    addResolver(new GlobalTemplateVariables.User())
  }

  addGlobalResolvers()
}

