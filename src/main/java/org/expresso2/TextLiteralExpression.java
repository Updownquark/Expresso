package org.expresso2;

import java.io.IOException;

import org.expresso.parse.impl.CharSequenceStream;

public class TextLiteralExpression<S extends CharSequenceStream> extends LiteralExpressionType<char[], S> {
	private final String theText;

	public TextLiteralExpression(int id, String text) {
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
	public String toString() {
		return "Literal(" + theText + ")";
	}
}
