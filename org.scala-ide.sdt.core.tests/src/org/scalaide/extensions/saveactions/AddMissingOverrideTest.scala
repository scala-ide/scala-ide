package org.scalaide.extensions.saveactions

import scala.reflect.internal.util.SourceFile

import org.junit.Ignore
import org.junit.Test
import org.scalaide.core.compiler.IScalaPresentationCompiler
import scala.tools.refactoring.util.UniqueNames

object AddMissingOverrideTest extends CompilerSaveActionTests {
  override def saveAction(spc: IScalaPresentationCompiler, tree: IScalaPresentationCompiler#Tree, sf: SourceFile, selectionStart: Int, selectionEnd: Int) =
    new AddMissingOverride {
      override val global = spc
      override val sourceFile = sf
      override val selection = new FileSelection(
          sf.file, tree.asInstanceOf[global.Tree], selectionStart, selectionEnd)
    }
}

class AddMissingOverrideTest {
  import AddMissingOverrideTest._

  @Test
  def no_change_when_nothing_is_overriden() = """^
    package no_change_when_nothing_is_overriden
    trait T
    trait TT extends T {
      def meth = 0
      val value = 0
      type Type = Int
    }
    """ becomes """^
    package no_change_when_nothing_is_overriden
    trait T
    trait TT extends T {
      def meth = 0
      val value = 0
      type Type = Int
    }
    """ after SaveEvent

  @Test
  def no_change_when_everything_is_already_overriden() = """^
    package no_change_when_everything_is_already_overriden
    trait T {
      def meth: Int
      val value: Int
      type Type
    }
    trait TT extends T {
      override def meth = 0
      override val value = 0
      override type Type = Int
    }
    """ becomes """^
    package no_change_when_everything_is_already_overriden
    trait T {
      def meth: Int
      val value: Int
      type Type
    }
    trait TT extends T {
      override def meth = 0
      override val value = 0
      override type Type = Int
    }
    """ after SaveEvent

  @Test
  def add_override_to_method() = """^
    package add_override_to_method
    trait T {
      def meth: Int
    }
    trait TT extends T {
      def meth = 0
    }
    """ becomes """^
    package add_override_to_method
    trait T {
      def meth: Int
    }
    trait TT extends T {
      override def meth = 0
    }
    """ after SaveEvent

  @Test
  def add_override_to_value() = """^
    package add_override_to_value
    trait T {
      val value: Int
    }
    trait TT extends T {
      val value = 0
    }
    """ becomes """^
    package add_override_to_value
    trait T {
      val value: Int
    }
    trait TT extends T {
      override val value = 0
    }
    """ after SaveEvent

  @Test
  def add_override_to_type() = """^
    package add_override_to_type
    trait T {
      type Type
    }
    trait TT extends T {
      type Type = Int
    }
    """ becomes """^
    package add_override_to_type
    trait T {
      type Type
    }
    trait TT extends T {
      override type Type = Int
    }
    """ after SaveEvent

  @Test
  def no_change_for_override_abstract() = """^
    package no_change_for_override_abstract
    trait T {
      def f = 0
    }
    trait TT extends T {
      abstract override def f = super.f+0
    }
    """ becomes """^
    package no_change_for_override_abstract
    trait T {
      def f = 0
    }
    trait TT extends T {
      abstract override def f = super.f+0
    }
    """ after SaveEvent

  @Test
  def add_override_modifier_to_constructor_param() = """^
    package add_override_modifier_to_constructor_param
    trait T {
      def meth: Int
    }
    case class C(val meth: Int) extends T
    class D(val meth: Int) extends T
    """ becomes """^
    package add_override_modifier_to_constructor_param
    trait T {
      def meth: Int
    }
    case class C(override val meth: Int) extends T
    class D(override val meth: Int) extends T
    """ after SaveEvent

  @Test
  def add_val_keyword_when_override_modifier_is_added_to_constructor_param() = """^
    package add_val_keyword_when_override_modifier_is_added_to_constructor_param
    trait T {
      def meth: Int
    }
    case class C(meth: Int) extends T
    class D(meth: Int) extends T
    """ becomes """^
    package add_val_keyword_when_override_modifier_is_added_to_constructor_param
    trait T {
      def meth: Int
    }
    case class C(override val meth: Int) extends T
    class D(override val meth: Int) extends T
    """ after SaveEvent

  @Test
  def no_change_to_constructor_param_when_nothing_is_overriden() = """^
    package no_change_to_constructor_param_when_nothing_is_overriden
    trait T {
      def meth: Int
    }
    case class B(meth: Int)
    class C(meth: Int)
    class D(meth: Int, func: Int) extends T
    """ becomes """^
    package no_change_to_constructor_param_when_nothing_is_overriden
    trait T {
      def meth: Int
    }
    case class B(meth: Int)
    class C(meth: Int)
    class D(override val meth: Int, func: Int) extends T
    """ after SaveEvent

  @Test @Ignore("Unimplemented. See ticket #1002222")
  def add_override_to_symbols_from_self_references() = """^
    package add_override_to_symbols_from_self_references
    trait T {
      def meth: Int
    }
    trait TT {
      this: T =>
      def meth = 0
    }
    """ becomes """^
    package add_override_to_symbols_from_self_references
    trait T {
      def meth: Int
    }
    trait TT {
      this: T =>
      override def meth = 0
    }
    """ after SaveEvent

  @Test
  def add_override_to_lazy_val() = """^
    package add_override_to_lazy_val
    trait T {
      def f: Int
    }
    trait TT extends T {
      lazy val f = 0
    }
    """ becomes """^
    package add_override_to_lazy_val
    trait T {
      def f: Int
    }
    trait TT extends T {
      override lazy val f = 0
    }
    """ after SaveEvent

  @Test
  def add_no_override_to_var_that_overrides_non_var() = """^
    package add_no_override_to_var_that_overrides_non_var
    trait T {
      def f: Int
    }
    trait TT extends T {
      var f = 0
    }
    """ becomes """^
    package add_no_override_to_var_that_overrides_non_var
    trait T {
      def f: Int
    }
    trait TT extends T {
      var f = 0
    }
    """ after SaveEvent

  @Test
  def add_override_to_var_that_overrides_a_var() = """^
    package add_override_to_var_that_overrides_a_var
    trait T {
      var f: Int
    }
    trait TT extends T {
      var f = 0
    }
    """ becomes """^
    package add_override_to_var_that_overrides_a_var
    trait T {
      var f: Int
    }
    trait TT extends T {
      override var f = 0
    }
    """ after SaveEvent

  @Test
  def add_override_to_def_that_overrides_java_method() = {
    val jPkg = UniqueNames.scalaPackage()
    val sPkg = UniqueNames.scalaPackage()
    mkJavaCompilationUnit(s"""
      package $jPkg;
      public class T {
        public String f() { return ""; }
      }
    """)

    s"""^
    package $sPkg
    import $jPkg.T
    trait TT extends T {
      def f = ""
    }
    """ becomes s"""^
    package $sPkg
    import $jPkg.T
    trait TT extends T {
      override def f = ""
    }
    """ after SaveEvent
  }

  @Test
  def add_no_override_to_val_that_overrides_java_field() = {
    val jPkg = UniqueNames.scalaPackage()
    val sPkg = UniqueNames.scalaPackage()
    mkJavaCompilationUnit(s"""
      package $jPkg;
      public class T {
        public String f = "";
      }
    """)

    s"""^
    package $sPkg
    import $jPkg.T
    trait TT extends T {
      val f = ""
    }
    """ becomes s"""^
    package $sPkg
    import $jPkg.T
    trait TT extends T {
      val f = ""
    }
    """ after SaveEvent
  }

  @Test
  def add_no_override_to_def_that_overrides_java_field() = {
    val jPkg = UniqueNames.scalaPackage()
    val sPkg = UniqueNames.scalaPackage()
    mkJavaCompilationUnit(s"""
      package $jPkg;
      public class T {
        public String f = "";
      }
    """)

    s"""^
    package $sPkg
    import $jPkg.T
    trait TT extends T {
      def f = ""
    }
    """ becomes s"""^
    package $sPkg
    import $jPkg.T
    trait TT extends T {
      def f = ""
    }
    """ after SaveEvent
  }
}
