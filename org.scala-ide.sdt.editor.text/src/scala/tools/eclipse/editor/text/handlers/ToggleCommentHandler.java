package scala.tools.eclipse.editor.text.handlers;

import java.util.Enumeration;
import java.util.ResourceBundle;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextOperationTarget;
import org.eclipse.jface.text.TextSelection;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.editors.text.TextEditor;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.part.MultiPageEditorPart;
import org.eclipse.ui.texteditor.TextOperationAction;

/**
 * Command handler for toggling comments. The handler detects if comments should
 * be added or removed from the currently selected lines by inspect the first non-whitespace
 * character on each line. If the first character is '#' comments are removed. Otherwise
 * a '#' character is added to each line.
 * @author oysteto
 *
 */
public class ToggleCommentHandler extends AbstractHandler {

	
	public Object execute(ExecutionEvent event) throws ExecutionException {

		ISelection selection = HandlerUtil.getCurrentSelection( event );
		
		if( selection == null || ! ( selection instanceof TextSelection ) ){
			return null;
		}
		
		TextSelection ts = (TextSelection) selection;
			
		//TODO Log.logger.info( ts.getStartLine() + " " + ts.getEndLine() );
		IEditorPart editorPart = HandlerUtil.getActiveEditor(event);
		if( !( editorPart instanceof TextEditor ) && !(editorPart instanceof MultiPageEditorPart )){
			//TODO Log.logger.warning( "Editor is not a TextEditor. Should always be so for the Toogle Comment action" );
			return null;
		}
		
		
		TextEditor textEditor = null;
		
		//It seems like a multipage editor can have several text editors. We just pick the first one
		//since it does not seem to be a way to retrieve the active one.
		if( editorPart instanceof MultiPageEditorPart ){
		    MultiPageEditorPart mpe = (MultiPageEditorPart) editorPart;
	        IEditorPart[] editors = mpe.findEditors(mpe.getEditorInput());
	        for( IEditorPart editor : editors ) {
	            if( editor instanceof TextEditor ){
	                textEditor = (TextEditor) editor;
	                //TODO Log.logger.info("Found text editor for the MultiPageEditorPart");
	                break;
	            }
	        }
	        
	        if( null == textEditor){
	            //TODO Log.logger.warning("The MultiPageEditorPart did not have a TextEditor. Cannot toggle comments");
	            return null;
	        }
		} else {
		    textEditor = (TextEditor) editorPart;    
		}
		
		IDocument document = textEditor.getDocumentProvider().getDocument(textEditor.getEditorInput());
		ResourceBundle b = _resourceBundle; //TODO Messages.getResourceBundle();
		
		Action action;
		if( addComment( document, ts ) ){
			action = new TextOperationAction( b, "", textEditor, ITextOperationTarget.PREFIX );
		} else {
			action = new TextOperationAction( b, "", textEditor, ITextOperationTarget.STRIP_PREFIX );			
		}
		action.run();
		return null;

	}
	
	/**
	 * Function used to determine if comments should be turned on or off for the selected
	 * section.
	 * Comments should be turned off if all the lines in the selection starts with a comment character.
	 * Otherwise comments should be turned on/added to all the lines.
	 * @return Returns true if comments should be turned on for the selected section. False otherwise.
	 */
	public static boolean addComment ( IDocument document, TextSelection selection ) {
		
		int startLine = selection.getStartLine();
		int endLine = selection.getEndLine();		

		try {
			for( int lineNum = startLine; lineNum <= endLine; lineNum++ ) {
				int offsetAtStart = document.getLineOffset(lineNum);
				int lineLength = document.getLineLength( lineNum );
				
				String lineText = document.get( offsetAtStart, lineLength );
				
				//TODO Log.logger.info( "Checking line " + lineNum + " with text " + lineText );
				
				//one of the lines did not start with a comment. Must add comments
				if( !startsWithComment( lineText ) ){
					return true;
				}		
			}
		} catch ( BadLocationException ex ){
			//TODO Log.logger.warning("BadLocationException when trying to toggle comments\n" + ex );
			return false;
		}
 
		return false;
	}
	
	/**
	 * Checks whether a line starts with a comment character or not.
	 * @param line The string that should be checked. Should be the text string of a single line
	 * without line delimiter characters.
	 * @return true if the string starts with a comment character, false otherwise.
	 */
	public static boolean startsWithComment( String line ){
		
		Pattern commentPattern = Pattern.compile( "^\\s*//.*", Pattern.DOTALL );
		
		Matcher m = commentPattern.matcher(line);
		boolean match = m.matches();
		
		//TODO Log.logger.info( "Match result was " + match + " for '" + line + "'" );
		return match;

	}

	private static ResourceBundle _resourceBundle = new ResourceBundle(){
    @Override
    public boolean containsKey(String key) {
        return true;
    }
    @Override
    public Enumeration<String> getKeys() {
        return new Vector<String>().elements();
        //return null;
    }

    @Override
    protected Object handleGetObject(String key) {
        return "xx_" + key;
    }
};//ResourceBundle.getBundle(RESOURCE_BUNDLE);
	

}
