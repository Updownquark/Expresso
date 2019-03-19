package org.expresso2.java;

import java.io.IOException;

import org.expresso.parse.impl.CharSequenceStream;
import org.expresso2.DefaultGrammarParser;
import org.expresso2.Expression;
import org.expresso2.ExpressionComponent;
import org.expresso2.ExpressoGrammar;
import org.expresso2.GrammarParser;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class JavaTest {
	private ExpressoGrammar<CharSequenceStream> theParser;

	@Before
	public void setupParser() throws IOException {
		GrammarParser<CharSequenceStream> grammarParser = DefaultGrammarParser.forText();
		theParser = grammarParser.parseGrammar(DefaultGrammarParser.class.getResource("/org/expresso/java/Grammar.xml"));
	}

	private Expression<CharSequenceStream> parse(String expression, String type) {
		ExpressionComponent<CharSequenceStream> component = theParser.getExpressionsByName().get(type);
		if (component == null)
			component = theParser.getExpressionClasses().get(type);
		if (component == null)
			throw new IllegalArgumentException("No such type or class: " + type);
		try {
			return theParser.parse(CharSequenceStream.from(expression), component, true);
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	@Test
	public void testVariable() {
		Expression<CharSequenceStream> result = parse("vbl", "result-producer");
		if (result.getErrorCount() > 0)
			Assert.assertEquals(result.getFirstError().getLocalErrorMessage(), 0, result.getErrorCount());
		Assert.assertEquals("vbl", result.toString());
	}
}
