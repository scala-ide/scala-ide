/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse.javaelements

import org.eclipse.core.resources.IFile

import scala.tools.nsc.symtab.Flags

import scala.tools.eclipse.ScalaPlugin
import scala.tools.eclipse.ScalaPresentationCompiler
import scala.tools.eclipse.ScalaSourceIndexer
import scala.tools.eclipse.SettingConverterUtil
import scala.tools.eclipse.properties.ScalaPluginSettings

/** Add entries to the JDT index. This class traverses an *unattributed* Scala AST. This
 *  means a tree without symbols or types. However, a tree that was typed may still get here
 *  (usually during reconciliation, after editing and saving a file). That adds some complexity
 *  because the Scala typechecker modifies the tree. One prime example is annotations, which
 *  after type-checking are removed from the tree and placed in the corresponding Symbol.
 *
 *  The indexer builds a map from names to documents that mention that name. Names are
 *  categorized (for instance, as method definitions, method references, annotation references, etc.).
 *
 *  The indexer is later used by the JDT to narrow the scope of a search. For instance, a search
 *  for test methods would first check the index for documents that have annotation references to
 *  'Test' in 'org.junit', and then pass those documents to the structure builder for
 *  precise parsing, where names are actually resolved.
 */
trait ScalaIndexBuilder { self : ScalaPresentationCompiler =>

  class IndexBuilderTraverser(indexer : ScalaSourceIndexer) extends Traverser {
    var packageName = new StringBuilder

    def addPackageName(p: Tree) {
      p match {
        case i: Ident =>
          packageName.append(i.name)
        case r: Select =>
          addPackageName(r.qualifier)
          packageName.append('.').append(r.name)
      }
    }

    def addPackage(p : PackageDef) = {
      if (!packageName.isEmpty) packageName.append('.')
      if (p.name != nme.EMPTY_PACKAGE_NAME && p.name != nme.ROOTPKG) {
        addPackageName(p.pid)
      }
    }

    def getSuperNames(supers: List[Tree]): Array[Array[Char]] = {
      val superNames = supers map {
        case Ident(id)                           => id.toChars
        case Select(_, name)                     => name.toChars
        case AppliedTypeTree(fun: RefTree, args) => fun.name.toChars
        case tpt @ TypeTree()                    => mapType(tpt.symbol).toCharArray // maybe the tree was typed
        case parent =>
          logger.info(s"superclass not understood: $parent")
          "$$NoRef".toCharArray
      }
      superNames.toArray
    }

    /** Add annotations on the given tree. If the tree is not yet typed,
     *  it uses the (unresolved) annotations in the tree (part of modifiers).
     *  If the modifiers are empty, it uses the Symbol for finding them (the type-checker
     *  moves the annotations from the tree to the symbol).
     */
    def addAnnotations(tree: MemberDef) {
      if (tree.mods.annotations.isEmpty && tree.symbol.isInitialized) // don't force any symbols
        addAnnotations(tree.symbol)
      else
        tree.mods.annotations.foreach(addAnnotationRef)
    }

    private def addAnnotations(sym: Symbol) =
      for {
        ann <- sym.annotations
        annotationType <- self.askOption(() => ann.atp.toString.toCharArray)
      } indexer.addAnnotationTypeReference(annotationType)

    private def addAnnotationRef(tree: Tree) {
      for (t <- tree) t match {
        case New(tpt) =>
          indexer.addAnnotationTypeReference(tpt.toString.toCharArray)
        case _ => ()
      }
    }

    def addClass(c : ClassDef) {
      indexer.addClassDeclaration(
        mapModifiers(c.mods),
        packageName.toString.toCharArray,
        c.name.toChars,
        enclClassNames.reverse.toArray,
        Array.empty,
        getSuperNames(c.impl.parents),
        Array.empty,
        true
      )

      addAnnotations(c)
    }

    def addModule(m: ModuleDef) {
      val moduleName = m.name
      List(moduleName, moduleName.append('$')) foreach { name =>
        indexer.addClassDeclaration(
          mapModifiers(m.mods),
          packageName.toString.toCharArray,
          name.toChars,
          enclClassNames.reverse.toArray,
          Array.empty,
          getSuperNames(m.impl.parents),
          Array.empty,
          true)
      }
    }

    def addVal(v : ValDef) {
      indexer.addMethodDeclaration(
        nme.getterName(v.name).toChars,
        Array.empty,
        mapType(v.tpt.symbol).toArray,
        Array.empty
      )

      if(v.mods.hasFlag(Flags.MUTABLE))
        indexer.addMethodDeclaration(
          nme.getterToSetter(nme.getterName(v.name)).toChars,
          Array.empty,
          mapType(v.tpt.symbol).toArray,
          Array.empty
        )
      addAnnotations(v)
    }

    def addDef(d : DefDef) {
      val name = if(nme.isConstructorName(d.name)) enclClassNames.head else d.name.toChars

      val fps = for(vps <- d.vparamss; vp <- vps) yield vp

      val paramTypes = fps.map(v => mapType(v.tpt.symbol))
      indexer.addMethodDeclaration(
        name,
        paramTypes.map(_.toCharArray).toArray,
        mapType(d.tpt.symbol).toArray,
        Array.empty
      )
      addAnnotations(d)
    }

    def addType(td : TypeDef) {
      // We don't care what to add, java doesn't see types anyway.
      indexer.addClassDeclaration(
        mapModifiers(td.mods),
        packageName.toString.toCharArray,
        td.name.toChars,
        Array.empty,
        Array.empty,
        Array.empty,
        Array.empty,
        true
      )
    }

    var enclClassNames = List[Array[Char]]()

    override def traverse(tree: Tree): Unit = {
      def inClass(c : Array[Char])(block : => Unit) {
        val old = enclClassNames
        enclClassNames = c::enclClassNames
        block
        enclClassNames = old
      }

      /** Add several method reference in the indexer for the passed [[RefTree]].
       *
       * Adding method references in the indexer is tricky for methods that have default arguments.
       * The problem is that method entries are encoded as selector '/' Arity, i.e., foo/0 for `foo()`.
       *
       * Hence, `addApproximateMethodReferences` adds {{{22 - minArgsNumber}} method reference entries in
       * the indexer, for the passed [[RefTree]].
       */
      def addApproximateMethodReferences(tree: RefTree, minArgsNumber: Int = 0): Unit = {
        val maxArgs = 22  // just arbitrary choice.
        for (i <- minArgsNumber to maxArgs)
          indexer.addMethodReference(tree.name.toChars, i)
      }

      tree match {
        case pd : PackageDef => addPackage(pd)
        case cd : ClassDef => addClass(cd)
        case md : ModuleDef => addModule(md)
        case vd : ValDef => addVal(vd)
        case td : TypeDef => addType(td)
        case dd : DefDef if dd.name != nme.MIXIN_CONSTRUCTOR => addDef(dd)

        case _ =>
      }

      tree match {
        case cd : ClassDef => inClass(cd.name.toChars) { super.traverse(tree) }
        case md : ModuleDef => inClass(md.name.append("$").toChars) { super.traverse(tree) }

        case Apply(rt : RefTree, args) =>
          addApproximateMethodReferences(rt, args.size)
          super.traverse(tree)

        // Partial apply.
        case Typed(ttree, Function(_, _)) =>
          ttree match {
            case rt : RefTree =>
              addApproximateMethodReferences(rt)
            case Apply(rt : RefTree, args) =>
              addApproximateMethodReferences(rt, args.size)
            case _ =>
          }
          super.traverse(tree)

        case rt : RefTree =>
          val name = rt.name.toChars
          indexer.addTypeReference(name)
          indexer.addMethodReference(name, 0)
          if(nme.isSetterName(rt.name)) indexer.addFieldReference(nme.setterToGetter(rt.name.toTermName).toChars)
          else indexer.addFieldReference(name)
          super.traverse(tree)

        case _ => super.traverse(tree)
      }
    }
  }
}
