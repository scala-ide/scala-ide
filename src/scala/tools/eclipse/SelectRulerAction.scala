/*
 * Copyright 2005-2009 LAMP/EPFL
 * @author Sean McDirmid
 */
// $Id$

package scala.tools.eclipse
import org.eclipse.jface.text.source._
class SelectRulerAction extends lampion.eclipse.SelectRulerAction {
  def plugin = ScalaUIPlugin.plugin // i don't understand....
  override def special(a : Annotation) : Option[Special] = super.special(a).orElse({
    if (a.getType == plugin.OverrideIndicator) Some(new Special {
      override def actionId = "OpenSuperImplementation."
      override def dispatch(editor : lampion.eclipse.Editor) = {
        val plugin = ScalaUIPlugin.plugin
        val file0 = editor.file
        if (!file0.isEmpty) {
          val file1 : plugin.File = file0.get.asInstanceOf[plugin.File]
          val external : plugin.ExternalFile = file1.external
          val project : plugin.Project = external.project
          val file = external.file
          import project.compiler._
          /* XXX: redo...
            val sym = generateIdeMaps.url2sym(a.getText)(null)
            if (sym != null && sym != NoSymbol)
              file.open(generateIdeMaps.External(sym)).foreach(_.apply)
*/
          () 
        }
      }
    }) else None
  })
}
