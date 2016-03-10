package org.expresso.parse.matchers;

import java.util.Map;
import java.util.Set;

import org.expresso.parse.impl.CharSequenceStream;

/**
 * Parses a literal string out of a stream
 *
 * @param <S> The type of character stream to parse
 */
public class TextLiteralMatcher<S extends CharSequenceStream> extends LiteralMatcher<char [], S> {
	/**
	 * @param name The name of this matcher
	 * @param tags The tags by which this matcher may be referenced in a parser
	 * @param value The value to parse
	 */
	public TextLiteralMatcher(String name, Set<String> tags, String value) {
		super(name, tags, value.toCharArray());
		if(value.length() == 0)
			throw new IllegalArgumentException("Text literals cannot search for empty strings");
	}

	@Override
	public String getTypeName() {
		return "literal";
	}

	@Override
	public Map<String, String> getAttributes() {
		return java.util.Collections.EMPTY_MAP;
	}

	@Override
	protected int getLength() {
		return getValue().length;
	}

	@Override
	protected boolean startsWithValue(S stream) {
		int i;
		for (i = 0; i < getValue().length; i++) {
			char streamChar = stream.charAt(i);
			if (getValue()[i] != streamChar)
				break;
		}
		return i == getValue().length;
	}

	@Override
	public String getValueString() {
		return new String(getValue());
	}

	@Override
	public String toString() {
		return super.toString() + " value=\"" + new String(getValue()) + "\"";
	}
}
