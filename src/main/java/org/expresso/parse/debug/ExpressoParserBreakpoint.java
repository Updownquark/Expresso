package org.expresso.parse.debug;

import java.util.regex.Pattern;

public class ExpressoParserBreakpoint {
	private Pattern thePreCursorText;

	private Pattern thePostCursorText;

	private String theOpName;

	private boolean isEnabled;

	public ExpressoParserBreakpoint() {
		setEnabled(true);
	}

	public Pattern getPreCursorText() {
		return thePreCursorText;
	}

	public void setPreCursorText(Pattern text) {
		thePreCursorText = text;
	}

	public Pattern getPostCursorText() {
		return thePostCursorText;
	}

	public void setPostCursorText(Pattern text) {
		thePostCursorText = text;
	}

	public String getOpName() {
		return theOpName;
	}

	public void setOpName(String opName) {
		theOpName = opName;
	}

	public boolean isEnabled() {
		return isEnabled;
	}

	public void setEnabled(boolean enabled) {
		isEnabled = enabled;
	}

	@Override
	public String toString() {
		StringBuilder ret = new StringBuilder();
		ret.append('[');
		if(isEnabled)
			ret.append('X');
		ret.append(']');
		if(thePreCursorText != null)
			ret.append('(').append(thePreCursorText).append(')');
		ret.append('.');
		if(thePostCursorText != null)
			ret.append('(').append(thePostCursorText).append(')');
		if(theOpName != null)
			ret.append(" for ").append(theOpName);
		return ret.toString();
	}
}
