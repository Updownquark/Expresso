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
		Assert.assertEquals("Incomplete match", expression.length(), result.length());
		return result;
	}

	/** Tests parsing a simple variable name (vbl) */
	@Test(timeout = 1000)
	public void testVariable() {
		Expression<CharSequenceStream> result = parse("vbl", "result-producer", true);
		result = result.unwrap();
		Assert.assertEquals("identifier", ((ConfiguredExpressionType<?>) result.getType()).getName());
		Assert.assertEquals("vbl", result.getField("name").getFirst().printContent());
	}

	/** Test parsing a field invocation (vbl.field) */
	@Test(timeout = 1000)
	public void testField() {
		Expression<CharSequenceStream> result = parse("vbl.field", "result-producer", true).unwrap();
		Assert.assertEquals("field-ref", ((ConfiguredExpressionType<?>) result.getType()).getName());
		Assert.assertEquals("vbl", result.getField("target").getFirst().printContent());
		Assert.assertEquals("field", result.getField("name").getFirst().printContent());
		Assert.assertEquals(0, result.getField("method").size());
	}

	/** Tests parsing a nested field invocation (vbl.field1.field2) */
	@Test(timeout = 1000)
	public void testDoubleField() {
		Expression<CharSequenceStream> result = parse("vbl.field1.field2", "result-producer", true).unwrap();
		Assert.assertEquals("field-ref", ((ConfiguredExpressionType<?>) result.getType()).getName());
		Expression<CharSequenceStream> inner = result.getField("target").getFirst().getWrapped().unwrap();
		String targetType = ((ConfiguredExpressionType<?>) inner.getChildren().get(0).getType()).getName();
		switch (targetType) {
		case "field-ref":
			Assert.assertEquals("vbl", inner.getField("target", "value").getFirst().printContent());
			Assert.assertEquals("field1", inner.getField("name").getFirst().printContent());
			break;
		case "basic-type":
			Deque<ExpressionField<CharSequenceStream>> typeNames = inner.getField("name");
			Assert.assertEquals(2, typeNames.size());
			Assert.assertEquals("vbl", typeNames.getFirst().printContent());
			Assert.assertEquals("field1", typeNames.getLast().printContent());
			break;
		default:
			Assert.assertTrue("Unrecognized target type: " + targetType, false);
			break;
		}
		Assert.assertEquals("field2", result.getField("name").getFirst().printContent());
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
	@Test(timeout = 1000)
	public void testConstructor() {
		Expression<CharSequenceStream> result = parse("new Integer(5, b)", "result-producer", true).unwrap();
		Assert.assertEquals("constructor", ((ConfiguredExpressionType<?>) result.getType()).getName());
		Expression<CharSequenceStream> type = result.getField("type").getFirst().getWrapped().unwrap();
		Assert.assertEquals("Integer", type.printContent());
		Deque<ExpressionField<CharSequenceStream>> args = result.getField("arguments", "argument");
		Assert.assertEquals(2, args.size());
		Assert.assertEquals("number", ((ConfiguredExpressionType<?>) args.getFirst().getWrapped().unwrap().getType()).getName());
		Assert.assertEquals("identifier", ((ConfiguredExpressionType<?>) args.getLast().getWrapped().unwrap().getType()).getName());
	}

	/** Test parsing a constructor with generic type arguments (new java.util.ArrayList&lt;Integer>(5)) */
	@Test(timeout = 1000)
	public void testGenericConstructor() {
		Expression<CharSequenceStream> result = parse("new java.util.ArrayList<java.lang.Integer>(5)", "result-producer", true).unwrap();
		Assert.assertEquals("constructor", ((ConfiguredExpressionType<?>) result.getType()).getName());
		Expression<CharSequenceStream> type = result.getField("type").getFirst().getWrapped().unwrap();
		Assert.assertEquals("java.util.ArrayList", type.getField("base").getFirst().printContent());
		Assert.assertEquals("java.lang.Integer", type.getField("parameters", "parameter").getFirst().printContent());
		Deque<ExpressionField<CharSequenceStream>> args = result.getField("arguments", "argument");
		Assert.assertEquals(1, args.size());
		Assert.assertEquals("number", ((ConfiguredExpressionType<?>) args.getFirst().getWrapped().unwrap().getType()).getName());
	}

	/** Tests a multi-argument static method invocation (list.addAll(java.util.Arrays.asList(1, 2, 3, 4, 5))) */
	@Test(timeout = 1000)
	public void testMethod() {
		Expression<CharSequenceStream> result = parse("java.util.Arrays.asList(1, 2, 3, 4, 5)", "result-producer", true).unwrap();
	}

	/** Tests a nested method invocation (list.addAll(java.util.Arrays.asList(1, 2, 3, 4, 5))) */
	@Test(timeout = 1000)
	public void testDoubleMethod() {
		Expression<CharSequenceStream> result = parse("list.addAll(java.util.Arrays.asList(1, 2, 3, 4, 5))", "result-producer", false)
			.unwrap();
	}

	/**
	 * Tests a more complex block of statements:
	 * 
	 * <pre>
	 * {
	 * 	java.util.ArrayList<Integer> list;
	 * 	list = new ArrayList<>(5);
	 * 	list.addAll(java.util.Arrays.asList(1, 2, 3, 4, 5));
	 * }
	 * </pre>
	 */
	@Test(timeout = 1000)
	public void testBlock() {
		String expression = "{\n";
		expression += "java.util.ArrayList<Integer> list;\n";
		expression += "list = new ArrayList<>(5);\n";
		expression += "list.addAll(java.util.Arrays.asList(1, 2, 3, 4, 5));\n";
		expression += "}";
		Expression<CharSequenceStream> result = parse(expression, "body-content", true).unwrap();

	}
}
