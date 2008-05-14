/*
 * Copyright 2005-2008 LAMP/EPFL
 * @author Sean McDirmid
 */
// $Id$

package scala.tools.eclipse.net

import scala.xml
import org.eclipse.core.resources._
import org.eclipse.core.runtime._
import scala.collection.jcl._
import java.io

trait ScalaMSILPlugin extends eclipse.ScalaPlugin {
  type Project <: ProjectImpl 
  
  abstract class Assembly {
    def resolve : List[io.File]
    def asXML : xml.Elem
  }
  case object ScalaLibAssembly extends Assembly {
    def asXML = (<scalalib/>)
    def resolve = {
      val libPath = bundlePath + "lib/"
      for (a <- "scalaruntime" :: "predef" :: "mscorlib" :: Nil) yield {
        val path = Path.fromPortableString(libPath + a + ".dll")
        val file = new io.File(path.toOSString)
        assert(file.exists)
        file
      }
    }
  }
  case class WorkspaceAssembly(file : IPath) extends Assembly {
    def resolve = {
      new io.File(workspace.getFile(file.addFileExtension("dll")).getLocation.toOSString) :: Nil
    }
    def asXML = (<workspace>{file.toPortableString}</workspace>)
  }
  case class GlobalAssembly(name : String) extends Assembly {
    def resolve = {
      globalAssemblyCache.flatMap{gac =>
        val res = gac.append("GAC_MSIL").append(name)
        val file = new java.io.File(res.toOSString)
        assert(file.isDirectory)
        var found : Option[java.io.File] = None
        file.listFiles.foreach{x =>
          if (x.isDirectory) {
            if (found.isEmpty) found = Some(x)
            else if (found.get.getName.compareTo(x.getName) < 0) found = Some(x)
          }
        }
        found.flatMap{found =>
          var file = new java.io.File(found, name + ".dll")
          if (file.exists) Some(file) else None
        }
      }.getOrElse(new io.File(name + ".dll")) :: Nil
    }
    def asXML = (<global>{name}</global>)
  }
  def globalAssemblyCache : Option[IPath] = {
    Some(Path.fromOSString("C:\\Windows\\assembly"))
  }
  
  trait ProjectImpl extends super.ProjectImpl {
    def self : Project
    def assembliesFile(make : Boolean) = underlying.getFile(".assemblies") match {
    case file if file.exists || make => Some(file)
    case _ => None
    }
    private var assemblies : LinkedHashSet[Assembly] = _
    override def intializePaths(global : nsc.Global) : Unit = {
      if (!underlying.hasNature(msilNatureId)) 
        return super.intializePaths(global)
      // do msil initialization
      global.settings.target.value = "msil"
      global.settings.assemname.value = outputPath0.append("code.msil").toOSString
      global.assemrefs.clear
      if (assemblies == null) loadAssemblies
      assemblies.foreach{_.resolve.foreach{global.assemrefs += _}}
      // assemblies all shoved in, ready to go!
    }
    
    def saveAssemblies : Unit = saveAssemblies(assemblies)
    def saveAssemblies(assemblies : Iterable[Assembly]) : Unit = check{assembliesFile(true) match {
    case Some(file) =>
      val what = (<assemblies>{assemblies.map(_.asXML)}</assemblies>)
      val path = file.getLocation.toOSString
      xml.XML.save(path, what)
      underlying.refreshLocal(1, null)
    case None => logError("cannot save .assemblies", null)
    }}
    
    def initAssemblies = assemblies = new LinkedHashSet[Assembly]
    def add(assembly : Assembly) = {
      assemblies += assembly; saveAssemblies; resetCompiler
    }
    def rem(assembly : Assembly) = {
      assemblies -= assembly; saveAssemblies; resetCompiler
    }
    private var assemblyUpdate : Long = IResource.NULL_STAMP
    override def checkClasspath : Unit = {super.checkClasspath; 
      if (assemblies == null) loadAssemblies
      else check {
      val cp = underlying.getFile(".assemblies")
      if (cp.exists) assemblyUpdate match {
      case IResource.NULL_STAMP => assemblyUpdate = cp.getModificationStamp()
      case stamp if stamp == cp.getModificationStamp() => 
      case _ =>
        loadAssemblies
        assemblyUpdate = cp.getModificationStamp()
        resetCompiler
      }
    }}
    
    def loadAssemblies = {
      assemblies = new LinkedHashSet[Assembly]
      assembliesFile(false) match {
    case None => 
    case Some(file) => check{ 
      val e = xml.XML.loadFile(file.getLocation.toOSString)
      if (e.label == "assemblies") e.child.foreach{e => e.label match {
        case "workspace" =>
          val path = Path.fromPortableString(e.child(0).text)
          assemblies += WorkspaceAssembly(path)
        case "global" =>
          assemblies += GlobalAssembly(e.child(0).text)
        case "scalalib" =>
          assemblies += ScalaLibAssembly
        case x => logError("unknown assembly label " + x, null)
        }
      } else logError("bad assmbly file", null)
      ()
    }
    }}
  }
}