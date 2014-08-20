package org.scalaide.extensions.saveactions

import org.scalaide.core.compiler.ScalaPresentationCompiler
import scala.reflect.internal.util.SourceFile
import org.junit.Test

object AddReturnTypeToPublicSymbolsTest extends CompilerSaveActionTests {
  override def saveAction(spc: ScalaPresentationCompiler, tree: ScalaPresentationCompiler#Tree, sf: SourceFile, selectionStart: Int, selectionEnd: Int) =
    new AddReturnTypeToPublicSymbols {
      override val global = spc
      override val sourceFile = sf
      override val selection = new FileSelection(
          sf.file, tree.asInstanceOf[global.Tree], selectionStart, selectionEnd)
    }
}

class AddReturnTypeToPublicSymbolsTest {
  import AddReturnTypeToPublicSymbolsTest._

  @Test
  def add_no_return_type_if_it_already_exists() = """^
    class X {
      def meth: java.io.File = new java.io.File("")
      val value: java.io.File = new java.io.File("")
      var value: java.io.File = new java.io.File("")
    }
    """ becomes """^
    class X {
      def meth: java.io.File = new java.io.File("")
      val value: java.io.File = new java.io.File("")
      var value: java.io.File = new java.io.File("")
    }
    """ after SaveEvent

  @Test
  def add_no_return_type_to_private_def() = """^
    class X {
      private def meth = new java.io.File("")
    }
    """ becomes """^
    class X {
      private def meth = new java.io.File("")
    }
    """ after SaveEvent

  @Test
  def add_no_return_type_to_private_val() = """^
    class X {
      private val value = new java.io.File("")
    }
    """ becomes """^
    class X {
      private val value = new java.io.File("")
    }
    """ after SaveEvent

  @Test
  def add_no_return_type_to_private_var() = """^
    class X {
      private var variable = new java.io.File("")
    }
    """ becomes """^
    class X {
      private var variable = new java.io.File("")
    }
    """ after SaveEvent

  @Test
  def add_no_return_type_to_nested_def() = """^
    class X {
      def meth: java.io.File = {
        def m = new java.io.File("")
        m
      }
      val value: java.io.File = {
        def m = new java.io.File("")
        m
      }
    }
    """ becomes """^
    class X {
      def meth: java.io.File = {
        def m = new java.io.File("")
        m
      }
      val value: java.io.File = {
        def m = new java.io.File("")
        m
      }
    }
    """ after SaveEvent

  @Test
  def add_no_return_type_to_nested_val() = """^
    class X {
      val value: java.io.File = {
        val m = new java.io.File("")
        m
      }
      def meth: java.io.File = {
        def m = new java.io.File("")
        m
      }
    }
    """ becomes """^
    class X {
      val value: java.io.File = {
        val m = new java.io.File("")
        m
      }
      def meth: java.io.File = {
        def m = new java.io.File("")
        m
      }
    }
    """ after SaveEvent

  @Test
  def add_no_return_type_to_nested_var() = """^
    class X {
      var variable: java.io.File = {
        var m = new java.io.File("")
        m
      }
      def meth: java.io.File = {
        def m = new java.io.File("")
        m
      }
    }
    """ becomes """^
    class X {
      var variable: java.io.File = {
        var m = new java.io.File("")
        m
      }
      def meth: java.io.File = {
        def m = new java.io.File("")
        m
      }
    }
    """ after SaveEvent

  @Test
  def add_return_type_to_public_def() = """^
    class X {
      def meth = new java.io.File("")
    }
    """ becomes """^
    class X {
      def meth: java.io.File = new java.io.File("")
    }
    """ after SaveEvent

  @Test
  def add_return_type_to_public_val() = """^
    class X {
      val value = new java.io.File("")
    }
    """ becomes """^
    class X {
      val value: java.io.File = new java.io.File("")
    }
    """ after SaveEvent

  @Test
  def add_return_type_to_public_var() = """^
    class X {
      val variable = new java.io.File("")
    }
    """ becomes """^
    class X {
      val variable: java.io.File = new java.io.File("")
    }
    """ after SaveEvent

  @Test
  def add_return_type_if_def_belongs_to_inner_class() = """^
    class X {
      class Y {
        trait Z1 {
          def meth = new java.io.File("")
        }
        trait Z2 {
          val value = new java.io.File("")
        }
        trait Z3 {
          var variable = new java.io.File("")
        }
      }
    }
    """ becomes """^
    class X {
      class Y {
        trait Z1 {
          def meth: java.io.File = new java.io.File("")
        }
        trait Z2 {
          val value: java.io.File = new java.io.File("")
        }
        trait Z3 {
          var variable: java.io.File = new java.io.File("")
        }
      }
    }
    """ after SaveEvent

  @Test
  def add_no_return_type_if_def_belongs_to_private_inner_class() = """^
    class X {
      private class Y {
        trait Z1 {
          def meth = new java.io.File("")
        }
        trait Z2 {
          val value = new java.io.File("")
        }
        trait Z3 {
          var variable = new java.io.File("")
        }
      }
    }
    """ becomes """^
    class X {
      private class Y {
        trait Z1 {
          def meth = new java.io.File("")
        }
        trait Z2 {
          val value = new java.io.File("")
        }
        trait Z3 {
          var variable = new java.io.File("")
        }
      }
    }
    """ after SaveEvent
}
