package scala.tools.eclipse.javaelements

import scala.tools.eclipse.contribution.weaving.jdt.imagedescriptor.IImageDescriptorSelector
import org.eclipse.jdt.core.JavaModelException
import org.eclipse.jdt.internal.ui.text.java.LazyJavaCompletionProposal;
import org.eclipse.jface.resource.ImageDescriptor;

class ScalaImageDescriptorSelector extends IImageDescriptorSelector {

  def getTypeImageDescriptor(isInner : Boolean, isInInterfaceOrAnnotation : Boolean, flags : Int, useLightIcons : Boolean, element : AnyRef) : ImageDescriptor =
  try {
    element match {
      case se : ScalaElement => se.getImageDescriptor
      case _ => null
    }
  } catch {
    case _ : JavaModelException => null
  }
    
  def createCompletionProposalImageDescriptor(proposal : LazyJavaCompletionProposal) : ImageDescriptor = {
    proposal.getJavaElement match {
      case se : ScalaElement => se.getImageDescriptor
      case _ => null
    }
  } 
}
