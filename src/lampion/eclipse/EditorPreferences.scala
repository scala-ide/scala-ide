/*
 * Copyright 2005-2009 LAMP/EPFL
 * @author Sean McDirmid
 */
// $Id$

package lampion.eclipse
import org.eclipse.jface._
import org.eclipse.jface.util._
import org.eclipse.jface.preference._
import org.eclipse.ui._
import org.eclipse.swt.widgets._
import org.eclipse.swt.custom._
import org.eclipse.swt.layout._
import org.eclipse.swt.events._
import org.eclipse.swt.SWT
import scala.collection.jcl._
import org.eclipse.swt.graphics._
import scala.xml._
import org.eclipse.jface.preference.PreferenceConverter

object EditorPreferences {
  trait Key {
    def styleKey : String
    def default(appendix : String) : Any
    def has(attribute : String) : Boolean = true
    def refresh : Unit
  }
}

abstract class EditorPreferences extends org.eclipse.jface.preference.PreferencePage with IWorkbenchPreferencePage {
  def plugin : UIPlugin 
  def sampleCode : NodeSeq
  
  import EditorPreferences.Key
  override def init(page : IWorkbench) = {}
  private var keys : List = _
  private var text : StyledText = _
  private var widgets : scala.List[Widget[Any]] = _
  private def setText = {
    val plugin = this.plugin
    import plugin.rgb2color
    val store = plugin.editorPreferenceStore
    val sc = sampleCode.elements.next
    def parent(key : Key) = key match {
    case plugin.KeyStyle(x,y) => y.parent match {
      case Some(key : plugin.KeyStyle) => Some(key)
      case None => None
    } 
    case _ => None
    }
    def getAttribute(key : Key, appendix : String) : Boolean = attributes.get((key,appendix)) match {
    case Some(value) => value
    case None => store.getBoolean(key.styleKey + appendix) || 
      (parent(key).map(key => getAttribute(key, appendix)).getOrElse(false))
    }
    def getColor(key : Key, appendix : String) : Color = colors.get((key,appendix)) match {
    case Some(None) | None => 
      val clr : Color = PreferenceConverter.getColor(store, key.styleKey + appendix)
      if (clr != null) clr
      else parent(key).map(key => getColor(key, appendix)) getOrElse null
    case Some(Some(rgb)) => plugin.colorMap(rgb)
    }
    { 
      import org.eclipse.ui.texteditor.AbstractTextEditor
      val fg = plugin.colorMap(PreferenceConverter.getColor(store, AbstractTextEditor.PREFERENCE_COLOR_FOREGROUND))
      val bg = plugin.colorMap(PreferenceConverter.getColor(store, AbstractTextEditor.PREFERENCE_COLOR_BACKGROUND))
      text.setForeground(fg)
      text.setBackground(bg)
    }
    if (sc.label == "code") {
      val buffer = new StringBuilder
      val styles = new ArrayList[StyleRange]
    
      def f(nodes : Seq[Node]) : Unit = nodes.foreach{
      case node : Elem =>
        assert(node.child.length == 1)
        val text : String = node.child.elements.next match {
        case scala.xml.Text(text) => text
        }
        val offset = buffer.length
        val idx = keys.indexOf(node.label)
        buffer append text
        if (idx != -1) {
          val key = plugin.preferences.editorPreferences(idx)        
          val fg = getColor(key, plugin.foregroundId)
          val bg = getColor(key, plugin.backgroundId)
          val italics = getAttribute(key, plugin.italicsId)
          val underline = getAttribute(key, plugin.underlineId)
          val bold = getAttribute(key, plugin.boldId)
          val strikeout = getAttribute(key, plugin.strikeoutId)
          val range = new StyleRange
          range.start = offset
          range.length = text.length
          range.foreground = fg
          range.background = bg
          range.underline = underline
          range.strikeout = strikeout
          range.fontStyle = (if (bold) SWT.BOLD else SWT.NORMAL) |
            (if (italics) SWT.ITALIC else SWT.NORMAL)
          styles += (range)
        }
      case scala.xml.Text(text) => buffer append text // no highlighting
      case node : scala.xml.Atom[_] => buffer append node.text
      }
      f(sc.child)
      text.setText(buffer.toString)
      text.setStyleRanges(styles.toArray)
    }
  }
  
  override def createContents(parent : Composite) : Control = {
    val plugin = this.plugin
    val top = new Composite(parent, SWT.NONE)
    top.setLayout(new RowLayout(SWT.VERTICAL))
    val composite = new Composite(top, SWT.NONE)
    text = new StyledText(top, SWT.READ_ONLY)
    val compositeLayout = new RowLayout(SWT.HORIZONTAL) 
    compositeLayout.spacing = 20
    composite.setLayout(compositeLayout)
    keys = new List(composite, SWT.SINGLE|SWT.V_SCROLL)

    plugin.preferences.editorPreferences.foreach{k => 
      val cs = k.styleKey.split('.')
      keys add cs(cs.length - 1)
    }
    val options = new Composite(composite, SWT.NONE)
    options.setLayout(new RowLayout(SWT.VERTICAL))

    val fg = new ColorWidget(options)(true)
    val bg = new ColorWidget(options)(false)
    val bold = new AttributeWidget(options)("bold", plugin.boldId)
    val italics = new AttributeWidget(options)("italics", plugin.italicsId)
    val underline = new AttributeWidget(options)("underline", plugin.underlineId)
    widgets = fg :: bg :: bold :: italics :: underline :: Nil
    keys.addSelectionListener(new SelectionListener {
      def widgetDefaultSelected(event : SelectionEvent) = widgetSelected(event)
      def widgetSelected(event : SelectionEvent) = {
        val key = keys.getSelectionIndex match {
        case -1 => null 
        case n => plugin.preferences.editorPreferences(n)
        }
        widgets.foreach(_ setKey key)
      }
    })
    composite.pack
    keys.setSize(100, 150)
    setText

    //noDefaultAndApplyButton
    top.pack
    top
  }
  val colors     = new LinkedHashMap[(Key,String),Option[RGB]]
  val attributes = new LinkedHashMap[(Key,String),Boolean]

  override def performDefaults : Unit = {
    super.performDefaults
    val plugin = this.plugin
    val store = plugin.editorPreferenceStore
    plugin.preferences.editorPreferences.foreach{key =>
      (plugin.foregroundId::plugin.backgroundId::Nil).foreach{appendix =>
        val default = key.default(appendix)
        colors((key,appendix)) = default match {
        case null => None
        case clr : Color => Some(clr.getRGB)
        }
      }
      (plugin.boldId::plugin.italicsId::plugin.underlineId::Nil).foreach{appendix =>
        val default = key.default(appendix)
        attributes((key,appendix)) = default.asInstanceOf[Boolean]
      }
    }
    widgets.foreach(w => if (w.key != null) w.initialize(w.key, store))
    setText
  }
  override def performOk : Boolean = {
    val plugin = this.plugin
    val store = plugin.editorPreferenceStore
    val refresh = new scala.collection.jcl.LinkedHashSet[Key]
    colors.foreach{
      case ((key,appendix),rgb) => 
        refresh += key
        if (rgb.isDefined)
          PreferenceConverter.setValue(store, key.styleKey + appendix, rgb.get)
        else store.setValue(key.styleKey + appendix, "-1")
    }
    attributes.foreach{
      case ((key,appendix),value) => 
        refresh += key
        store.setValue(key.styleKey + appendix, value)
    }
    refresh.foreach(_.refresh)
    org.eclipse.ui.internal.editors.text.EditorsPlugin.getDefault.savePluginPreferences
    true
  }
  
  abstract class Widget[+T](parent : Composite) {
    protected def control : Button
    def appendix : String
    var key : Key = _
    
    def setKey(key : Key) = if (key == null || !key.has(appendix)) {
      control.setEnabled(false)
      this.key = null
    } else {
      this.key = key
      val store = plugin.editorPreferenceStore
      control.setEnabled(true)
      initialize(key, store)
    }
    def initialize(key : Key, store : IPreferenceStore) : Unit
  }
  class AttributeWidget(parent : Composite)(title : String, val appendix : String) extends Widget[Boolean](parent) {
    val control = new Button(parent, SWT.CHECK)
    control.setText(title)
    setKey(null)
    control.addSelectionListener(new SelectionListener {
      def widgetDefaultSelected(event : SelectionEvent) = widgetSelected(event)
      def widgetSelected(event : SelectionEvent) = {
        attributes((key,appendix)) = control.getSelection
        setText
      }
    })
    def initialize(key : Key, store : IPreferenceStore) : Unit = {
      control.setSelection(attributes.get((key,appendix)) match {
      case Some(v) => v
      case _ => store.getBoolean(key.styleKey + appendix)
      })
    }
  }
  class ColorWidget(parent : Composite)(isForeground : Boolean) extends Widget[RGB](parent) {
    val composite = new Composite(parent, 0) // SWT.SHADOW_ETCHED_IN)
    def appendix = if (isForeground) plugin.foregroundId else plugin.backgroundId

    {
      val layout= new GridLayout
      layout.numColumns= 2
      composite setLayout layout
      //composite.setText((if (isForeground) "fore" else "back") + "ground")
    }
    val control = new Button(composite, SWT.CHECK)
    val colorSelector = new ColorSelector(composite)
    control.setText((if (isForeground) "fore" else "back") + "ground")
    control.addSelectionListener(new SelectionListener {
      def widgetDefaultSelected(event : SelectionEvent) = widgetSelected(event)
      def widgetSelected(event : SelectionEvent) = {
        colorSelector.getButton.setEnabled(control.getSelection)
        if (!control.getSelection) 
          colors((key,appendix)) = None // no color
          setText
      }
    })
    colorSelector.addListener(new IPropertyChangeListener {
      override def propertyChange(event : PropertyChangeEvent) = {
        colors((key,appendix)) = Some(colorSelector.getColorValue)
        setText
      }
    })
    setKey(null)

    def initialize(key : Key, store : IPreferenceStore) : Unit = {
      val clr = colors.get((key,appendix)) match {
      case Some(clr) => clr.map(plugin.colorMap)
      case _ => plugin.colorMap(PreferenceConverter.getColor(store, key.styleKey + appendix)) match{
        case null => None
        case clr => Some(clr)
        }
      }
      control.setSelection(clr.isDefined)
      colorSelector.setEnabled(clr.isDefined)
      if (clr.isDefined) 
        colorSelector.setColorValue(clr.get.getRGB)
      else colorSelector.setColorValue(new RGB(0,0,0))
    }
  }
}
