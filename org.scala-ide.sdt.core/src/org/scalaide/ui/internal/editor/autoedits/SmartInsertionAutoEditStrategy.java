package org.scalaide.ui.internal.editor.autoedits;

import org.eclipse.core.runtime.Assert;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.javaeditor.CompilationUnitEditor;
import org.eclipse.jdt.internal.ui.text.SmartBackspaceManager;
import org.eclipse.jdt.internal.ui.text.SmartBackspaceManager.UndoSpec;
import org.eclipse.jdt.internal.ui.text.java.SmartSemicolonAutoEditStrategy;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.DocumentCommand;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.text.ITypedRegion;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.TextSelection;
import org.eclipse.jface.text.TextUtilities;
import org.eclipse.text.edits.DeleteEdit;
import org.eclipse.text.edits.MalformedTreeException;
import org.eclipse.text.edits.ReplaceEdit;
import org.eclipse.text.edits.TextEdit;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.texteditor.ITextEditorExtension2;
import org.eclipse.ui.texteditor.ITextEditorExtension3;
import org.scalaide.ui.internal.preferences.EditorPreferencePage;


/**
 * !!! ATTENTION !!!
 * 
 * This code is an exact copy paste of the superclass. The difference to the
 * superclass is that it accesses the Scala preference store instead of the Java
 * one. You should NOT try to update this class but to replace it with a real
 * implementation that is Scala aware. It was copied to make Scala preferences
 * available and should not be maintained. For documentation of the methods see
 * the superclass.
 */
@SuppressWarnings("restriction")
public class SmartInsertionAutoEditStrategy extends SmartSemicolonAutoEditStrategy {
  
  /** String representation of a semicolon. */
  private static final String SEMICOLON= ";"; //$NON-NLS-1$
  /** Char representation of a semicolon. */
  private static final char SEMICHAR= ';';
  /** String represenattion of a opening brace. */
  private static final String BRACE= "{"; //$NON-NLS-1$
  /** Char representation of a opening brace */
  private static final char BRACECHAR= '{';
  
  private char fCharacter;
  private String fPartitioning;
  private IPreferenceStore fPrefStore;

  public SmartInsertionAutoEditStrategy(String partitioning, IPreferenceStore prefStore) {
    super(partitioning);
    fPartitioning = partitioning;
    fPrefStore = prefStore;
  }
  
  public void customizeDocumentCommand(IDocument document, DocumentCommand command) {
    // 0: early pruning
    // also customize if <code>doit</code> is false (so it works in code completion situations)
    //    if (!command.doit)
    //      return;

    if (command.text == null)
      return;

    if (command.text.equals(SEMICOLON))
      fCharacter= SEMICHAR;
    else if (command.text.equals(BRACE))
      fCharacter= BRACECHAR;
    else
      return;

    if (fCharacter == SEMICHAR && !fPrefStore.getBoolean(EditorPreferencePage.P_ENABLE_SMART_INSERTION_SEMICOLONS()))
      return;
    if (fCharacter == BRACECHAR && !fPrefStore.getBoolean(EditorPreferencePage.P_ENABLE_SMART_INSERTION_BRACES()))
      return;

    IWorkbenchPage page= JavaPlugin.getActivePage();
    if (page == null)
      return;
    IEditorPart part= page.getActiveEditor();
    if (!(part instanceof CompilationUnitEditor))
      return;
    CompilationUnitEditor editor= (CompilationUnitEditor)part;
    if (editor.getInsertMode() != ITextEditorExtension3.SMART_INSERT || !editor.isEditable())
      return;
    ITextEditorExtension2 extension= (ITextEditorExtension2)editor.getAdapter(ITextEditorExtension2.class);
    if (extension != null && !extension.validateEditorInputState())
      return;
    if (isMultilineSelection(document, command))
      return;

    // 1: find concerned line / position in java code, location in statement
    int pos= command.offset;
    ITextSelection line;
    try {
      IRegion l= document.getLineInformationOfOffset(pos);
      line= new TextSelection(document, l.getOffset(), l.getLength());
    } catch (BadLocationException e) {
      return;
    }

    // 2: choose action based on findings (is for-Statement?)
    // for now: compute the best position to insert the new character
    int positionInLine= computeCharacterPosition(document, line, pos - line.getOffset(), fCharacter, fPartitioning);
    int position= positionInLine + line.getOffset();

    // never position before the current position!
    if (position < pos)
      return;

    // never double already existing content
    if (alreadyPresent(document, fCharacter, position))
      return;

    // don't do special processing if what we do is actually the normal behaviour
    String insertion= adjustSpacing(document, position, fCharacter);
    if (command.offset == position && insertion.equals(command.text))
      return;

    try {

      final SmartBackspaceManager manager= (SmartBackspaceManager) editor.getAdapter(SmartBackspaceManager.class);
      if (manager != null) {
        TextEdit e1= new ReplaceEdit(command.offset, command.text.length(), document.get(command.offset, command.length));
        UndoSpec s1= new UndoSpec(command.offset + command.text.length(),
            new Region(command.offset, 0),
            new TextEdit[] {e1},
            0,
            null);

        DeleteEdit smart= new DeleteEdit(position, insertion.length());
        ReplaceEdit raw= new ReplaceEdit(command.offset, command.length, command.text);
        UndoSpec s2= new UndoSpec(position + insertion.length(),
            new Region(command.offset + command.text.length(), 0),
            new TextEdit[] {smart, raw},
            2,
            s1);
        manager.register(s2);
      }

      // 3: modify command
      command.offset= position;
      command.length= 0;
      command.caretOffset= position;
      command.text= insertion;
      command.doit= true;
      command.owner= null;
    } catch (MalformedTreeException e) {
      JavaPlugin.log(e);
    } catch (BadLocationException e) {
      JavaPlugin.log(e);
    }


  }
  
  private boolean isMultilineSelection(IDocument document, DocumentCommand command) {
    try {
      return document.getNumberOfLines(command.offset, command.length) > 1;
    } catch (BadLocationException e) {
      // ignore
      return false;
    }
  }
  
  private boolean alreadyPresent(IDocument document, char ch, int position) {
    int pos= firstNonWhitespaceForward(document, position, fPartitioning, document.getLength());
    try {
      if (pos != -1 && document.getChar(pos) == ch)
        return true;
    } catch (BadLocationException e) {
    }

    return false;
  }
  
  private static boolean isDefaultPartition(IDocument document, int position, String partitioning) {
    Assert.isTrue(position >= 0);
    Assert.isTrue(position <= document.getLength());

    try {
      // don't use getPartition2 since we're interested in the scanned character's partition
      ITypedRegion region= TextUtilities.getPartition(document, partitioning, position, false);
      return region.getType().equals(IDocument.DEFAULT_CONTENT_TYPE);

    } catch (BadLocationException e) {
    }

    return false;
  }
  
  private static int firstNonWhitespaceForward(IDocument document, int position, String partitioning, int bound) {
    Assert.isTrue(position >= 0);
    Assert.isTrue(bound <= document.getLength());

    try {
      while (position < bound) {
        char ch= document.getChar(position);
        if (!Character.isWhitespace(ch) && isDefaultPartition(document, position, partitioning))
          return position;
        position++;
      }
    } catch (BadLocationException e) {
    }
    return -1;
  }
  
  private String adjustSpacing(IDocument doc, int position, char character) {
    if (character == BRACECHAR) {
      if (position > 0 && position <= doc.getLength()) {
        int pos= position - 1;
        if (looksLike(doc, pos, ")") //$NON-NLS-1$
        || looksLike(doc, pos, "=") //$NON-NLS-1$
        || looksLike(doc, pos, "]") //$NON-NLS-1$
        || looksLike(doc, pos, "try") //$NON-NLS-1$
        || looksLike(doc, pos, "else") //$NON-NLS-1$
        || looksLike(doc, pos, "synchronized") //$NON-NLS-1$
        || looksLike(doc, pos, "static") //$NON-NLS-1$
        || looksLike(doc, pos, "finally") //$NON-NLS-1$
        || looksLike(doc, pos, "do")) //$NON-NLS-1$
          return new String(new char[] { ' ', character });
      }
    }

    return new String(new char[] { character });
  }
  
  private static boolean looksLike(IDocument document, int position, String like) {
    int length= like.length();
    if (position < length - 1)
      return false;

    try {
      if (!like.equals(document.get(position - length + 1, length)))
        return false;

      if (position >= length && Character.isJavaIdentifierPart(like.charAt(0)) && Character.isJavaIdentifierPart(document.getChar(position - length)))
        return false;

    } catch (BadLocationException e) {
      return false;
    }

    return true;
  }
}
