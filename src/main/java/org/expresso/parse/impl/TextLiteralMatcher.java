package org.expresso.parse.impl;

import java.util.Set;

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
	}

	@Override
	protected int getLength() {
		return getValue().length;
	}

	@Override
	protected boolean startsWithValue(S stream) {
		int i = 0;
		for(; i < getValue().length && getValue()[i] == stream.charAt(i); i++) {}
		return i == getValue().length;
	}

	@Override
	public String toString() {
		return super.toString() + " value=\"" + new String(getValue()) + "\"";
	}
}
