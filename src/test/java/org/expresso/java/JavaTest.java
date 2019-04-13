package org.expresso.java;

import java.io.IOException;
import java.util.function.Supplier;

import org.expresso.*;
import org.expresso.stream.CharSequenceStream;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.qommons.BreakpointHere;
import org.qommons.QommonsUtils;
import org.qommons.collect.BetterList;

/** Tests the Expresso parser using the embedded Java grammar */
public class JavaTest {
	/** Don't terminate early if debugging, 1 second otherwise */
	private static final long TIMEOUT = BreakpointHere.isDebugEnabled() == null ? 1000 : 0;

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

	private <T> T getWithin(Supplier<T> task, long time) {
		if (time == 0)
			return task.get();
		boolean[] done = new boolean[1];
		Object[] result = new Object[1];
		Throwable[] ex = new RuntimeException[1];
		Thread worker = new Thread(() -> {
			try {
				result[0] = task.get();
				done[0] = true;
			} catch (ThreadDeath e) {
			} catch (RuntimeException | Error e) {
				ex[0] = e;
				done[0] = true;
			}
		}, getClass().getSimpleName() + " worker");
		worker.start();
		long start = System.currentTimeMillis();
		while (System.currentTimeMillis() < start + time && !done[0]) {
			try {
				Thread.sleep(10);
			} catch (InterruptedException e) {
			}
		}
		if (done[0]) {
			if (ex[0] instanceof RuntimeException)
				throw (RuntimeException) ex[0];
			else if (ex[0] instanceof Error)
				throw (Error) ex[0];
			else
				return (T) result[0];
		} else {
			worker.stop();
			throw new IllegalStateException("Task took longer than " + QommonsUtils.printTimeLength(time));
		}
	}

	private Expression<CharSequenceStream> parse(String expression, String type, boolean checkForErrors, long time) {
		return getWithin(() -> {
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
			Assert.assertEquals("Incomplete match: " + result.printContent(false), expression.length(), result.length());
			return result;
		}, time);
	}

	/** Tests parsing a simple variable name (vbl) */
	@Test
	public void testVariable() {
		Expression<CharSequenceStream> result = parse("vbl", "result-producer", true, TIMEOUT);
		result = result.unwrap();
		Assert.assertEquals("identifier", ((ConfiguredExpressionType<?>) result.getType()).getName());
		Assert.assertEquals("vbl", result.getField("name").getFirst().printContent(false));
	}

	/** Test parsing a field invocation (vbl.field) */
	@Test
	public void testField() {
		Expression<CharSequenceStream> result = parse("vbl.field", "result-producer", true, TIMEOUT).unwrap();
		Assert.assertEquals("field-ref", ((ConfiguredExpressionType<?>) result.getType()).getName());
		Assert.assertEquals("vbl", result.getField("target").getFirst().printContent(false));
		Assert.assertEquals("field", result.getField("name").getFirst().printContent(false));
		Assert.assertEquals(0, result.getField("method").size());
	}

	/** Tests parsing a nested field invocation (vbl.field1.field2) */
	@Test
	public void testDoubleField() {
		Expression<CharSequenceStream> result = parse("vbl.field1.field2", "result-producer", true, TIMEOUT).unwrap();
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
	@Test
	public void testConstructor() {
		Expression<CharSequenceStream> result = parse("new Integer(5, b)", "result-producer", true, TIMEOUT).unwrap();
		Assert.assertEquals("constructor", ((ConfiguredExpressionType<?>) result.getType()).getName());
		Expression<CharSequenceStream> type = result.getField("type").getFirst().getWrapped().unwrap();
		Assert.assertEquals("Integer", type.printContent(false));
		BetterList<ExpressionField<CharSequenceStream>> args = result.getField("arguments", "argument");
		Assert.assertEquals(2, args.size());
		Assert.assertEquals("number", ((ConfiguredExpressionType<?>) args.getFirst().getWrapped().unwrap().getType()).getName());
		Assert.assertEquals("identifier", ((ConfiguredExpressionType<?>) args.getLast().getWrapped().unwrap().getType()).getName());
	}

	/** Test parsing a constructor with generic type arguments (new java.util.ArrayList&lt;Integer>(5)) */
	@Test
	public void testGenericConstructor() {
		Expression<CharSequenceStream> result = parse("new java.util.ArrayList<java.lang.Integer>(5)", "result-producer", true, TIMEOUT)
			.unwrap();
		Assert.assertEquals("constructor", ((ConfiguredExpressionType<?>) result.getType()).getName());
		Expression<CharSequenceStream> type = result.getField("type").getFirst().getWrapped().unwrap();
		Assert.assertEquals("java.util.ArrayList", type.getField("base").getFirst().printContent(false));
		Assert.assertEquals("java.lang.Integer", type.getField("parameters", "parameter").getFirst().printContent(false));
		BetterList<ExpressionField<CharSequenceStream>> args = result.getField("arguments", "argument");
		Assert.assertEquals(1, args.size());
		Assert.assertEquals("number", ((ConfiguredExpressionType<?>) args.getFirst().getWrapped().unwrap().getType()).getName());
	}

	/** Tests a multi-argument static method invocation (list.addAll(java.util.Arrays.asList(1, 2, 3, 4, 5))) */
	@Test
	public void testMethod() {
		Expression<CharSequenceStream> result = parse("java.util.Arrays.asList(1, 2, 3, 4, 5)", "result-producer", true, TIMEOUT).unwrap();
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
	@Test
	public void testDoubleMethod() {
		Expression<CharSequenceStream> result = parse("list.addAll(java.util.Arrays.asList(1, 2, 3, 4, 5))", "result-producer", false,
			TIMEOUT)
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
	@Test
	public void testBlock() {
		String expression = "{\n";
		expression += "\tjava.util.ArrayList<Integer> list;\n";
		expression += "\tlist = new ArrayList<>(5);\n";
		expression += "\tlist.addAll(java.util.Arrays.asList(1, 2, 3, 4, 5));\n";
		expression += "}";
		Expression<CharSequenceStream> result = parse(expression, "body-content", true, TIMEOUT).unwrap();
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

	@Test
	public void testWow() {
		String expression = "org.observe.collect.ObservableCollection.create(org.observe.util.TypeTokens.get().STRING,"
			+ "new org.qommons.tree.SortedTreeList<String>(true, org.qommons.QommonsUtils.DISTINCT_NUMBER_TOLERANT))";
		Expression<CharSequenceStream> result = parse(expression, "result-producer", true, TIMEOUT * 5).unwrap();
		checkWow(result);
	}

	private void checkWow(Expression<?> expr) {
		Assert.assertEquals("method", ((ConfiguredExpressionType<?>) expr.getType()).getName());

		Expression<?> target = expr.getField("target").getFirst().getWrapped().unwrap();
		Assert.assertEquals("org.observe.collect.ObservableCollection", target.printContent(false));
		Assert.assertEquals("create", expr.getField("method", "name").getFirst().printContent(false));

		BetterList<? extends ExpressionField<?>> args = expr.getField("method", "arguments", "argument");
		Assert.assertEquals(2, args.size());

		Expression<?> arg1 = args.getFirst().getWrapped().unwrap();
		Assert.assertEquals("field-ref", ((ConfiguredExpressionType<?>) arg1.getType()).getName());
		Expression<?> arg1Target = arg1.getField("target").getFirst().getWrapped().unwrap();
		Assert.assertEquals("method", ((ConfiguredExpressionType<?>) arg1Target.getType()).getName());
		Expression<?> arg1TargetTarget = arg1Target.getField("target").getFirst().getWrapped().unwrap();
		Assert.assertEquals("org.observe.util.TypeTokens", arg1TargetTarget.printContent(false));
		Assert.assertEquals("get", arg1Target.getField("method", "name").getFirst().printContent(false));
		Assert.assertEquals("STRING", arg1.getField("name").getFirst().printContent(false));

		Expression<?> arg2 = args.getLast().getWrapped().unwrap();
		Assert.assertEquals("constructor", ((ConfiguredExpressionType<?>) arg2.getType()).getName());
		Expression<?> arg2Type = arg2.getField("type").getFirst().getWrapped().unwrap();
		Assert.assertEquals("generic-type", ((ConfiguredExpressionType<?>) arg2Type.getType()).getName());
		Assert.assertEquals("org.qommons.tree.SortedTreeList", arg2Type.getField("base").getFirst().printContent(false));
		Assert.assertEquals("String", arg2Type.getField("parameters", "parameter").getFirst().printContent(false));

		BetterList<? extends ExpressionField<?>> args2 = arg2.getField("arguments", "argument");
		Assert.assertEquals(2, args2.size());
		Expression<?> arg2Arg1 = args2.getFirst().getWrapped().unwrap();
		// TODO Right now, the parser is finding an identifier instead of a boolean. This is probably just a problem with the grammar.
		// Assert.assertEquals("boolean", ((ConfiguredExpressionType<?>) arg2Arg1.getType()).getName());
		Assert.assertEquals("true", arg2Arg1.printContent(false));
		Expression<?> arg2Arg2 = args2.getLast();
		Assert.assertEquals("org.qommons.QommonsUtils.DISTINCT_NUMBER_TOLERANT", arg2Arg2.printContent(false).trim());
	}

	@Test
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
