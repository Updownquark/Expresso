package org.expresso2.java;

import java.io.IOException;

import org.expresso.parse.impl.CharSequenceStream;
import org.expresso2.DefaultGrammarParser;
import org.expresso2.Expression;
import org.expresso2.ExpressionComponent;
import org.expresso2.ExpressionType;
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

	private Expression<CharSequenceStream> parse(String expression, String type, boolean checkForErrors) {
		ExpressionComponent<CharSequenceStream> component = theParser.getExpressionsByName().get(type);
		if (component == null)
			component = theParser.getExpressionClasses().get(type);
		if (component == null)
			throw new IllegalArgumentException("No such type or class: " + type);
		Expression<CharSequenceStream> result;
		try {
			result = theParser.parse(CharSequenceStream.from(expression), component, true);
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
		if (result.getErrorCount() > 0)
			Assert.assertEquals(result.getFirstError().getLocalErrorMessage(), 0, result.getErrorCount());
		return result;
	}

	@Test
	public void testVariable() {
		Expression<CharSequenceStream> result = parse("vbl", "result-producer", true).unwrap();
		Assert.assertEquals("identifier", ((ExpressionType<?>) result.getType()).getName());
		Assert.assertEquals("vbl", result.getField("name").getFirst().toString());
	}

	@Test
	public void testField() {
		Expression<CharSequenceStream> result = parse("vbl.field", "result-producer", true).unwrap();
		Assert.assertEquals("member", ((ExpressionType<?>) result.getType()).getName());
		Assert.assertEquals("vbl", result.getField("target").getFirst().toString());
		Assert.assertEquals("field", result.getField("name").getFirst().toString());
		Assert.assertEquals(0, result.getField("method").size());
	}
}
