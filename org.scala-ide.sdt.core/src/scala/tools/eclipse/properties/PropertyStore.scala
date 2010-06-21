/*
 * Copyright 2005-2010 LAMP/EPFL
 * @author Josh Suereth
 */
// $Id$

package scala.tools.eclipse.properties

import org.eclipse.core.runtime.preferences._
import org.eclipse.core._
import org.eclipse.core.runtime._
import org.eclipse.ui._
import org.eclipse.jface.preference._
import org.eclipse.core.resources._

/**
 *A wrapper around a Preference store for properties. 
 */
class PropertyStore( val project : IProject, val workbenchStore : IPreferenceStore, val pageId : String) 
    extends PreferenceStore {
  
      
  lazy val projectNode : IEclipsePreferences = {
    new ProjectScope(project).getNode(pageId)
  }
  /** Returns the "default" value of a property (in this case, the workbench value) */
  override def getDefaultString(name : String ) = workbenchStore.getDefaultString(name);
  /**
   * Pulls the value of a property as a string 
   */
  override def getString(name : String) = {
	  insertValue(name);
	  super.getString(name);
  }
  
  override def getDefaultBoolean(name : String) = workbenchStore.getDefaultBoolean(name);
  override def getBoolean(name : String) = {
	  insertValue(name);
	  super.getBoolean(name);
  }
  override def getDefaultDouble(name : String) = workbenchStore.getDefaultDouble(name);
  override def getDouble(name : String) = {
	  insertValue(name);
	  super.getDouble(name);
  }
  override def getDefaultFloat(name : String) = workbenchStore.getDefaultFloat(name);
  override def getFloat(name : String) = {
	  insertValue(name);
	  super.getFloat(name);
  }
  override def getDefaultInt(name : String) = workbenchStore.getDefaultInt(name);
  override def getInt(name : String) = {
	  insertValue(name);
	  super.getInt(name);
  }
  override def getDefaultLong(name : String) = workbenchStore.getDefaultLong(name);
  override def getLong(name : String) = {
	  insertValue(name);
	  super.getLong(name);
  }
  
  private var inserting = false;
  /**
   * Inserts the value from the workbench store into the property store if needed.
   */
  private def insertValue(name :String) : Unit = {
    /** Pulls the value of the property on the resource*/
    def getProperty(name :String ) = projectNode.get(name, null)
    
    this synchronized {
      if (inserting)
	    return;
	  if (super.contains(name))
	    return;
	  inserting = true;
	  var prop : String = null;
	  try {
	    prop = getProperty(name);
	  } catch {
	    case e :CoreException =>
	  }
	  if (prop == null)
	    prop = workbenchStore.getString(name);
	  if (prop != null)
	    setValue(name, prop);
	  inserting = false;
    }
  }
  
  /** Checks to see if we should have a property */
  override def contains(name : String) : Boolean = { 
    if(projectNode.get(name, null) == null) {
      return workbenchStore.contains(name); 
    } 
    true
  }
  /** Resets a property to the "default" (workbench) value */
  override def setToDefault(name : String) : Unit = setValue(name, getDefaultString(name));
  /** Checks to see if our property is the same as the "default" (workbench) */
  override def isDefault( name : String) : Boolean = {
	  val defaultValue = getDefaultString(name);
	  if (defaultValue == null) return false;
	  return defaultValue.equals(getString(name));
	}
  
    /** Overrides save to work for properties */
  	override def save() : Unit =  writeProperties();
   
    import java.io.OutputStream;
    import java.io.IOException;
    /** overrides save to work for properties */
	override def save( out : OutputStream,  header : String) = writeProperties();
	
    /** This will save out our properties */
	private def writeProperties() : Unit = {
	  
	  //Helper method to actually set properties
	  def setProperty(name : String, value : String) = {
	    projectNode.put(name, value)
      }
	  
	  val preferences = super.preferenceNames();
	  preferences foreach  { preference :String =>
	    try {
	      setProperty(preference, getString(preference));
	    } catch  {
	      case e : CoreException => throw new IOException("Cannot write resource property " + preference);
	    }
	  }
   
      projectNode.flush
	}

}
