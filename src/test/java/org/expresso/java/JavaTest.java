package org.expresso.java;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.util.function.Supplier;

import org.expresso.*;
import org.expresso.stream.CharSequenceStream;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.qommons.ArrayUtils;
import org.qommons.BreakpointHere;
import org.qommons.QommonsUtils;
import org.qommons.collect.BetterList;
import org.qommons.config.QommonsConfig;

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

	@SuppressWarnings("deprecation")
	private static <T> T getWithin(Supplier<T> task, long time) {
		if (time == 0)
			time = Long.MAX_VALUE;
		// return task.get();
		boolean[] done = new boolean[1];
		Object[] result = new Object[1];
		Throwable[] ex = new Throwable[1];
		Thread worker = new Thread(() -> {
			try {
				result[0] = task.get();
				done[0] = true;
			} catch (ThreadDeath e) {
			} catch (RuntimeException | Error e) {
				ex[0] = e;
				done[0] = true;
			}
		}, JavaTest.class.getSimpleName() + " worker");
		worker.start();
		long start = System.currentTimeMillis();
		while (System.currentTimeMillis() - start < time && !done[0]) {
			try {
				Thread.sleep(10);
			} catch (InterruptedException e) {
			}
		}
		if (done[0]) {
			if (ex[0] != null) {
				ex[0].setStackTrace(QommonsUtils.patchStackTraces(//
					ex[0].getStackTrace(), //
					ArrayUtils.remove(Thread.currentThread().getStackTrace(), 0), //
					Thread.class.getName(), "run"));
				if (ex[0] instanceof RuntimeException)
					throw (RuntimeException) ex[0];
				else
					throw (Error) ex[0];
			} else
				return (T) result[0];
		} else {
			worker.stop();
			throw new IllegalStateException("Task took longer than " + QommonsUtils.printTimeLength(time));
		}
	}

	private Expression<CharSequenceStream> parse(String expression, String type, boolean checkForErrors, long time) {
		return parse(CharSequenceStream.from(expression), type, checkForErrors, time);
	}

	private Expression<CharSequenceStream> parse(CharSequenceStream stream, String type, boolean checkForErrors, long time) {
		return getWithin(() -> {
			ExpressionType<CharSequenceStream> component = theParser.getExpressionsByName().get(type);
			if (component == null)
				component = theParser.getExpressionClasses().get(type);
			if (component == null)
				throw new IllegalArgumentException("No such type or class: " + type);
			Expression<CharSequenceStream> result;
			try {
				result = theParser.parse(stream, component, checkForErrors ? 0 : -5);
			} catch (IOException e) {
				throw new IllegalStateException(e);
			}
			if (checkForErrors) {
				Assert.assertTrue("No result!", result != null);
				if (result.getErrorCount() > 0)
					Assert.assertEquals(result.getFirstError().getLocalErrorMessage(), 0, result.getErrorCount());
			}
			Assert.assertEquals("Incomplete match: " + result.printContent(false), stream.length(), result.length());
			return result;
		}, time);
	}

	/** Tests parsing a simple variable name (vbl) */
	@Test
	public void testVariable() {
		Expression<CharSequenceStream> result = parse("vbl", "result-producer", true, TIMEOUT);
		result = result.unwrap();
		Assert.assertEquals("qualified-name", ((ConfiguredExpressionType<?>) result.getType()).getName());
		Assert.assertEquals("vbl", result.getField("name").getFirst().printContent(false));
	}

	/** Test parsing a field invocation (vbl.field) */
	@Test
	public void testField() {
		Expression<CharSequenceStream> result = parse("vbl.field", "result-producer", true, TIMEOUT).unwrap();
		new ExpressionTester("fieldTest").withType("qualified-name", "field-ref")//
			.withField("target", target -> {
				target.withType("qualified-name").withContent("vbl");
			}).withField("name", name -> {
				name.withContent("field");
			}).withField("method")//
			.test(result);
	}

	/** Tests parsing a nested field invocation (vbl.field1.field2) */
	@Test
	public void testDoubleField() {
		Expression<CharSequenceStream> result = parse("vbl.field1.field2", "result-producer", true, TIMEOUT).unwrap();
		Assert.assertEquals("qualified-name", ((ConfiguredExpressionType<?>) result.getType()).getName());
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
		Assert.assertEquals("qualified-name", ((ConfiguredExpressionType<?>) args.getLast().getWrapped().unwrap().getType()).getName());
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
			TIMEOUT).unwrap();
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

	/** Tests parsing a variable declaration(java.util.ArrayList<Integer> list;) */
	@Test
	public void testVarDeclaration() {
		String expression = "java.util.ArrayList<Integer> list;";
		Expression<CharSequenceStream> result = parse(expression, "body-content", true, TIMEOUT).unwrap();
		new ExpressionTester("Var declaration").withType("variable-declaration").withField("type", type -> {
			type.withType("generic-type").withField("base", base -> {
				base.withContent("java.util.ArrayList");
			}).withField("parameters.parameter", param -> {
				param.withContent("Integer");
			});
		}).withField("name", name -> name.withContent("list"))//
			.test(result);
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
		new ExpressionTester("block").withType("block").withField("content.?content", s1 -> {
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
		})//
			.test(expr);
	}

	/**
	 * Tests A fairly complex expression
	 * (<code>org.observe.collect.ObservableCollection.create(org.observe.util.TypeTokens.get().STRING,new org.qommons.tree.SortedTreeList&lt;String&gt;(true, org.qommons.QommonsUtils.DISTINCT_NUMBER_TOLERANT))</code>)
	 */
	@Test
	public void testWow() {
		String expression = "org.observe.collect.ObservableCollection.create(org.observe.util.TypeTokens.get().STRING,"
			+ " new org.qommons.tree.SortedTreeList<String>(true, org.qommons.QommonsUtils.DISTINCT_NUMBER_TOLERANT))";
		Expression<CharSequenceStream> result = parse(expression, "result-producer", true, TIMEOUT * 5).unwrap();
		checkWow(result);
	}

	private static void checkWow(Expression<?> expr) {
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
			})//
			.test(expr);
	}

	/** Tests parsing a very simple expression (a+b) */
	@Test
	public void testSimpleOperations() {
		new ExpressionTester("add").withType("add").withField("left", left -> {
			left.withType("qualified-name").withContent("a");
		}).withField("right", right -> {
			right.withType("qualified-name").withContent("b");
		})//
			.test(parse("a+b", "result-producer", true, TIMEOUT));
	}

	/** Tests parsing a method declaration (with body) */
	@Test
	public void testMethodDeclaration() {
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
			})//
			.test(parse(String.join("", "\tpublic static int add(int a, int b){\n", //
				"\t\treturn a+b;\n", //
				"\t}\n"), "class-content", true, TIMEOUT * 2));
	}

	/**
	 * Tests parsing of a simple, but complete, java file
	 * 
	 * @throws IOException If the file cannot be read
	 */
	@Test
	public void testSimpleJavaFile() throws IOException {
		URL file = QommonsConfig.toUrl("src/test/java/org/expresso/java/SimpleParseableJavaFile.java");
		Expression<CharSequenceStream> result;
		try (Reader reader = new InputStreamReader(file.openStream())) {
			result = parse(CharSequenceStream.from(reader, 4096), "java-file", true, TIMEOUT * 5);
		}
		result = result.unwrap();
		new ExpressionTester("SimpleParseableJavaFile").withType("java-file")//
			.withField("package", pkg -> pkg.withContent("org.expresso.java"))//
			.withField("content",
				clazz -> clazz.withType("class-declaration")//
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
					}))//
			.test(result);
	}
}
