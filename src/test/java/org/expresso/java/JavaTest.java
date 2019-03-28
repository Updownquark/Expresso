package org.expresso.java;

import java.io.IOException;
import java.util.Deque;

import org.expresso.ConfiguredExpressionType;
import org.expresso.DefaultGrammarParser;
import org.expresso.Expression;
import org.expresso.ExpressionField;
import org.expresso.ExpressionType;
import org.expresso.ExpressoGrammar;
import org.expresso.ExpressoGrammarParser;
import org.expresso.stream.CharSequenceStream;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/** Tests the Expresso parser using the embedded Java grammar */
public class JavaTest {
	private ExpressoGrammar<CharSequenceStream> theParser;

	/**
	 * Builds the parser from the embedded Java grammar
	 * 
	 * @throws IOException If the grammar file cannot be read
	 */
	@Before
	public void setupParser() throws IOException {
		ExpressoGrammarParser<CharSequenceStream> grammarParser = ExpressoGrammarParser.defaultText();
		theParser = grammarParser.parseGrammar(DefaultGrammarParser.class.getResource("/org/expresso/grammars/Java8.xml"));
	}

	private Expression<CharSequenceStream> parse(String expression, String type, boolean checkForErrors) {
		ExpressionType<CharSequenceStream> component = theParser.getExpressionsByName().get(type);
		if (component == null)
			component = theParser.getExpressionClasses().get(type);
		if (component == null)
			throw new IllegalArgumentException("No such type or class: " + type);
		Expression<CharSequenceStream> result;
		try {
			result = theParser.parse(CharSequenceStream.from(expression), component, !checkForErrors);
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
		if (result.getErrorCount() > 0)
			Assert.assertEquals(result.getFirstError().getLocalErrorMessage(), 0, result.getErrorCount());
		Assert.assertEquals(expression.length(), result.length());
		return result;
	}

	/** Tests parsing a simple variable name (vbl) */
	@Test
	public void testVariable() {
		Expression<CharSequenceStream> result = parse("vbl", "result-producer", true);
		result = result.unwrap();
		Assert.assertEquals("identifier", ((ConfiguredExpressionType<?>) result.getType()).getName());
		Assert.assertEquals("vbl", result.getField("name").getFirst().toString());
	}

	/** Test parsing a field invocation (vbl.field) */
	@Test
	public void testField() {
		Expression<CharSequenceStream> result = parse("vbl.field", "result-producer", true).unwrap();
		Assert.assertEquals("member", ((ConfiguredExpressionType<?>) result.getType()).getName());
		Assert.assertEquals("vbl", result.getField("target").getFirst().toString());
		Assert.assertEquals("field", result.getField("name").getFirst().toString());
		Assert.assertEquals(0, result.getField("method").size());
	}

	/** Tests parsing a nested field invocation (vbl.field1.field2) */
	@Test
	public void testDoubleField() {
		Expression<CharSequenceStream> result = parse("vbl.field1.field2", "result-producer", true).unwrap();
		Assert.assertEquals("member", ((ConfiguredExpressionType<?>) result.getType()).getName());
		Expression<CharSequenceStream> inner = result.getField("target").getFirst().getWrapped().unwrap();
		Assert.assertEquals("member", ((ConfiguredExpressionType<?>) inner.getType()).getName());
		Assert.assertEquals("vbl", inner.getField("target").getFirst().toString());
		Assert.assertEquals("field1", inner.getField("name").getFirst().toString());
		Assert.assertEquals("field2", result.getField("name").getFirst().toString());
		Assert.assertEquals(0, result.getField("method").size());
	}

	/**
	 * <p>
	 * Tests parsing a constructor invocation (new Integer(5, b)).
	 * </p>
	 * <p>
	 * I may change the type later to make more sense, but, it doesn't matter here.
	 * </p>
	 */
	@Test
	public void testConstructor() {
		Expression<CharSequenceStream> result = parse("new Integer(5, b)", "result-producer", true).unwrap();
		Assert.assertEquals("constructor", ((ConfiguredExpressionType<?>) result.getType()).getName());
		Expression<CharSequenceStream> type = result.getField("type").getFirst().getWrapped().unwrap();
		Assert.assertEquals("Integer", type.toString());
		Deque<ExpressionField<CharSequenceStream>> args = result.getField("arguments", "argument");
		Assert.assertEquals(2, args.size());
		Assert.assertEquals("number", ((ConfiguredExpressionType<?>) args.getFirst().getWrapped().unwrap().getType()).getName());
		Assert.assertEquals("identifier", ((ConfiguredExpressionType<?>) args.getLast().getWrapped().unwrap().getType()).getName());
	}
}
