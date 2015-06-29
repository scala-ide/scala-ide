package org.scalaide.core.text

/**
 * Base trait for all changes that should be done by a
 * [[org.scalaide.extensions.ScalaIdeExtension]].
 *
 * See the available subclasses and the the concrete Scala IDE extensions for
 * more documentation about what values this trait may represent.
 */
trait Change

object TextChange {

  /**
   * Creates a text change instance of one of the types [[Add]], [[Remove]] or
   * [[Replace]]. The one that best fits the parameters is chosen.
   */
  def apply(start: Int, end: Int, text: String): TextChange =
    if (start == end)
      Add(start, text)
    else if (text.isEmpty())
      Remove(start, end)
    else
      Replace(start, end, text)

  def unapply(change: TextChange): Option[(Int, Int, String)] =
    Some((change.start, change.end, change.text))
}

/**
 * A text change represents an action that changes the underlying document of an
 * editor.
 */
trait TextChange extends Change {
  def start: Int
  def end: Int
  def text: String

  def copy(start: Int = this.start, end: Int = this.end, text: String = this.text): TextChange =
    TextChange(start, end, text)

  /**
   * Associates this text change instance with an instance of [[CursorUpdate]].
   */
  def withCursorPos(pos: Int): CursorUpdate =
    CursorUpdate(this, pos)

  /**
   * Associates this text change instance with an instance of [[LinkedModel]].
   */
  def withLinkedModel(exitPosition: Int, positionsGroups: Seq[Seq[(Int, Int)]] = Seq()): LinkedModel =
    LinkedModel(this, exitPosition, positionsGroups)

  override def toString(): String =
    s"""TextChange(start=$start, end=$end, text="$text")"""
}

/**
 * Adds `text` to the position `start`. `end` is equal to `start` which means
 * that this change object can not replace existing text in the document.
 */
case class Add(override val start: Int, override val text: String) extends TextChange {
  override val end = start
}

/**
 * Replaces existing text in the document. The range that is spanned by `start`
 * and `end` is replaced with `text.
 */
case class Replace(override val start: Int, override val end: Int, override val text: String) extends TextChange

/**
 * Removes existing text in the document. The range that is spanned by `start`
 * and `end` is removed and no further text is added.
 */
case class Remove(override val start: Int, override val end: Int) extends TextChange {
  override val text = ""
}

/**
 * Updates the position of the cursor in the editor.
 *
 * @param textChange
 *        The associated [[TextChange]] object.
 * @param cursorPosition
 *        The new position of the cursor.
 * @param smartBackspaceEnabled
 *        Enables the smart backspace functionality. When it is enabled, the
 *        next pressed backspace key doesn't remove the character before the
 *        cursor position (what it would do normally), but undos the last text
 *        change. This is especially useful when the user wants to perform a
 *        specific change with performing additional text change actions by the
 *        IDE. Instead of disabling the feature altogether, the user can press
 *        the backspace key to get their original change back after the IDE has
 *        performed its action.
 *
 *        When the user leaves the edit area of the text change with the
 *        cursor without using the smart backspace action, it is removed and can
 *        no longer be used.
 */
case class CursorUpdate(textChange: TextChange, cursorPosition: Int, smartBackspaceEnabled: Boolean = false) extends Change

/**
 * Creates a linked model. A linked model is a feature of the text editor that
 * allows the user to jump between regions of text and edit them simultaneously.
 * Once the linked model is created, the tab and enter key behave differently.
 * With the tab key it is possible to jump between the positions of the linked
 * model, whereas with the enter key it is possible to jump directly to its exit
 * position. After enter is pressed or the text change area is left, the linked
 * model is removed from the editor. It is visually possible to see if a linked
 * models is enabled - it is represented by boxes that surround the text
 * selections and a green bar that marks the exit position.
 *
 * @param textChange
 *        The associated [[TextChange]] object. A linked model can only be used
 *        with a text change, therefore this association exists.
 * @param exitPosition
 *        The position where the cursor jumps to whenever the user presses
 *        enter. When the user leaves the text change area without jumping to
 *        the exit position, it is removed and can no longer be used.
 * @param positionGroups
 *        The positions to which the user jumps to whenever they press the tab
 *        key. The first element of the tuple represents the position itself,
 *        while the second element represents its size, i.e. the number of
 *        characters that get selected. Each sequence represents a new group.
 *
 *        Every time the tab key is pressed the cursor moves to the next
 *        position. If the tab key is pressed, when the last position is
 *        selected, the first position will be selected. By pressing shift-tab,
 *        the user can jump backwards. If a linked model gets nested into
 *        another linked model it is no longer possible to cycle between the
 *        positions. Instead, when the last position of the inner linked model
 *        is selected and the tab key is pressed, the next element of the outer
 *        linked model is selected.
 *
 *        Multiple positions may represent a group. When an element of such a
 *        group is selected and the user types some text, all selections of this
 *        group are updated simultaneously.
 */
case class LinkedModel(textChange: TextChange, exitPosition: Int, positionGroups: Seq[Seq[(Int, Int)]] = Seq()) extends Change
