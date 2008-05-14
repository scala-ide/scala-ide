/*
 * Copyright 2005-2008 LAMP/EPFL
 * @author Sean McDirmid
 */
// $Id$

package scala.tools.eclipse.navigator;
import org.eclipse.jdt.internal.ui.navigator._
import org.eclipse.core.runtime._
import org.eclipse.core.resources._
import org.eclipse.ui.navigator._
import org.eclipse.ui._
import org.eclipse.jdt.ui._
import org.eclipse.jdt.core._
import org.eclipse.jdt.ui.actions._
import org.eclipse.jdt.internal.ui.actions._
import org.eclipse.ui.actions._
import org.eclipse.jface.action._
import org.eclipse.jface.viewers._
/** note: copied from PackageExplorerOpenActionProvider in the JDT. Please see their license */
class OpenActionProvider extends CommonActionProvider {
  var fOpenGroup : OpenEditorActionGroup = _
  var fOpenAndExpand : OpenAndExpand = _
  var fInViewPart = false
  
  override def fillActionBars(actionBars : IActionBars) = {
    if (fInViewPart) {
      fOpenGroup.fillActionBars(actionBars)
      if (fOpenAndExpand == null && fOpenGroup.getOpenAction.isEnabled()) // TODO: is not updated!
        actionBars.setGlobalActionHandler(ICommonActionConstants.OPEN, fOpenGroup.getOpenAction)
      else if (fOpenAndExpand != null && fOpenAndExpand.isEnabled())
        actionBars.setGlobalActionHandler(ICommonActionConstants.OPEN, fOpenAndExpand);
    } else super.fillActionBars(actionBars)
  }
  override def fillContextMenu(menu : IMenuManager) = 
    if(fInViewPart && fOpenGroup.getOpenAction.isEnabled)
      fOpenGroup.fillContextMenu(menu)
  
  override def init(site : ICommonActionExtensionSite) = {
    super.init(site)
    site.getViewSite match {
    case workbenchSite : ICommonViewerWorkbenchSite => 
      workbenchSite.getPart match {
      case viewPart : IViewPart =>
        fOpenGroup = new OpenEditorActionGroup(viewPart)       
        site.getStructuredViewer match {
        case tree : TreeViewer =>
          fOpenAndExpand = new OpenAndExpand(workbenchSite.getSite, fOpenGroup.getOpenAction.asInstanceOf[OpenAction], tree);
        case _ => 
        }
        fInViewPart = true
      case _ => 
      }
    case _ => 
    }
  }
  override def setContext(context : ActionContext) = {
    super.setContext(context)
    if (fInViewPart) fOpenGroup.setContext(context)
  }
  class MyOpenAction(fSite : IWorkbenchSite) extends OpenAction(fSite) {
    override def run(elements0 : Array[AnyRef]) : Unit = {
      var elements = elements0
      if (elements != null) elements = elements.filter{
        case element : IClassFile if ScalaUIPlugin.plugin != null => 
          val plugin = ScalaUIPlugin.plugin
          plugin.inputFor(element) match { 
            case Some(input) =>
              val page = fSite.getPage
              page.openEditor(input, plugin.editorId)
              false
            case None => true
          }
        case _ => true
      }
      super.run(elements)
    }
  }
  
  class OpenEditorActionGroup(part : IViewPart) extends ActionGroup {
    var fSite : IWorkbenchSite = part.getSite
    var fIsEditorOwner : Boolean = _
     // copy all this crap for the JDT just so I can write this!
    var fOpen : OpenAction = new MyOpenAction(fSite)
    fOpen.setActionDefinitionId(IJavaEditorActionDefinitionIds.OPEN_EDITOR);
    initialize(fSite.getSelectionProvider())
    def getOpenAction = fOpen
    private def initialize(provider : ISelectionProvider) = {
      val selection= provider.getSelection();
      fOpen.update(selection)
      if (!fIsEditorOwner) provider.addSelectionChangedListener(fOpen);
    }
    override def fillActionBars(actionBar : IActionBars) = {
      super.fillActionBars(actionBar)
      setGlobalActionHandlers(actionBar)
    }
    override def fillContextMenu(menu : IMenuManager) = {
      super.fillContextMenu(menu)
      appendToGroup(menu, fOpen)
      if (!fIsEditorOwner) addOpenWithMenu(menu)
    }
    override def dispose = {
      fSite.getSelectionProvider.removeSelectionChangedListener(fOpen)
      super.dispose
    }
    private def setGlobalActionHandlers(actionBars : IActionBars) = 
      actionBars.setGlobalActionHandler(JdtActionConstants.OPEN, fOpen)
    private def appendToGroup(menu : IMenuManager, action : IAction) = {
      if (action.isEnabled())
        menu.appendToGroup(IContextMenuConstants.GROUP_OPEN, action)
    }
    private def addOpenWithMenu(menu : IMenuManager) : Unit= {
      val selection = getContext.getSelection match {
        case selection : IStructuredSelection if selection.size == 1 => selection
        case _ => return
      }
      val resource = selection.getFirstElement match {
        case o : IAdaptable => o.getAdapter(classOf[IResource]) match {
          case file : IFile => file
          case _ => return
        }
        case _ => return
      }
      val submenu= new MenuManager(ActionMessages.OpenWithMenu_label)
      submenu.add(new OpenWithMenu(fSite.getPage(), resource))
      // Add the submenu.
      menu.appendToGroup(IContextMenuConstants.GROUP_OPEN, submenu);
      
    }
  }
}
