package org.scalaide.ui.completion

import org.eclipse.jdt.internal.ui.JavaPlugin
import org.eclipse.jdt.internal.ui.JavaPluginImages
import org.eclipse.jdt.ui.PreferenceConstants
import org.eclipse.jface.preference.PreferenceConverter
import org.eclipse.jface.text.contentassist.ICompletionProposal
import org.eclipse.jface.text.link._
import org.eclipse.swt.graphics.Color
import org.scalaide.core.completion.CompletionProposal
import org.scalaide.ui.ScalaImages

/** Factory to create completion proposal instances usable by the
 *  Eclipse UI.
 */
object ScalaCompletionProposal {

  import ScalaImages._
  val defImage = PUBLIC_DEF.createImage()
  val classImage = SCALA_CLASS.createImage()
  val traitImage = SCALA_TRAIT.createImage()
  val objectImage = SCALA_OBJECT.createImage()
  val packageObjectImage = SCALA_PACKAGE_OBJECT.createImage()
  val typeImage = SCALA_TYPE.createImage()
  val valImage = PUBLIC_VAL.createImage()

  val javaInterfaceImage = JavaPluginImages.get(JavaPluginImages.IMG_OBJS_INTERFACE)
  val javaClassImage = JavaPluginImages.get(JavaPluginImages.IMG_OBJS_CLASS)
  val packageImage = JavaPluginImages.get(JavaPluginImages.IMG_OBJS_PACKAGE)


  /** Wraps a [[CompletionProposa]] returned by the presentation compiler in
   *  an ICompletionProposal usable by the platform.
   */
  def apply(proposal: CompletionProposal): ICompletionProposal =
    new org.scalaide.ui.internal.completion.ScalaCompletionProposalImpl(proposal)

}
