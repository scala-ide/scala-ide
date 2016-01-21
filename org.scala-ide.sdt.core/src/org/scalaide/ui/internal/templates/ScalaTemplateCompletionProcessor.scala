package org.scalaide.ui.internal.templates

import org.eclipse.jdt.internal.ui.JavaPluginImages
import org.eclipse.ui.editors.text.templates.ContributionContextTypeRegistry
import org.eclipse.ui.editors.text.templates.ContributionTemplateStore
import org.eclipse.jface.text.IRegion
import org.eclipse.jface.text.ITextViewer
import org.eclipse.jface.text.templates.Template
import org.eclipse.jface.text.templates.TemplateCompletionProcessor
import org.eclipse.jface.text.templates.TemplateContextType
import org.eclipse.swt.graphics.Image
import org.eclipse.jface.text.templates.GlobalTemplateVariables
import org.eclipse.jface.text.templates.TemplateContextType
import org.scalaide.core.IScalaPlugin
import org.scalaide.core.SdtConstants

/**
 * Group template related information instead of being merged/flatten into ScalaPlugin.
 *
 */
class ScalaTemplateManager {

  val CONTEXT_TYPE = SdtConstants.PluginId + ".templates"
  val TEMPLATE_STORE_ID = SdtConstants.PluginId + ".preferences.Templates"

  lazy val templateStore = {
    val b = new ContributionTemplateStore(contextTypeRegistry, IScalaPlugin().getPreferenceStore(), TEMPLATE_STORE_ID)
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

/**
 * Completion processor used for templates.
 *
 */
class ScalaTemplateCompletionProcessor(val tm : ScalaTemplateManager) extends TemplateCompletionProcessor {

  protected override def getContextType(viewer : ITextViewer , region : IRegion) : TemplateContextType = {
    tm.contextTypeRegistry.getContextType(tm.CONTEXT_TYPE);
  }

  //TODO provide a icon for template
  protected override def getImage(template : Template) : Image  = JavaPluginImages.get(JavaPluginImages.IMG_OBJS_TEMPLATE)

  /**
   * @return All the templates
   * @TODO take care of contextTypeId
   */
  protected override def getTemplates(contextTypeId : String) : Array[Template] = {
    tm.templateStore.getTemplates(contextTypeId)
  }

  protected override def getRelevance(template : Template, prefix : String) : Int = {
    if (prefix == null || prefix.trim().length == 0) 0
    else super.getRelevance(template, prefix)
  }

  override def createContext(viewer: ITextViewer, region: IRegion) = {
    val contextType = getContextType(viewer, region);
    if (contextType != null) {
      val document= viewer.getDocument();
      new ScalaTemplateContext(contextType, document, region.getOffset, region.getLength)
    } else null
  }
}


/**
 * Simple TemplateContextType for Scala.
 *
 * @TODO add context resolver like java editor (near variable of some type,...)
 */
class ScalaTemplateContextType extends TemplateContextType {

  private def addGlobalResolvers(): Unit = {
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

