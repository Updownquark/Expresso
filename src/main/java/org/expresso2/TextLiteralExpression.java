package org.expresso2;

import org.expresso.parse.impl.CharSequenceStream;

public class TextLiteralExpression<S extends CharSequenceStream> extends LiteralExpressionType<char[], S> {
	private final String theText;

	public TextLiteralExpression(String text) {
		super(text.toCharArray());
		theText = text;
	}

	@Override
	protected int getLength() {
		return theText.length();
	}

	@Override
	protected boolean startsWithValue(S stream) {
		for (int i = 0; i < theText.length(); i++) {
			if (theText.charAt(i) != stream.charAt(i))
				return false;
		}
		return true;
	}
}
