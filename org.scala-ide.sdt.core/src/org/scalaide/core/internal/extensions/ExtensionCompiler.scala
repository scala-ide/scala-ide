package org.scalaide.core.internal.extensions

import java.io.File
import java.io.File.{ separator => sep }

import scala.reflect.internal.util.AbstractFileClassLoader
import scala.reflect.internal.util.BatchSourceFile
import scala.reflect.internal.util.SourceFile
import scala.reflect.io.Directory
import scala.reflect.io.PlainDirectory
import scala.tools.nsc.Settings
import scala.tools.nsc.interactive.Global
import scala.tools.nsc.reporters.StoreReporter
import scala.util.Failure
import scala.util.Success
import scala.util.Try

import org.eclipse.core.runtime.FileLocator
import org.scalaide.core.IScalaPlugin
import org.scalaide.core.ScalaIdeDataStore
import org.scalaide.core.SdtConstants
import org.scalaide.core.compiler.IScalaPresentationCompiler
import org.scalaide.core.internal.project.ScalaInstallation
import org.scalaide.core.text.Document
import org.scalaide.core.text.TextChange
import org.scalaide.extensions.AutoEdit
import org.scalaide.extensions.CompilerSupport
import org.scalaide.extensions.DocumentSupport
import org.scalaide.extensions.ExtensionSetting
import org.scalaide.extensions.SaveAction
import org.scalaide.logging.HasLogger

/**
 * Represents possible Scala IDE extension creators. A creator is a function
 * that takes some arguments and creates an instance of an extension that
 * depends on the passed arguments.
 */
object ExtensionCreators {

  /** Represents a Save Action that depends on [[DocumentSupport]]. */
  type DocumentSaveAction =
    Document ⇒ SaveAction with DocumentSupport

  /** Represents a Save Action that depends on [[CompilerSupport]]. */
  type CompilerSaveAction = (
      IScalaPresentationCompiler, IScalaPresentationCompiler#Tree,
      SourceFile, Int, Int
    ) ⇒ SaveAction with CompilerSupport

  /** Represents an Auto Edit. */
  type AutoEdit =
    (Document, TextChange) ⇒ org.scalaide.extensions.AutoEdit
}

/**
 * This compiler generates source snippets that allow the creation of Scala IDE
 * extensions. Because every Scala IDE extension relies on some dependencies it
 * is not straightforward on how to instantiate them. Therefore this compiler
 * creates so called "creators", one of the types available in
 * [[ExtensionCreator]], that return functions, which take the dependencies and
 * instantiate the extensions.
 *
 * Because compilation takes time, the generated source snippets are stored on
 * disk after their first compilation and for all further uses loaded from
 * there.
 */
object ExtensionCompiler extends AnyRef with HasLogger {

  /**
   * The version of the generated source code that is compiled and cached to
   * disk. If the generated source code changes in any way, this version needs
   * to be increased, otherwise we risk a broken cache.
   */
  private val vGenerated = "v1"

  /**
   * The version of Scala that is used to compile the generated source code.
   * Since the Scala compiler and the internals of Scala IDE are binary
   * incompatible, we have to create a new cache every time the Scala version
   * changes.
   */
  private val vScala = ScalaInstallation.platformInstallation.version

  /**
   * The reporter used to report any compilation errors and warnings that occur
   * in the generated source code.
   */
  private val reporter = new StoreReporter

  /**
   * The location of the class file cache.
   */
  private val outputDir = {
    val f = new File(s"${ScalaIdeDataStore.extensionsOutputDirectory}$sep${vScala.unparse}$sep$vGenerated")
    f.mkdirs()
    new PlainDirectory(new Directory(f))
  }

  /**
   * The class loader that is used to access all generated class files.
   */
  private val classLoader = new AbstractFileClassLoader(outputDir, this.getClass.getClassLoader)

  private val settings = {
    val s = new Settings(err ⇒ logger.error(err))
    s.outputDirs.setSingleOutput(outputDir)
    s.usejavacp.value = true

    val install = ScalaInstallation.platformInstallation
    s.bootclasspath.value = install.allJars.map(_.classJar).mkString(File.pathSeparator)
    s.source.value = vScala

    val bundles = IScalaPlugin().getBundle.getBundleContext.getBundles

    /*
     * Creates all output directories of the Scala IDE bundles whose classes
     * should be added to the classpath of the extension compiler. Not all of
     * the bundles are necessarily required but for safety reasons we add more
     * of them than less. It may happen that in future that further bundles are
     * required or that the location of the output directory changes - in these
     * cases this method needs to be changed accordingly.
     */
    def devBundles = {
      import SdtConstants._
      val pluginIds = Seq(AspectsPluginId, PluginId, DebuggerPluginId, ExpressionEvaluatorPluginId, ScalaRefactoringPluginId)
      val devBundles = pluginIds flatMap (id ⇒ bundles.find(_.getSymbolicName == id))

      devBundles.map(FileLocator.getBundleFile).map(ref ⇒ s"${ref}${File.separator}target${File.separator}classes")
    }

    /*
     * The Scala IDE bundles are available through a different path when we use
     * the IDE in development mode. Development mode means that Scala IDE is run
     * inside of Eclipse, i.e. the classes of the bundles are located in the
     * output directories of the build and not inside of the JAR file of the
     * bundle.
     */
    val isInDevelopmentMode = !IScalaPlugin().getBundle.getLocation.endsWith(".jar")

    val scalaIdeClasspath =
      if (isInDevelopmentMode)
        devBundles
      else
        Seq()

    val bundlesClasspath = bundles.map(FileLocator.getBundleFile).filter(_.getPath.endsWith(".jar"))
    s.classpath.value = (bundlesClasspath ++ scalaIdeClasspath).mkString(File.pathSeparator)

    logger.debug(s"The extension compiler is created with the following bootclasspath: ${s.bootclasspath.value}")
    logger.debug(s"The extension compiler is created with the following Scala version: ${s.source.value}")
    logger.debug(s"The extension compiler is created with the following classpath: ${s.classpath.value}")
    s
  }

  private val compiler = new Global(settings, reporter)

  private object Types {
    val documentSupport = ExtensionSetting.fullyQualifiedName[DocumentSupport]
    val document = ExtensionSetting.fullyQualifiedName[Document]
    val compiler = ExtensionSetting.fullyQualifiedName[IScalaPresentationCompiler]
    val compilerSupport = ExtensionSetting.fullyQualifiedName[CompilerSupport]
    val sourceFile = ExtensionSetting.fullyQualifiedName[SourceFile]
    val textChange = ExtensionSetting.fullyQualifiedName[TextChange]
  }

  private def buildDocumentSaveAction(fullyQualifiedName: String, creatorName: String, pkg: String) = s"""
    package $pkg
    class $creatorName {
      def create(doc: ${Types.document}): ${Types.documentSupport} =
        new $fullyQualifiedName {
          override val document: ${Types.document} = doc
        }
    }
  """

  private def buildCompilerSaveAction(fullyQualifiedName: String, creatorName: String, pkg: String) = s"""
    package $pkg
    class $creatorName {
      def create(
          c: ${Types.compiler},
          t: ${Types.compiler}#Tree,
          sf: ${Types.sourceFile},
          selStart: Int,
          selEnd: Int)
          : ${Types.compilerSupport} = {

        new $fullyQualifiedName {
          override val global: ${Types.compiler} = c
          override val sourceFile: ${Types.sourceFile} = sf
          override val selection = new FileSelection(
            sf.file, t.asInstanceOf[global.Tree], selStart, selEnd)
        }
      }
    }
  """

  private def buildAutoEdit(fullyQualifiedName: String, creatorName: String, pkg: String) = s"""
    package $pkg
    class $creatorName {
      def create(doc: ${Types.document}, change: ${Types.textChange}): $fullyQualifiedName =
        new $fullyQualifiedName {
          override val document: ${Types.document} = doc
          override val textChange: ${Types.textChange} = change
        }
    }
  """

  /**
   * Compiles `srcs` and makes defined classes available through [[classLoader]].
   */
  private def compile(srcs: Seq[String]): Unit = {
    reporter.reset()
    val srcFiles = srcs map (new BatchSourceFile("<memory>", _))
    val run = new compiler.Run

    compiler ask { () ⇒ run.compileSources(srcFiles.toList) }

    if (reporter.hasErrors || reporter.hasWarnings)
      throw new IllegalStateException(reporter.infos.mkString("Errors occurred during compilation of extension wrapper:\n", "\n", ""))
  }

  /**
   * Represents the state of the extension creator that is used to instantiate a
   * concrete extensions.
   */
  private sealed trait CreatorState

  /**
   * Represents an extension creator that has not yet been compiled and therefore
   * is not yet cached on disk. `src` is the source code that needs to be compiled,
   * `className` is the name of the creator that exists in `src` and `fn` is a
   * function that can access an instantiation of `className` through
   * reflection.
   */
  private case class Uncompiled(src: String, className: String, fn: (Class[_], Any) ⇒ Any) extends CreatorState

  /**
   * Represents an extension creator that has already been compiled and is
   * therefore stored on disk. `cls` is the class of the creator and `fn` is a
   * function that can access an instantiation of `className` through
   * reflection.
   */
  private case class Cached(cls: Class[_], fn: (Class[_], Any) ⇒ Any) extends CreatorState

  private def load(fullyQualifiedName: String): CreatorState = {
    val pkg = "org.scalaide.core.internal.generated"
    val creatorName = s"${fullyQualifiedName.split('.').last}Creator"

    val className = s"$pkg.$creatorName"
    val cachedCls = classLoader.tryToInitializeClass(className)
    val isCached = cachedCls.isDefined

    def allInterfacesOf(cls: Class[_]): Seq[Class[_]] = {
      val i = cls.getInterfaces
      i ++ (i flatMap allInterfacesOf)
    }

    val interfaces = try allInterfacesOf(classLoader.loadClass(fullyQualifiedName)) catch {
      case e: ClassNotFoundException ⇒ throw new IllegalArgumentException(s"Extension '$fullyQualifiedName' doesn't exist.", e)
    }
    val isDocumentSaveAction = Set(classOf[SaveAction], classOf[DocumentSupport]) forall interfaces.contains
    val isCompilerSaveAction = Set(classOf[SaveAction], classOf[CompilerSupport]) forall interfaces.contains
    val isAutoEdit = interfaces contains classOf[AutoEdit]

    def mkDocumentSaveAction = {
      val fn = (cls: Class[_], obj: Any) ⇒ {
        val m = cls.getMethod("create", classOf[Document])
        (doc: Document) ⇒
          m.invoke(obj, doc)
      }
      if (isCached)
        Cached(cachedCls.get, fn)
      else
        Uncompiled(buildDocumentSaveAction(fullyQualifiedName, creatorName, pkg), className, fn)
    }

    def mkCompilerSaveAction = {
      val fn = (cls: Class[_], obj: Any) ⇒ {
        val m = cls.getMethod("create", classOf[IScalaPresentationCompiler], classOf[IScalaPresentationCompiler#Tree], classOf[SourceFile], classOf[Int], classOf[Int])
        (c: IScalaPresentationCompiler, t: IScalaPresentationCompiler#Tree, src: SourceFile, selStart: Int, selEnd: Int) ⇒
          m.invoke(obj, c, t, src, Integer.valueOf(selStart), Integer.valueOf(selEnd))
      }
      if (isCached)
        Cached(cachedCls.get, fn)
      else
        Uncompiled(buildCompilerSaveAction(fullyQualifiedName, creatorName, pkg), className, fn)
    }

    def mkAutoEdit = {
      val fn = (cls: Class[_], obj: Any) ⇒ {
        val m = cls.getMethod("create", classOf[Document], classOf[TextChange])
        (doc: Document, change: TextChange) ⇒
          m.invoke(obj, doc, change)
      }
      if (isCached)
        Cached(cachedCls.get, fn)
      else
        Uncompiled(buildAutoEdit(fullyQualifiedName, creatorName, pkg), className, fn)
    }

    if (isDocumentSaveAction)
      mkDocumentSaveAction
    else if (isCompilerSaveAction)
      mkCompilerSaveAction
    else if (isAutoEdit)
      mkAutoEdit
    else
      throw new IllegalArgumentException(s"Extension '$fullyQualifiedName' couldn't be qualified as a valid extension.")
  }

  /**
   * Returns a function, which allows the instantiation of an extension
   * represented by `fullyQualifiedName`. The type of `A` can be one of the
   * types available in [[ExtensionCreator]].
   *
   * This returns a [[Try]] because there are many things that can go wrong. A
   * [[Failure]] is returned whenever `fullyQualifiedName` doesn't represent an
   * existing type, `A` is not one of the types available in
   * [[ExtensionCreator]], the compilation of an extension creator was not
   * successful, the loading of the cached extension creator failed or the
   * creation of an extension did not complete normally.
   *
   * If one does not care about the failure, [[savelyLoadExtension]] should be
   * used.
   */
  def loadExtension[A](fullyQualifiedName: String): Try[A] = Try {
    load(fullyQualifiedName) match {
      case Uncompiled(src, className, fn) ⇒
        compile(Seq(src))
        val cls = classLoader.loadClass(className)
        val obj = cls.newInstance()
        val res = fn(cls, obj).asInstanceOf[A]
        logger.debug(s"Compiling Scala IDE extension '$fullyQualifiedName' was successful.")
        res

      case Cached(cls, fn) ⇒
        val obj = cls.newInstance()
        val res = fn(cls, obj).asInstanceOf[A]
        logger.debug(s"Loading cached Scala IDE extension '$fullyQualifiedName' was successful.")
        res
    }
  }

  /**
   * See [[loadExtension]] for documentation about behavior of this method. If
   * anything goes wrong during the loading of an extension, [[None]] is
   * returned and an error message is logged.
   */
  def savelyLoadExtension[A](fullyQualifiedName: String): Option[A] = {
    loadExtension[A](fullyQualifiedName) match {
      case Success(ext) ⇒
        Some(ext)
      case Failure(f) ⇒
        logger.error(s"An error occurred while loading Scala IDE extension '$fullyQualifiedName'.", f)
        None
    }
  }

}
