package scala.tools.eclipse.editor.text;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
//import org.eclipse.jface.text.TextUtilities;
public class TextHelper {
    /**
     * Returns the content of the given line without the leading whitespace.
     *
     * @param document - the document being parsed
     * @param line - the line being searched
     * @return the content of the given line without the leading whitespace
     * @throws BadLocationException in case <code>line</code> is invalid in the document
     */
     protected static String getIndentOfLine(IDocument document, int line) throws BadLocationException {
        if (line > -1) {
            int start= document.getLineOffset(line);
            int end= start + document.getLineLength(line) - 1;
            int whiteend= findEndOfWhiteSpace(document, start, end);
            return document.get(start, whiteend - start);
        }
        return ""; //$NON-NLS-1$
    }

    protected static int findEndOfWhiteSpace(IDocument document, int offset, int end)
            throws BadLocationException {
        int current = offset;
        while (current < end) {
            char c = document.getChar(current);
            if (current>offset && c == '*' && document.getChar(current-1) == ' ') return current-1;
            if (c != ' ' && c != '\t') return current;
            current++;
        }
        return end;
    }

    private static int getNextLineStart(IDocument document, int offset) {
        try {
            int line = document.getLineOfOffset(offset);
            return document.getLineOffset(line + 1);
        } catch (BadLocationException e) {
        }
        return document.getLength();
    }
    /**
     * Returns the end position of a comment starting at the given <code>position</code>.
     *
     * @param document - the document being parsed
     * @param offset - the start position for the search
     * @param end - the end position for the search
     * @return the end position of a comment starting at the given <code>position</code>
     * @throws BadLocationException in case <code>position</code> and <code>end</code> are invalid in the document
     */
    protected static int getCommentEnd(IDocument document, int offset, int end) throws BadLocationException {
        int currentPosition = offset;
        while (currentPosition < end) {
            char curr= document.getChar(currentPosition);
            currentPosition++;
            if (curr == '*') {
                if (currentPosition < end && document.getChar(currentPosition) == '/') {
                    return currentPosition + 1;
                }
            }
        }
        return end;
    }

    protected static int getLineEnd(IDocument document, int offset) throws BadLocationException {
        int line = document.getLineOfOffset(offset);
        return document.getLineOffset(line) + document.getLineLength(line);
    }

    /**
     * Returns the position of the <code>character</code> in the <code>document</code> after <code>position</code>.
     *
     * @param document - the document being parsed
     * @param position - the position to start searching from
     * @param end - the end of the document
     * @param character - the character you are trying to match
     * @return the next location of <code>character</code>
     * @throws BadLocationException in case <code>position</code> is invalid in the document
     */
     private static int getStringEnd(IDocument document, int position, int end, char character) throws BadLocationException {
        int currentPosition = position;
        while (currentPosition < end) {
            char currentCharacter= document.getChar(currentPosition);
            currentPosition++;
            if (currentCharacter == '\\') {
                // ignore escaped characters
                currentPosition++;
            } else if (currentCharacter == character) {
                return currentPosition;
            }
        }
        return end;
    }

    protected static int getOpenPairCount(IDocument document, char startChar) throws BadLocationException {
        int count = 0;
        int i = 0;
        int end = document.getLength();
        char endChar = getMathingPair(startChar);
        boolean isString = endChar == startChar;
        while (i < end) {
            char c = document.getChar(i++);
            if (c == startChar) ++count;
            else if (c == endChar) --count;
            else if (isString && (c == '"' || c == '\'')) i = getStringEnd(document, i, end, c);
            else if (c == '/') {
                char next = document.getChar(i);
                if (next == '*') i = getCommentEnd(document, i + 1, end);
                else if (next == '/') i = getNextLineStart(document, i);
            }
        }
        if (isString) count %= 2;
        return count;
    }

    /**
     * Returns the bracket value of a section of text. Closing brackets have a value of -1 and
     * open brackets have a value of 1.
     *
     * @param document - the document being parsed
     * @param start - the start position for the search
     * @param end - the end position for the search
     * @param ignoreClosePair - whether or not to ignore closing brackets in the count
     * @return the bracket value of a section of text
     * @throws BadLocationException in case the positions are invalid in the document
     */
    protected static int getOpenPairCount(IDocument document, int start, int end, boolean ignoreClosePair) throws BadLocationException {
        int begin = start;
        int bracketcount= 0;
        while (begin < end) {
            char curr= document.getChar(begin);
            begin++;
            switch (curr) {
                case '/' :
                    if (begin < end) {
                        char next= document.getChar(begin);
                        if (next == '*') {
                            // a comment starts, advance to the comment end
                            begin= TextHelper.getCommentEnd(document, begin + 1, end);
                        } else if (next == '/') {
                            // '//'-comment: nothing to do anymore on this line
                            begin= end;
                        }
                    }
                    break;
                case '*' :
                    if (begin < end) {
                        char next= document.getChar(begin);
                        if (next == '/') {
                            // we have been in a comment: forget what we read before
                            bracketcount= 0;
                            begin++;
                        }
                    }
                    break;
                case '[' :
                case '(' :
                case '{' :
                    bracketcount++;
                    ignoreClosePair= false;
                    break;
                case ']' :
                case ')' :
                case '}' :
                    if (!ignoreClosePair) {
                        bracketcount--;
                    }
                    break;
                case '"' :
                case '\'' :
                    begin= getStringEnd(document, begin, end, curr);
                    break;
                default :
                    }
        }
        return bracketcount;
    }

    protected static char getMathingPair(char c) {
        if (c == '{') return '}';
        if (c == '(') return ')';
        if (c == '[') return ']';
        if (c == '}') return '{';
        if (c == ')') return '(';
        if (c == ']') return '[';
        if (c == '"') return '"';
        if (c == '\'') return '\'';
        return c;
    }

    protected static boolean isOpenBrace(char c) {
		return (c == '{' || c == '(' || c == '[');
	}

	protected static boolean isCloseBrace(String text) {
		return (text != null) && (text.length() == 1) && isCloseBrace(text.charAt(0));
	}

	protected static boolean isCloseBrace(char c) {
		return (c == '}' || c == ')' || c == ']');
	}

    protected static boolean isOpenString(char c) {
		return (c == '"' || c == '\'');
	}

    protected static boolean isOpenPair(char c) {
		return isOpenBrace(c) || isOpenString(c);
	}

    protected static int[] getOpenCommentCount(IDocument doc, int offset)
        throws BadLocationException {
            int count = 0;
            int i = 0;
            int end = doc.getLength();
            int isInsideComment = -1;
            int isInsideUnclosed = -1;
            int last = -1;
            while (i < end) {
                if (i == offset && count>0)
                    isInsideComment = last;
                char c = doc.getChar(i++);
                if (c == '/') {
                    char next = doc.getChar(i);
                    if (next == '*') {
                        if (last >= 0 && isInsideComment == last)
                            isInsideUnclosed = 1;
                        last = i;
                        count++;
                    }
                    // skip single line comments
                    if (next == '/') i = getNextLineStart(doc, i);;
                }
                else if (c == '*') {
                    char next = doc.getChar(i);
                    if (next == '/') count--;
                }
                else if (c == '"' || c == '\'') i = getStringEnd(doc, i, end, c);
            }
            if (count > 0 && isInsideComment == last)
                isInsideUnclosed = 1;
            return new int[] {count, isInsideComment, isInsideUnclosed };
    }
    
    /**
     * Returns the line number of the next bracket after end.
     *
     * @param document - the document being parsed
     * @param line - the line to start searching back from
     * @param end - the end position to search back from
     * @param closingPairIncrease - the number of brackets to skip
     * @return the line number of the next matching bracket after end
     * @throws BadLocationException in case the line numbers are invalid in the document
     */
     protected static int findMatchingOpenPair(IDocument document, int line, int end, int closingPairIncrease) throws BadLocationException {

        int start= document.getLineOffset(line);
        int brackcount= TextHelper.getOpenPairCount(document, start, end, false) - closingPairIncrease;

        // sum up the brackets counts of each line (closing brackets count negative,
        // opening positive) until we find a line the brings the count to zero
        while (brackcount < 0) {
            line--;
            if (line < 0) {
                return -1;
            }
            start= document.getLineOffset(line);
            end= start + document.getLineLength(line) - 1;
            brackcount += TextHelper.getOpenPairCount(document, start, end, false);
        }
        return line;
    }
}
