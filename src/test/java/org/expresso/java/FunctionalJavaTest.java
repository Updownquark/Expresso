package org.expresso.java;

import java.io.IOException;

import org.expresso.ExpressionTester;
import org.junit.Before;
import org.junit.Test;

/** Tests the functionality of the java parser */
public class FunctionalJavaTest extends JavaTest {
	@Before
	@Override
	public void setupParser() throws IOException {
		super.setupParser();
	}

	/** Tests parsing a simple variable name (vbl) */
	@Test
	public void testVariable() {
		testExpression("vbl", "result-producer", true, TIMEOUT, //
			new ExpressionTester("vbl").withType("qualified-name").withContent("vbl"));
	}

	/** Test parsing a field invocation (vbl.field) */
	@Test
	public void testField() {
		testExpression("vbl.field", "result-producer", true, TIMEOUT, //
			new ExpressionTester("fieldTest").withType("qualified-name", "field-ref")//
				.withField("target", target -> {
					target.withType("qualified-name").withContent("vbl");
				}).withField("name", name -> {
					name.withContent("field");
				}));
	}

	/** Tests parsing a nested field invocation (vbl.field1.field2) */
	@Test
	public void testDoubleField() {
		testExpression("vbl.field1.field2", "result-producer", true, TIMEOUT, //
			new ExpressionTester("doubleField").withType("qualified-name", "field-ref")//
				.withField("target",
					inner -> inner//
						.withFieldContent("target", "vbl")//
						.withFieldContent("name", "field1"))//
				.withFieldContent("name", "field2"));
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
		testExpression("new Integer(5, b)", "result-producer", true, TIMEOUT, //
			new ExpressionTester("constructor").withType("constructor")//
				.withFieldContent("type", "Integer")//
				.withFieldContents("arguments.argument", "5", "b"));
	}

	/** Test parsing a constructor with generic type arguments (new java.util.ArrayList&lt;Integer>(5)) */
	@Test
	public void testGenericConstructor() {
		testExpression("new java.util.ArrayList<java.lang.Integer>(5)", "result-producer", true, TIMEOUT, //
			new ExpressionTester("generic-constructor").withType("constructor")//
				.withField("type",
					type -> type.withType("generic-type")//
						.withFieldContent("base", "java.util.ArrayList")//
						.withFieldContent("parameters.parameter", "java.lang.Integer"))//
				.withField("arguments.argument", arg -> arg.withType("number").withContent("5")));
	}

	/** Tests a no-argument static method invocation (list.toArray()) */
	@Test
	public void testMethod() {
		testExpression("list.toArray()", //
			"result-producer", //
			true, TIMEOUT, new ExpressionTester("method").withType("method")//
				.withField("target", target -> target.withType("identifier", "qualified-name").withContent("list"))//
				.withFieldContent("method.name", "toArray"));
	}

	/** Tests a multi-argument static method invocation (list.addAll(java.util.Arrays.asList(1, 2, 3, 4, 5))) */
	@Test
	public void testMethod2() {
		testExpression("java.util.Arrays.asList(1, 2, 3, 4, 5)", "result-producer", true, TIMEOUT,
			checkArraysAsList(new ExpressionTester("method2")));
	}

	private static ExpressionTester checkArraysAsList(ExpressionTester tester) {
		return tester.withType("method")//
			.withField("target", target -> target.withType("qualified-name", "field-ref").withContent("java.util.Arrays"))//
			.withFieldContent("method.name", "asList")//
			.withFieldContents("method.arguments.argument", "1", "2", "3", "4", "5");
	}

	/** Tests a nested method invocation (list.addAll(java.util.Arrays.asList(1, 2, 3, 4, 5))) */
	@Test
	public void testDoubleMethod() {
		testExpression("list.addAll(java.util.Arrays.asList(1, 2, 3, 4, 5))", "result-producer", false, TIMEOUT, //
			checkDoubleMethod(new ExpressionTester("doubleMethod")));
	}

	private static ExpressionTester checkDoubleMethod(ExpressionTester tester) {
		return tester.withType("method")//
			.withFieldContent("target", "list")//
			.withFieldContent("method.name", "addAll")//
			.withField("method.arguments.argument", arg -> checkArraysAsList(arg));
	}

	/** Tests parsing a variable declaration(java.util.ArrayList<Integer> list;) */
	@Test
	public void testVarDeclaration() {
		String expression = "java.util.ArrayList<Integer> list;";
		testExpression(expression, "body-content", true, TIMEOUT, //
			new ExpressionTester("Var declaration").withType("variable-declaration")//
				.withField("type",
					type -> type.withType("generic-type")//
						.withFieldContent("base", "java.util.ArrayList")//
						.withFieldContent("parameters.parameter", "Integer"))
				.withFieldContent("name", "list"));
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
		testExpression(expression, "body-content", true, TIMEOUT, //
			new ExpressionTester("block").withType("block")//
				.withField("content.?content",
					s1 -> s1.withType("variable-declaration")//
						.withField("type",
							s1Type -> s1Type.withType("generic-type")//
								.withFieldContent("base", "java.util.ArrayList")//
								.withFieldContent("parameters.parameter", "Integer")), //
					s2 -> s2.withType("assign")//
						.withField("variable", "qualified-name", "list")//
						.withField("operand",
							s2Op -> s2Op.withType("constructor")//
								.withField("type",
									s2OpType -> s2OpType.withType("generic-type")//
										.withFieldContent("base", "ArrayList")//
										.withField("parameters.parameter") // No parameters
								)//
								.withField("arguments.argument", "number", "5")), //
					s3 -> checkDoubleMethod(s3)));
	}

	/**
	 * Tests A fairly complex expression
	 * (<code>org.observe.collect.ObservableCollection.create(org.observe.util.TypeTokens.get().STRING,new org.qommons.tree.SortedTreeList&lt;String&gt;(true, org.qommons.QommonsUtils.DISTINCT_NUMBER_TOLERANT))</code>)
	 */
	@Test
	public void testWow() {
		String expression = "org.observe.collect.ObservableCollection.create(org.observe.util.TypeTokens.get().STRING,"
			+ " new org.qommons.tree.SortedTreeList<String>(true, org.qommons.QommonsUtils.DISTINCT_NUMBER_TOLERANT))";
		testExpression(expression, "result-producer", true, TIMEOUT * 5, //
			new ExpressionTester("WOW").withType("method")//
				.withFieldContent("target", "org.observe.collect.ObservableCollection")//
				.withFieldContent("method.name", "create")//
				.withField("method.arguments.argument",
					arg1 -> arg1.withType("qualified-name", "field-ref")//
						.withField("target",
							arg1Target -> arg1Target.withType("method")//
								.withFieldContent("target", "org.observe.util.TypeTokens")//
								.withFieldContent("method.name", "get"))//
						.withFieldContent("name", "STRING"),
					arg2 -> arg2.withType("constructor")//
						.withField("type",
							arg2Type -> arg2Type.withType("generic-type")//
								.withFieldContent("base", "org.qommons.tree.SortedTreeList")//
								.withFieldContent("parameters.parameter", "String"))
						.withField("arguments.argument",
							arg2Arg1 -> arg2Arg1
								// TODO Right now, the parser is finding an identifier instead of a boolean.
								// This is probably just a problem with the grammar.
								// .withType("boolean")
								// arg2Arg1.withType("boolean");
								.withContent("true"),
							arg2Arg2 -> {
								arg2Arg2.withType("qualified-name")//
									.withFieldContent("target", "org.qommons.QommonsUtils")//
									.withFieldContent("name", "DISTINCT_NUMBER_TOLERANT");
							})));
	}

	/** Tests parsing a very simple expression (a+b) */
	@Test
	public void testSimpleOperations() {
		testExpression("a+b", "result-producer", true, TIMEOUT, //
			new ExpressionTester("add").withType("add").withField("left", left -> {
				left.withType("qualified-name").withContent("a");
			}).withField("right", right -> {
				right.withType("qualified-name").withContent("b");
			}));
	}

	/** Tests parsing a method declaration (with body) */
	@Test
	public void testMethodDeclaration() {
		testExpression(
			String.join("", "\tpublic static int add(int a, int b){\n", //
				"\t\treturn a+b;\n", //
				"\t}\n"),
			"class-content", true, TIMEOUT * 2, //
			new ExpressionTester("addMethod").withType("method-declaration")//
				.withFieldContents("qualifier", "public", "static")//
				.withField("name", name -> name.withContent("add"))//
				.withField("body.content",
					body -> body.withType("return")//
						.withField("value",
							returnValue -> returnValue.withType("add")//
								.withField("left", "qualified-name", "a")//
								.withField("right", "qualified-name", "b"))));
	}

	/** Tests parsing strings, including escaped quotes and other content */
	@Test
	public void testString() {
		testExpression("\"This is a string\"", "result-producer", true, TIMEOUT, //
			new ExpressionTester("Simple String").withType("string")//
				.withFieldContent("content", "This is a string"));

		testExpression("\"This string has an escaped quote (\\\")\"", "result-producer", true, TIMEOUT, //
			new ExpressionTester("Simple String").withType("string")//
				.withFieldContent("content", "This string has an escaped quote (\\\")"));
	}

	/**
	 * Tests parsing of a simple, but complete, java file
	 * 
	 * @throws IOException If the file cannot be read
	 */
	@Test
	public void testSimpleJavaFile() throws IOException {
		testExpressionOnFile("SimpleParseableJavaFile.java", TIMEOUT * 5,
			new ExpressionTester("SimpleParseableJavaFile").withType("java-file")//
				.withField("package", pkg -> pkg.withContent("org.expresso.java"))//
				.withField("content",
					clazz -> clazz.withType("class-declaration")//
						.withFieldContent("javadoc",
							"/**\n"//
								+ " * This file is only here to be parsed by a unit test\n"//
								+ " * \n"//
								+ " * @author Andrew Butler\n"//
								+ " */")
						.withFieldContent("name", "SimpleParseableJavaFile")//
						.withFieldContent("qualifier", "public")//
						.withFieldContent("type", "class")//
						.withField("content", addMethod -> {
							addMethod.withType("method-declaration")//
								.withFieldContents("qualifier", "public", "static")//
								.withFieldContent("type", "int")//
								.withFieldContent("name", "add")//
								.withField("parameter", //
									param -> param.withFieldContent("type", "int").withFieldContent("name", "a"), //
									param -> param.withFieldContent("type", "int").withFieldContent("name", "b"))
								.withField("body.content",
									body -> body.withType("return")//
										.withField("value",
											rv -> rv//
												.withFieldContent("name", "+")//
												.withFieldContent("left", "a")//
												.withFieldContent("right", "b")));
						}, subMethod -> {
							subMethod.withType("method-declaration")//
								.withFieldContents("qualifier", "public", "static")//
								.withFieldContent("type", "int")//
								.withFieldContent("name", "subtract")//
								.withField("parameter", //
									param -> param.withFieldContent("type", "int").withFieldContent("name", "a"), //
									param -> param.withFieldContent("type", "int").withFieldContent("name", "b"))
								.withField("body.content",
									body -> body.withType("return")//
										.withField("value",
											rv -> rv//
												.withFieldContent("name", "-")//
												.withFieldContent("left", "a")//
												.withFieldContent("right", "b")));
						}, op1 -> {
							op1.withType("method-declaration")//
								.withFieldContents("qualifier", "public", "static")//
								.withFieldContent("type", "int")//
								.withFieldContent("name", "op1")//
								.withField("parameter", //
									param -> param.withFieldContent("type", "int").withFieldContent("name", "a"), //
									param -> param.withFieldContent("type", "int").withFieldContent("name", "b"), //
									param -> param.withFieldContent("type", "int").withFieldContent("name", "c"))
								.withField("body.content",
									body -> body.withType("return")//
										.withField("value",
											rv -> rv.withType("add")//
												.withFieldContent("left", "a")//
												.withField("right",
													right -> right//
														.withFieldContent("name", "*")//
														.withFieldContent("left", "b")//
														.withFieldContent("right", "c"))));
						})));
	}

	/** Tests a simple assignment statement (theParser = grammarParser.parseGrammar();) */
	@Test
	public void testAssign1() {
		testExpression("theParser = grammarParser.parseGrammar();", "statement", true, TIMEOUT, //
			new ExpressionTester("assign1").withType("statement")//
				.withField("content",
					content -> content.withType("assign")//
						.withFieldContent("variable", "theParser")//
						.withField("operand",
							val -> val.withType("method")//
								.withFieldContent("target", "grammarParser")//
								.withFieldContent("method.name", "parseGrammar"))));
	}

	/** Another assignment test (theParser = DefaultGrammarParser.class.getResource;) */
	@Test
	public void testAssign2() {
		testExpression("theParser = DefaultGrammarParser.class.getResource;", "statement", true, TIMEOUT, //
			new ExpressionTester("assign2").withType("statement")//
				.withField("content",
					content -> content.withType("assign")//
						.withFieldContent("variable", "theParser")//
						.withFieldContent("operand", "DefaultGrammarParser.class.getResource")));
	}

	/**
	 * A more complex assignment test:
	 * <code>theParser = grammarParser.parseGrammar(DefaultGrammarParser.class.getResource("/org/expresso/grammars/Java8.xml")</code>
	 */
	@Test
	public void testAssign3() {
		testExpression(
			"theParser = grammarParser.parseGrammar(DefaultGrammarParser.class.getResource(\"/org/expresso/grammars/Java8.xml\"));",
			"statement", true, TIMEOUT, //
			new ExpressionTester("assign3").withType("statement")//
				.withField("content",
					content -> content.withType("assign")//
						.withFieldContent("variable", "theParser")//
						.withField("operand",
							val -> val.withType("method")//
								.withFieldContent("target", "grammarParser")//
								.withFieldContent("method.name", "parseGrammar")//
								.withField("method.arguments.argument",
									arg -> arg.withType("method")//
										.withFieldContent("target", "DefaultGrammarParser.class")//
										.withFieldContent("method.name", "getResource")//
										.withField("method.arguments.argument", argArg -> argArg.withType("string")//
											.withFieldContent("content", "/org/expresso/grammars/Java8.xml"))))));
	}

	/**
	 * Parses this file
	 * 
	 * @throws IOException If the file cannot be read
	 */
	@Test
	public void testParseSelf() throws IOException {
		testExpressionOnFile(getClass().getSimpleName() + ".java", TIMEOUT * 4, //
			new ExpressionTester(getClass().getSimpleName())//
				.withField("content", clazz -> clazz.withType("class-declaration")//
					.withField("name", name -> name.withContent(getClass().getSimpleName()))));
	}
}
