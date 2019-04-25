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
			new ExpressionTester("doubleField").withType("qualified-name")//
				.withField("target",
					inner -> inner//
						.withField("target", "vbl")//
						.withField("name", "field1"))//
				.withField("name", "field2"));
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
				.withField("type", "Integer")//
				.withField("arguments.argument", //
					arg1 -> arg1.withContent("5"), //
					arg2 -> arg2.withContent("b")));
	}

	/** Test parsing a constructor with generic type arguments (new java.util.ArrayList&lt;Integer>(5)) */
	@Test
	public void testGenericConstructor() {
		testExpression("new java.util.ArrayList<java.lang.Integer>(5)", "result-producer", true, TIMEOUT, //
			new ExpressionTester("generic-constructor").withType("constructor")//
				.withField("type",
					type -> type.withType("generic-type")//
						.withField("base", "java.util.ArrayList")//
						.withField("parameters.parameter", "java.lang.Integer"))//
				.withField("arguments.argument", arg -> arg.withType("number").withContent("5")));
	}

	/** Tests a multi-argument static method invocation (list.addAll(java.util.Arrays.asList(1, 2, 3, 4, 5))) */
	@Test
	public void testMethod() {
		testExpression("java.util.Arrays.asList(1, 2, 3, 4, 5)", "result-producer", true, TIMEOUT,
			checkArraysAsList(new ExpressionTester("method")));
	}

	private static ExpressionTester checkArraysAsList(ExpressionTester tester) {
		return tester.withType("method")//
			.withField("target", target -> target.withType("qualified-name").withContent("java.util.Arrays"))//
			.withField("method.name", "asList")//
			.withField("method.arguments.argument", //
				arg1 -> arg1.withContent("1"), //
				arg2 -> arg2.withContent("2"), //
				arg3 -> arg3.withContent("3"), //
				arg4 -> arg4.withContent("4"), //
				arg5 -> arg5.withContent("5"));
	}

	/** Tests a nested method invocation (list.addAll(java.util.Arrays.asList(1, 2, 3, 4, 5))) */
	@Test
	public void testDoubleMethod() {
		testExpression("list.addAll(java.util.Arrays.asList(1, 2, 3, 4, 5))", "result-producer", false, TIMEOUT, //
			new ExpressionTester("doubleMethod").withType("method")//
				.withField("target", "list")//
				.withField("method.name", "addAll")//
				.withField("method.arguments.argument", arg -> checkArraysAsList(arg)));
	}

	/** Tests parsing a variable declaration(java.util.ArrayList<Integer> list;) */
	@Test
	public void testVarDeclaration() {
		String expression = "java.util.ArrayList<Integer> list;";
		testExpression(expression, "body-content", true, TIMEOUT, //
			new ExpressionTester("Var declaration").withType("variable-declaration").withField("type", type -> {
				type.withType("generic-type").withField("base", base -> {
					base.withContent("java.util.ArrayList");
				}).withField("parameters.parameter", param -> {
					param.withContent("Integer");
				});
			}).withField("name", name -> name.withContent("list")));
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
				.withField("content.?content", s1 -> {
					s1.withType("variable-declaration").withField("type", s1Type -> {
						s1Type.withType("generic-type").withField("base", s1TypeBase -> {
							s1TypeBase.withContent("java.util.ArrayList");
						}).withField("parameters.parameter", s1TypeParams -> {
							s1TypeParams.withContent("Integer");
						});
					});
				}, s2 -> {
					s2.withType("assign").withField("variable", s2Var -> {
						s2Var.withType("qualified-name").withContent("list");
					}).withField("operand", s2Op -> {
						s2Op.withType("constructor").withField("type", s2OpType -> {
							s2OpType.withType("generic-type").withField("base", s2OpTypeBase -> {
								s2OpTypeBase.withContent("ArrayList");
							}).withField("parameters.parameter"); // No parameters
						}).withField("arguments.argument", s2OpArgs -> {
							s2OpArgs.withType("number").withContent("5");
						});
					});
				}, s3 -> {
					s3.withType("method").withField("target", s3Target -> {
						s3Target.withType("qualified-name").withContent("list");
					}).withField("method.name", s3MethodName -> {
						s3MethodName.withContent("addAll");
					}).withField("method.arguments.argument", s3Arg -> {
						s3Arg.withType("method").withField("target", s3ArgTarget -> {
							s3ArgTarget.withContent("java.util.Arrays");
						}).withField("method.name", s3ArgMethodName -> {
							s3ArgMethodName.withContent("asList");
						}).withField("method.arguments.argument", s3ArgArg1 -> {
							s3ArgArg1.withType("number").withContent("1");
						}, s3ArgArg2 -> {
							s3ArgArg2.withType("number").withContent("2");
						}, s3ArgArg3 -> {
							s3ArgArg3.withType("number").withContent("3");
						}, s3ArgArg4 -> {
							s3ArgArg4.withType("number").withContent("4");
						}, s3ArgArg5 -> {
							s3ArgArg5.withType("number").withContent("5");
						});
					});
				}));
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
				.withField("target", target -> {
					target.withContent("org.observe.collect.ObservableCollection");
				}).withField("method.name", method -> {
					method.withContent("create");
				}).withField("method.arguments.argument", arg1 -> {
					arg1.withType("qualified-name", "field-ref").withField("target", arg1Target -> {
						arg1Target.withType("method").withField("target", arg1TargetTarget -> {
							arg1TargetTarget.withContent("org.observe.util.TypeTokens");
						}).withField("method.name", arg1TargetMethod -> arg1TargetMethod.withContent("get"));
					}).withField("name", arg1FieldName -> arg1FieldName.withContent("STRING"));
				}, arg2 -> {
					arg2.withType("constructor").withField("type", arg2Type -> {
						arg2Type.withType("generic-type").withField("base", arg2TypeBase -> {
							arg2TypeBase.withContent("org.qommons.tree.SortedTreeList");
						}).withField("parameters.parameter", arg2TypeParams -> {
							arg2TypeParams.withContent("String");
						});
					}).withField("arguments.argument", arg2Arg1 -> {
						// TODO Right now, the parser is finding an identifier instead of a boolean.
						// This is probably just a problem with the grammar.
						// arg2Arg1.withType("boolean");
						arg2Arg1.withContent("true");
					}, arg2Arg2 -> {
						arg2Arg2.withType("qualified-name").withField("target", arg2Arg2Target -> {
							arg2Arg2Target.withContent("org.qommons.QommonsUtils");
						}).withField("name", arg2Arg2Name -> {
							arg2Arg2Name.withContent("DISTINCT_NUMBER_TOLERANT");
						});
					});
				}));
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
				.withField("qualifier", //
					qualifier -> qualifier.withContent("public"), //
					qualifier -> qualifier.withContent("static")//
				).withField("name", name -> name.withContent("add")//
				).withField("body.content", body -> {
					body.withType("return").withField("value", returnValue -> {
						returnValue.withType("add").withField("left", left -> {
							left.withType("qualified-name").withContent("a");
						}).withField("right", right -> {
							right.withType("qualified-name").withContent("b");
						});
					});
				}));
	}

	/** Tests parsing strings, including escaped quotes and other content */
	@Test
	public void testString() {
		testExpression("\"This is a string\"", "result-producer", true, TIMEOUT, //
			new ExpressionTester("Simple String").withType("string").withField("content", val -> val.withContent("This is a string")));

		testExpression("\"This string has an escaped quote (\\\")\"", "result-producer", true, TIMEOUT, //
			new ExpressionTester("Simple String").withType("string").withField("content", val -> val//
				.withContent("This string has an escaped quote (\\\")")));
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
						.withField("javadoc",
							jd -> jd.withContent("/**\n"//
								+ " * This file is only here to be parsed by a unit test\n"//
								+ " * \n"//
								+ " * @author Andrew Butler\n"//
								+ " */"))
						.withField("name", name -> name.withContent("SimpleParseableJavaFile"))//
						.withField("qualifier", qfr -> qfr.withContent("public"))//
						.withField("type", type -> type.withContent("class"))//
						.withField("content", addMethod -> {
							addMethod.withType("method-declaration")//
								.withField("qualifier", //
									qfr -> qfr.withContent("public"), qfr -> qfr.withContent("static"))//
								.withField("type", type -> type.withContent("int"))//
								.withField("name", name -> name.withContent("add"))//
								.withField("parameter", //
									param -> param.withField("type", pt -> pt.withContent("int")).withField("name",
										name -> name.withContent("a")), //
									param -> param.withField("type", pt -> pt.withContent("int")).withField("name",
										name -> name.withContent("b")))
								.withField("body.content",
									body -> body.withType("return").withField("value",
										rv -> rv.withType("add")//
											.withField("left", left -> left.withContent("a"))//
											.withField("right", right -> right.withContent("b"))));
						}, subMethod -> {
							subMethod.withType("method-declaration")//
								.withField("qualifier", //
									qfr -> qfr.withContent("public"), qfr -> qfr.withContent("static"))//
								.withField("type", type -> type.withContent("int"))//
								.withField("name", name -> name.withContent("subtract"))//
								.withField("parameter", //
									param -> param.withField("type", pt -> pt.withContent("int")).withField("name",
										name -> name.withContent("a")), //
									param -> param.withField("type", pt -> pt.withContent("int")).withField("name",
										name -> name.withContent("b")))
								.withField("body.content",
									body -> body.withType("return").withField("value",
										rv -> rv.withType("subtract")//
											.withField("left", left -> left.withContent("a"))//
											.withField("right", right -> right.withContent("b"))));
						}, op1 -> {
							op1.withType("method-declaration")//
								.withField("qualifier", //
									qfr -> qfr.withContent("public"), qfr -> qfr.withContent("static"))//
								.withField("type", type -> type.withContent("int"))//
								.withField("name", name -> name.withContent("op1"))//
								.withField("parameter", //
									param -> param.withField("type", pt -> pt.withContent("int")).withField("name",
										name -> name.withContent("a")), //
									param -> param.withField("type", pt -> pt.withContent("int")).withField("name",
										name -> name.withContent("b")), //
									param -> param.withField("type", pt -> pt.withContent("int")).withField("name",
										name -> name.withContent("c")))
								.withField("body.content",
									body -> body.withType("return").withField("value",
										rv -> rv.withType("add")//
											.withField("left", left -> left.withContent("a"))//
											.withField("right",
												right -> right.withType("multiply")//
													.withField("left", multLeft -> multLeft.withContent("b"))//
													.withField("right", multRight -> multRight.withContent("c")))));
						})));
	}

	/** Tests a simple assignment statement (theParser = grammarParser.parseGrammar();) */
	@Test
	public void testAssign1() {
		testExpression("theParser = grammarParser.parseGrammar();", "statement", true, TIMEOUT, //
			new ExpressionTester("assignment").withType("statement")//
				.withField("content",
					content -> content.withType("assign")//
						.withField("variable", "theParser")//
						.withField("operand",
							val -> val.withType("method")//
								.withField("target", "grammarParser")//
								.withField("method.name", "parseGrammar"))));
	}

	/**
	 * A more complex assignment test:
	 * <code>theParser = grammarParser.parseGrammar(DefaultGrammarParser.class.getResource("/org/expresso/grammars/Java8.xml")</code>
	 */
	@Test
	public void testAssign2() {
		testExpression(
			"theParser = grammarParser.parseGrammar(DefaultGrammarParser.class.getResource(\"/org/expresso/grammars/Java8.xml\"));",
			"statement", true, TIMEOUT, //
			new ExpressionTester("assignment").withType("statement")//
				.withField("content",
					content -> content.withType("assign")//
						.withField("variable", "theParser")//
						.withField("operand",
							val -> val.withType("method")//
								.withField("target", "grammarParser")//
								.withField("method.name", "parseGrammar")//
								.withField("method.arguments.argument",
									arg -> arg.withType("method")//
										.withField("target", "DefaultGrammarParser.class")//
										.withField("method.name", "getResource")//
										.withField("method.arguments.argument", argArg -> argArg.withType("string")//
											.withField("content", "/org/expresso/grammars/Java8.xml"))))));
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
