package org.expresso.parse.debug;

import java.util.regex.Pattern;

/** Represents a breakpoint set for debugging Expresso parsing */
public class ExpressoParserBreakpoint {
	private Pattern thePreCursorText;

	private Pattern thePostCursorText;

	private String theMatcherName;

	private boolean isEnabled;

	/** Creates the breakpoint */
	public ExpressoParserBreakpoint() {
		setEnabled(true);
	}

	/** @return The pattern for matching text prior to the breakpoint */
	public Pattern getPreCursorText() {
		return thePreCursorText;
	}

	/** @param text The pattern for matching text prior to the breakpoint */
	public void setPreCursorText(Pattern text) {
		thePreCursorText = text;
	}

	/** @return The pattern for matching text after the breakpoint */
	public Pattern getPostCursorText() {
		return thePostCursorText;
	}

	/** @param text The pattern for matching text after the breakpoint */
	public void setPostCursorText(Pattern text) {
		thePostCursorText = text;
	}

	/** @return The name of the matcher to break for */
	public String getMatcherName() {
		return theMatcherName;
	}

	/** @param name The name of the matcher to break for */
	public void setMatcherName(String name) {
		theMatcherName = name;
	}

	/** @return Whether this breakpoint is enabled */
	public boolean isEnabled() {
		return isEnabled;
	}

	/** @param enabled Whether this breakpoint should be enabled */
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
		if(theMatcherName != null)
			ret.append(" for ").append(theMatcherName);
		return ret.toString();
	}
}
