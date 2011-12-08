package scala.tools.eclipse.contribution.weaving.jdt.jcompiler;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IMarkerDelta;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IResourceDeltaVisitor;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.PlatformObject;

/*
 * Tests needed, for all test cases, and also for the one described at https://github.com/froden/scala-ide-test
 */

/**
 * A resource delta which allows addition of a Resource change in its tree, and 
 * use wrapped IResourceDelta for other nodes.<br>
 * It is used to tell the Eclipse Java compiler to compile more file than the set
 * that was marked at the beginning of the build.
 */
public class ExpandableResourceDelta extends PlatformObject implements IResourceDelta {

  /**
   * A wrapped resource. If it is set, it is used for all information except
   * the list of children.
   */
  private IResourceDelta wrapped;
  
  /**
   * The children of this resource delta.
   */
  private List<ExpandableResourceDelta> children= new ArrayList<ExpandableResourceDelta>();
  
  /**
   * A resource. If it is set, this resource delta is content change for this resource.
   */
  private IResource resource;

  /**
   * Create a node wrapping the give resource delta.
   */
  private ExpandableResourceDelta(IResourceDelta resourceDelta) {
    wrapped= resourceDelta;
  }

  /**
   * Create a node for the give resource, with a status CONTENT and CHANGE.
   */
  private ExpandableResourceDelta(IResource resource) {
    this.resource= resource;
  }

  /*
   * (non-Javadoc)
   * @see org.eclipse.core.resources.IResourceDelta#accept(org.eclipse.core.resources.IResourceDeltaVisitor)
   */
  public void accept(IResourceDeltaVisitor visitor) throws CoreException {
    accept(visitor, IResource.NONE);
  }

  /*
   * (non-Javadoc)
   * @see org.eclipse.core.resources.IResourceDelta#accept(org.eclipse.core.resources.IResourceDeltaVisitor, boolean)
   */
  public void accept(IResourceDeltaVisitor visitor, boolean includePhantoms)
      throws CoreException {
    accept(visitor, includePhantoms ? IContainer.INCLUDE_PHANTOMS : IResource.NONE);
  }

  /*
   * (non-Javadoc)
   * @see org.eclipse.core.resources.IResourceDelta#accept(org.eclipse.core.resources.IResourceDeltaVisitor, int)
   */
  public void accept(IResourceDeltaVisitor visitor, int memberFlags)
      throws CoreException {
    for (IResourceDelta child: getAffectedChildren(ADDED | REMOVED | CHANGED, memberFlags)) {
      visitor.visit(child);
    }
  }

  /*
   * (non-Javadoc)
   * @see org.eclipse.core.resources.IResourceDelta#findMember(org.eclipse.core.runtime.IPath)
   */
  public IResourceDelta findMember(IPath path) {
    if (path.segmentCount() == 0)
      return this;
    
    // look for a child with the first segment of the path.
    IPath fullPath= getFullPath().append(path.segment(0));
    int nbSegments= fullPath.segmentCount();
    for (IResourceDelta child: children) {
      if (child.getFullPath().matchingFirstSegments(fullPath) == nbSegments) {
        // and search in this child
        return child.findMember(path.removeFirstSegments(1));
      }
    }
    return null;
  }

  /*
   * (non-Javadoc)
   * @see org.eclipse.core.resources.IResourceDelta#getAffectedChildren()
   */
  public IResourceDelta[] getAffectedChildren() {
    return getAffectedChildren(ADDED | REMOVED | CHANGED, IResource.NONE);
  }

  /*
   * (non-Javadoc)
   * @see org.eclipse.core.resources.IResourceDelta#getAffectedChildren(int)
   */
  public IResourceDelta[] getAffectedChildren(int kindMask) {
    return getAffectedChildren(kindMask, IResource.NONE);
  }

  /*
   * (non-Javadoc)
   * @see org.eclipse.core.resources.IResourceDelta#getAffectedChildren(int, int)
   */
  public IResourceDelta[] getAffectedChildren(int kindMask, int memberFlags) {
    List<IResourceDelta> affectedChildren= new ArrayList<IResourceDelta>();
    if ((memberFlags & IContainer.INCLUDE_PHANTOMS) != 0) {
      kindMask |= ADDED_PHANTOM | REMOVED_PHANTOM;
    }
    boolean includeHidden = (memberFlags & IContainer.INCLUDE_HIDDEN) != 0;
    boolean includeTeamPrivateMember = (memberFlags & IContainer.INCLUDE_TEAM_PRIVATE_MEMBERS) == 0;
    
    for (IResourceDelta child: children) {
      if ((kindMask & child.getKind()) != 0 && (includeHidden || !child.getResource().isHidden()) && (includeTeamPrivateMember || !child.getResource().isTeamPrivateMember())) {
        affectedChildren.add(child);
      }
    }
    
    return affectedChildren.toArray(new IResourceDelta[affectedChildren.size()]);
  }

  /*
   * (non-Javadoc)
   * @see org.eclipse.core.resources.IResourceDelta#getFlags()
   */
  public int getFlags() {
    if (wrapped != null) 
      return wrapped.getFlags();
    return IResourceDelta.CONTENT;
  }

  /*
   * (non-Javadoc)
   * @see org.eclipse.core.resources.IResourceDelta#getFullPath()
   */
  public IPath getFullPath() {
    if (wrapped != null) 
      return wrapped.getFullPath();
    return resource.getFullPath();
  }

  /*
   * (non-Javadoc)
   * @see org.eclipse.core.resources.IResourceDelta#getKind()
   */
  public int getKind() {
    if (wrapped != null)
      return wrapped.getKind();
    return IResourceDelta.CHANGED;
  }

  /*
   * (non-Javadoc)
   * @see org.eclipse.core.resources.IResourceDelta#getMarkerDeltas()
   */
  public IMarkerDelta[] getMarkerDeltas() {
    if (wrapped != null)
      return wrapped.getMarkerDeltas();
    return new IMarkerDelta[0];
  }

  /*
   * (non-Javadoc)
   * @see org.eclipse.core.resources.IResourceDelta#getMovedFromPath()
   */
  public IPath getMovedFromPath() {
    if (wrapped != null)
      return wrapped.getMovedFromPath();
    return null;
  }

  /*
   * (non-Javadoc)
   * @see org.eclipse.core.resources.IResourceDelta#getMovedToPath()
   */
  public IPath getMovedToPath() {
    if (wrapped != null)
      return wrapped.getMovedToPath();
    return null;
  }

  /*
   * (non-Javadoc)
   * @see org.eclipse.core.resources.IResourceDelta#getProjectRelativePath()
   */
  public IPath getProjectRelativePath() {
    if (wrapped != null) 
      return wrapped.getProjectRelativePath();
    return resource.getFullPath().makeRelativeTo(resource.getProject().getFullPath());
  }

  /*
   * (non-Javadoc)
   * @see org.eclipse.core.resources.IResourceDelta#getResource()
   */
  public IResource getResource() {
    if (wrapped != null) 
      return wrapped.getResource();
    return resource;
  }
  
  /**
   * Add the given resource in this resource delta, as a content change node.<br>
   * Creates the parent nodes if required.
   */
  public void addChangedResource(IResource changedResource) {
    getOrAddResourceForChange(changedResource);
  }
  
  /**
   * Return the node for a given resource. If it doesn't exist yet, create it, and its parent if required.<br>
   * Make sure that it is "reachable": it doesn't have NO_CHANGE parents.
   */
  private ExpandableResourceDelta getOrAddResourceForChange(IResource newResource) {
    // if this is the node for the given resource, we found it
    if (getResource().equals(newResource)) {
      
      // if the existing node is marked as NO_CHANGE, we need to unwrap it (thus making it CHANGE),
      // otherwise it would not be traversed
      if (getKind() == IResourceDelta.NO_CHANGE) {
        resource= wrapped.getResource();
        wrapped= null;
      }
      return this;
    }
    
    // otherwise, look for its parent
    ExpandableResourceDelta parentResourceDelta= getOrAddResourceForChange(newResource.getParent());
    
    // check if one of the existing siblings is actually the node for the given resource
    for (ExpandableResourceDelta childResourceDelta: parentResourceDelta.children) {
      if (childResourceDelta.getResource().equals(newResource)) {
        return childResourceDelta;
      }
    }
    
    // if it doesn't exist, create the node and adds it to its parent
    ExpandableResourceDelta newResourceDelta= new ExpandableResourceDelta(newResource);
    parentResourceDelta.children.add(newResourceDelta);
    return newResourceDelta;
  }

  /**
   * Duplicate a resource delta by wrapping the root node and duplicating its children.
   */
  public static ExpandableResourceDelta duplicate(IResourceDelta original) {
    ExpandableResourceDelta newDelta = new ExpandableResourceDelta(original);
    for (IResourceDelta child: original.getAffectedChildren(ALL_WITH_PHANTOMS, IContainer.INCLUDE_HIDDEN | IContainer.INCLUDE_PHANTOMS | IContainer.INCLUDE_TEAM_PRIVATE_MEMBERS)) {
      newDelta.children.add(duplicate(child));
    }
    return newDelta;
  }


  /*
   * (non-Javadoc)
   * @see java.lang.Object#toString()
   */
  @Override
  public String toString() {
    return "ExtendedResourceDelta(" + getFullPath() + ")";
  }
}
