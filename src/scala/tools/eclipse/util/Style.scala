package scala.tools.eclipse.util

import scala.collection.jcl.{ ArrayList, LinkedHashSet }

import org.eclipse.jface.preference.PreferenceConverter
import org.eclipse.swt.graphics.{ Color, Image }
import org.eclipse.ui.editors.text.EditorsUI

import scala.tools.eclipse.ScalaPlugin
import scala.tools.eclipse.properties.EditorPreferences

trait Style {
  def foreground : Color
  def background : Color
  def bold : Boolean 
  def italics : Boolean 
  def strikeout : Boolean 
  def underline : Boolean
}

object Style {
  
  val foregroundId = ".Color.Foreground"
  val backgroundId = ".Color.Background"
  val boldId    = ".Bold"
  val italicsId = ".Italics"
  val underlineId = ".Underline"
  val strikeoutId = ".Strikeout"

  val commentStyle = KeyStyle("comment", Colors.salmon, false, false, false)
  val keywordStyle = KeyStyle("keyword", Colors.iron, true, false, false)
  val litStyle = KeyStyle("literal", Colors.salmon, false, true, false)
  val stringStyle  = KeyStyle("string", Colors.salmon, false, true, false)
  val charStyle = KeyStyle("char", Colors.salmon, false, true, false)
  val symbolStyle = KeyStyle("symbol", Colors.salmon, false, true, false)
  
  val classStyle = KeyStyle("class", Colors.mocha, false, false, false)
  val traitStyle = KeyStyle("trait", Colors.mocha, false, true, false)
  val typeStyle = KeyStyle("type", Colors.mocha, true, false, false)
  val objectStyle = KeyStyle("object", Colors.mocha, false, false, true)
  val packageStyle = KeyStyle("package", Colors.mocha, true, false, true)
  
  val valStyle = KeyStyle("val", Colors.blueberry, false, false, false)
  val varStyle = KeyStyle("var", Colors.blueberry, false, false, true)
  val defStyle = KeyStyle("def", Colors.ocean, false, false, false)
  val argStyle = KeyStyle("arg", Colors.blueberry, false, true, false)
  
  object noStyle extends Style {
    def underline = false
    def italics = false
    def strikeout = false
    def bold = false
    def background = Colors.noColor
    def foreground = Colors.noColor
  }
  
  case class KeyStyle(key : String, fgDflt : Color, boldDflt : Boolean, italicDflt : Boolean, ulDflt : Boolean)
    extends Style with EditorPreferences.Key {
    
    var foreground0 : Color = fgDflt
    var background0 : Color = noStyle.background
    var bold0 : Boolean = boldDflt
    var italics0 : Boolean = italicDflt
    var underline0 : Boolean = ulDflt
    var strikeout0 : Boolean = noStyle.strikeout
    
    refresh
    preferences.editorPreferences += this
    preferences.styles += this
    
    def default(suffix : String) = suffix match {
      case `foregroundId` => fgDflt
      case `backgroundId` => noStyle.background
      case `boldId` => boldDflt
      case `italicsId` => italicDflt
      case `underlineId` => ulDflt
      case `strikeoutId` => noStyle.strikeout
    }

    override def styleKey = ScalaPlugin.plugin.pluginId + "." + key
    
    def refresh {
      val store = EditorsUI.getPreferenceStore
      
      def color(what : String) : Color = {
        val key = styleKey + what
        if (store.isDefault(key)) {
          val ret = default(what).asInstanceOf[Color]
          PreferenceConverter.setValue(store, key, ret.getRGB)
          ret
        } else (Colors.colorMap(PreferenceConverter.getColor(store, key)))
      }
      
      def boolean(what : String) = {
        val key = styleKey + what
        store.getBoolean(key)
      }

      foreground0 = color(foregroundId)
      background0 = color(backgroundId)
      bold0 = boolean(boldId)
      underline0 = boolean(underlineId)
      italics0 = boolean(italicsId)
      strikeout0 = boolean(strikeoutId)
    }
   
    override def foreground = foreground0 
    override def background = background0
    override def bold = bold0
    override def italics = italics0
    override def underline = underline0
    override def strikeout = strikeout0
  }

  def imageFor(style : Style) : Option[Image] = {
    val kind = style match {
      case `classStyle` => "class"
      case `objectStyle` => "object"
      case `traitStyle` => "trait"
      case `defStyle` => "defpub"
      case `varStyle` => "valpub"
      case `valStyle` => "valpub"
      case `typeStyle` => "typevariable"
      case _ => return None
    }
    
    return Some(Images.fullIcon("obj16/" + kind + "_obj.gif"))
  }
  
  def initializeEditorPreferences = {
    val store = EditorsUI.getPreferenceStore
    preferences.styles.foreach { style =>
      store.setValue(style.styleKey + boldId, style.default(boldId).asInstanceOf[Boolean])
      store.setValue(style.styleKey + italicsId, style.default(italicsId).asInstanceOf[Boolean])
      store.setValue(style.styleKey + underlineId, style.default(underlineId).asInstanceOf[Boolean])
      store.setValue(style.styleKey + strikeoutId, style.default(strikeoutId).asInstanceOf[Boolean])
      PreferenceConverter.setValue(store, style.styleKey + foregroundId, style.default(foregroundId).asInstanceOf[Color].getRGB)
      PreferenceConverter.setValue(store, style.styleKey + backgroundId, style.default(backgroundId).asInstanceOf[Color].getRGB)
    }
  }
  
  object preferences {
    val editorPreferences = new ArrayList[scala.tools.eclipse.properties.EditorPreferences.Key]
    val styles = new LinkedHashSet[KeyStyle]
  }
}