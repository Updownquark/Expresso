package org.expresso.java;

import java.io.IOException;

import org.expresso.stream.CharSequenceStream;
import org.expresso3.ConfiguredExpressionType;
import org.expresso3.DefaultGrammarParser;
import org.expresso3.Expression;
import org.expresso3.ExpressionField;
import org.expresso3.ExpressionType;
import org.expresso3.ExpressoGrammar;
import org.expresso3.ExpressoGrammarParser;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.qommons.collect.BetterList;

/** Tests the Expresso parser using the embedded Java grammar */
public class JavaTest {
	// private static final long TIMEOUT = 1000; // 1s
	private static final long TIMEOUT = 0; // DEBUG

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
			result = theParser.parse(CharSequenceStream.from(expression), component, checkForErrors ? 0 : -5);
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
		if (checkForErrors) {
			Assert.assertTrue("No result!", result != null);
			if (result.getErrorCount() > 0)
				Assert.assertEquals(result.getFirstError().getLocalErrorMessage(), 0, result.getErrorCount());
		}
		Assert.assertEquals("Incomplete match", expression.length(), result.length());
		return result;
	}

	/** Tests parsing a simple variable name (vbl) */
	@Test(timeout = TIMEOUT)
	public void testVariable() {
		Expression<CharSequenceStream> result = parse("vbl", "result-producer", true);
		result = result.unwrap();
		Assert.assertEquals("identifier", ((ConfiguredExpressionType<?>) result.getType()).getName());
		Assert.assertEquals("vbl", result.getField("name").getFirst().printContent(false));
	}

	/** Test parsing a field invocation (vbl.field) */
	@Test(timeout = TIMEOUT)
	public void testField() {
		Expression<CharSequenceStream> result = parse("vbl.field", "result-producer", true).unwrap();
		Assert.assertEquals("field-ref", ((ConfiguredExpressionType<?>) result.getType()).getName());
		Assert.assertEquals("vbl", result.getField("target").getFirst().printContent(false));
		Assert.assertEquals("field", result.getField("name").getFirst().printContent(false));
		Assert.assertEquals(0, result.getField("method").size());
	}

	/** Tests parsing a nested field invocation (vbl.field1.field2) */
	@Test(timeout = TIMEOUT)
	public void testDoubleField() {
		Expression<CharSequenceStream> result = parse("vbl.field1.field2", "result-producer", true).unwrap();
		Assert.assertEquals("field-ref", ((ConfiguredExpressionType<?>) result.getType()).getName());
		Expression<CharSequenceStream> inner = result.getField("target").getFirst().getWrapped().unwrap();
		// The target could be parsed validly as a field or a type, so checking that here would be more trouble than it's worth
		Assert.assertEquals("vbl.field1", inner.printContent(false));
		Assert.assertEquals("field2", result.getField("name").getFirst().printContent(false));
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
	@Test(timeout = TIMEOUT)
	public void testConstructor() {
		Expression<CharSequenceStream> result = parse("new Integer(5, b)", "result-producer", true).unwrap();
		Assert.assertEquals("constructor", ((ConfiguredExpressionType<?>) result.getType()).getName());
		Expression<CharSequenceStream> type = result.getField("type").getFirst().getWrapped().unwrap();
		Assert.assertEquals("Integer", type.printContent(false));
		BetterList<ExpressionField<CharSequenceStream>> args = result.getField("arguments", "argument");
		Assert.assertEquals(2, args.size());
		Assert.assertEquals("number", ((ConfiguredExpressionType<?>) args.getFirst().getWrapped().unwrap().getType()).getName());
		Assert.assertEquals("identifier", ((ConfiguredExpressionType<?>) args.getLast().getWrapped().unwrap().getType()).getName());
	}

	/** Test parsing a constructor with generic type arguments (new java.util.ArrayList&lt;Integer>(5)) */
	@Test(timeout = TIMEOUT)
	public void testGenericConstructor() {
		Expression<CharSequenceStream> result = parse("new java.util.ArrayList<java.lang.Integer>(5)", "result-producer", true).unwrap();
		Assert.assertEquals("constructor", ((ConfiguredExpressionType<?>) result.getType()).getName());
		Expression<CharSequenceStream> type = result.getField("type").getFirst().getWrapped().unwrap();
		Assert.assertEquals("java.util.ArrayList", type.getField("base").getFirst().printContent(false));
		Assert.assertEquals("java.lang.Integer", type.getField("parameters", "parameter").getFirst().printContent(false));
		BetterList<ExpressionField<CharSequenceStream>> args = result.getField("arguments", "argument");
		Assert.assertEquals(1, args.size());
		Assert.assertEquals("number", ((ConfiguredExpressionType<?>) args.getFirst().getWrapped().unwrap().getType()).getName());
	}

	/** Tests a multi-argument static method invocation (list.addAll(java.util.Arrays.asList(1, 2, 3, 4, 5))) */
	@Test(timeout = TIMEOUT)
	public void testMethod() {
		Expression<CharSequenceStream> result = parse("java.util.Arrays.asList(1, 2, 3, 4, 5)", "result-producer", true).unwrap();
		checkArraysAsList(result);
	}

	private static void checkArraysAsList(Expression<?> expr) {
		Assert.assertEquals("method", ((ConfiguredExpressionType<?>) expr.getType()).getName());
		Expression<?> target = expr.getField("target").getFirst().getWrapped().unwrap();
		// The target could be parsed validly as a field or a type, so checking that here would be more trouble than it's worth
		Assert.assertEquals("java.util.Arrays", target.printContent(false));
		Assert.assertEquals("asList", expr.getField("method", "name").getFirst().printContent(false));
		BetterList<? extends ExpressionField<?>> args = expr.getField("method", "arguments", "argument");
		Assert.assertEquals(5, args.size());
		int i = 1;
		for (ExpressionField<?> arg : args) {
			Assert.assertEquals(String.valueOf(i++), arg.printContent(false).trim());
		}
	}

	/** Tests a nested method invocation (list.addAll(java.util.Arrays.asList(1, 2, 3, 4, 5))) */
	@Test(timeout = TIMEOUT)
	public void testDoubleMethod() {
		Expression<CharSequenceStream> result = parse("list.addAll(java.util.Arrays.asList(1, 2, 3, 4, 5))", "result-producer", false)
			.unwrap();
		checkDoubleMethod(result);
	}

	private static void checkDoubleMethod(Expression<?> expr) {
		Assert.assertEquals("method", ((ConfiguredExpressionType<?>) expr.getType()).getName());
		Expression<?> target = expr.getField("target").getFirst().getWrapped().unwrap();
		// The target could be parsed validly as a field or a type, so checking that here would be more trouble than it's worth
		Assert.assertEquals("list", target.printContent(false).trim());
		Assert.assertEquals("addAll", expr.getField("method", "name").getFirst().printContent(false));
		Expression<?> arg = expr.getField("method", "arguments", "argument").getFirst().unwrap();
		checkArraysAsList(arg);
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
	@Test(timeout = TIMEOUT * 5)
	public void testBlock() {
		String expression = "{\n";
		expression += "\tjava.util.ArrayList<Integer> list;\n";
		expression += "\tlist = new ArrayList<>(5);\n";
		expression += "\tlist.addAll(java.util.Arrays.asList(1, 2, 3, 4, 5));\n";
		expression += "}";
		Expression<CharSequenceStream> result = parse(expression, "body-content", true).unwrap();
		checkBlock(result);
	}

	private static void checkBlock(Expression<?> expr) {
		Assert.assertEquals("block", ((ConfiguredExpressionType<?>) expr.getType()).getName());
		BetterList<? extends Expression<?>> statements = expr.getField("content", "content");
		Assert.assertEquals(3, statements.size());

		Expression<?> vblDecl = statements.getFirst().unwrap();
		Assert.assertEquals("variable-declaration", ((ConfiguredExpressionType<?>) vblDecl.getType()).getName());
		Assert.assertEquals("java.util.ArrayList", vblDecl.getField("type", "base").getFirst().printContent(false).trim());
		BetterList<? extends Expression<?>> typeParams = vblDecl.getField("type", "parameters", "parameter");
		Assert.assertEquals(1, typeParams.size());
		Assert.assertEquals("Integer", typeParams.getFirst().printContent(false));

		Expression<?> assign = statements.get(1).unwrap();
		Assert.assertEquals("assign", ((ConfiguredExpressionType<?>) assign.getType()).getName());
		Expression<?> assignVbl = assign.getField("variable").getFirst().unwrap();
		Assert.assertEquals("identifier", ((ConfiguredExpressionType<?>) assignVbl.getType()).getName());
		Assert.assertEquals("list", assignVbl.printContent(false).trim());
		Expression<?> assignVal = assign.getField("operand").getFirst().unwrap();
		Assert.assertEquals("constructor", ((ConfiguredExpressionType<?>) assignVal.getType()).getName());
		Assert.assertEquals("generic-type",
			((ConfiguredExpressionType<?>) assignVal.getField("type").getFirst().unwrap().getType()).getName());
		Assert.assertEquals("ArrayList", assignVal.getField("type", "base").getFirst().printContent(false).trim());
		Assert.assertEquals("5", assignVal.getField("arguments").getFirst().printContent(false));

		checkDoubleMethod(statements.getLast().unwrap());
	}

	@Test(timeout = TIMEOUT * 5)
	public void testWow() {
		String expression = "org.observe.collect.ObservableCollection.create(org.observe.util.TypeTokens.get().STRING,"
			+ "new org.qommons.tree.SortedTreeList<String>(true, org.qommons.QommonsUtils.DISTINCT_NUMBER_TOLERANT))";
		Expression<CharSequenceStream> result = parse(expression, "result-producer", true).unwrap();
	}

	// @Test
	public void testPerformance() {
		@SuppressWarnings("unused")
		int before = 0;
		for (int i = 0; i < 10; i++) {
			testBlock();
			System.out.println("Success " + (i + 1) + " of 10");
		}
		@SuppressWarnings("unused")
		int after = 1;
	}
}
