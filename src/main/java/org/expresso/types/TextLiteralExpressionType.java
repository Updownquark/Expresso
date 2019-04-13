package org.expresso.types;

import java.io.IOException;

import org.expresso.stream.CharSequenceStream;

/**
 * A {@link LiteralExpressionType} for a {@link CharSequenceStream}
 *
 * @param <S> The sub-type of the text stream
 */
public class TextLiteralExpressionType<S extends CharSequenceStream> extends LiteralExpressionType<char[], S> {
	private final String theText;

	/**
	 * @param id The cache ID for this expression type
	 * @param text The text literal to expect in the stream
	 */
	public TextLiteralExpressionType(int id, String text) {
		super(id, text.toCharArray());
		theText = text;
	}

	@Override
	protected int getLength() {
		return theText.length();
	}

	@Override
	protected int getMatchUntil(S stream) throws IOException {
		for (int i = 0; i < theText.length(); i++) {
			if (!stream.hasMoreData(i + 1) || theText.charAt(i) != stream.charAt(i))
				return i;
		}
		return theText.length();
	}

	@Override
	public int hashCode() {
		return theText.hashCode();
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		else if (!(o instanceof TextLiteralExpressionType))
			return false;
		return theText.equals(((TextLiteralExpressionType<?>) o).theText);
	}

	@Override
	public String toString() {
		return "L:" + theText;
	}
}
