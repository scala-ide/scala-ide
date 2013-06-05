/*
 *
 */
package scala.tools.eclipse.templates

import org.eclipse.jdt.internal.ui.JavaPluginImages


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
    val b = new ContributionTemplateStore(contextTypeRegistry, ScalaPlugin.prefStore, TEMPLATE_STORE_ID)
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

import org.eclipse.jface.text.IRegion
import org.eclipse.jface.text.ITextViewer
import org.eclipse.jface.text.templates.Template
import org.eclipse.jface.text.templates.TemplateCompletionProcessor
import org.eclipse.jface.text.templates.TemplateContextType
import org.eclipse.swt.graphics.Image

/**
 * Completion processor used for templates.
 *
 * @author david.bernard
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
   * @TODO provide a ScalaTemplate class with a match() method in template and get more sensible template matching.
   */
  protected override def getTemplates(contextTypeId : String) : Array[Template] = {
    tm.templateStore.getTemplates()
  }

  protected override def getRelevance(template : Template, prefix : String) : Int = {
    (prefix == null || prefix.trim().length == 0) match {
      case true => 0
      case false => super.getRelevance(template, prefix)
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

