package org.expresso.java;

import java.io.IOException;

import org.expresso.ExpressionTester;
import org.junit.Before;
import org.junit.Test;

/** Tests for correct order of expressions */
public class OrderOfOperationsTest extends JavaTest {
	@Before
	@Override
	public void setupParser() throws IOException {
		super.setupParser();
	}

	/** Tests -a*b */
	@Test
	public void testOperationOrder1() {
		testExpression("-a*b", "result-producer", true, org.expresso.java.JavaTest.TIMEOUT, //
			new ExpressionTester("-a*b")//
				.withFieldContent("name", "-")//
				.withField("operand",
					op -> op//
						.withFieldContent("name", "*")//
						.withFieldContent("left", "a")//
						.withFieldContent("right", "b")));
	}

	/** Tests a*-b */
	@Test
	public void testOperationOrder2() {
		testExpression("a*-b", "result-producer", true, TIMEOUT, //
			new ExpressionTester("a*-b")//
				.withFieldContent("name", "*")//
				.withFieldContent("left", "a")//
				.withField("right",
					right -> right//
						.withFieldContent("name", "-")//
						.withFieldContent("operand", "b")));
	}

	/** Tests 5+(a-b) */
	@Test
	public void testOperationOrder3() {
		testExpression("5+(a-b)", "result-producer", true, TIMEOUT, //
			new ExpressionTester("5+(a-b)")//
				.withFieldContent("name", "+")//
				.withFieldContent("left", "5")//
				.withField("right",
					right -> right.withType("parenthetic")//
						.withField("content",
							content -> content//
								.withFieldContent("name", "-")//
								.withFieldContent("left", "a")//
								.withFieldContent("right", "b"))));
	}

	/** Tests -a*b+5 */
	@Test
	public void testOperationOrder4() {
		testExpression("-a*b+5", "result-producer", true, TIMEOUT, //
			new ExpressionTester("-a*b+5")//
				.withFieldContent("name", "+")//
				.withField("left",
					left -> left//
						.withFieldContent("name", "-")//
						.withField("operand",
							op -> op//
								.withFieldContent("name", "*")//
								.withFieldContent("left", "a")//
								.withFieldContent("right", "b")))
				.withField("right", right -> right.withContent("5")));
	}

	/** Tests a/b/c */
	@Test
	public void testOperationOrder5() {
		testExpression("a/b/c", "result-producer", true, TIMEOUT, //
			new ExpressionTester("a/b/c")//
				.withFieldContent("name", "/")//
				.withField("left",
					left -> left//
						.withFieldContent("name", "/")//
						.withFieldContent("left", "a")//
						.withFieldContent("right", "b"))//
				.withFieldContent("right", "c"));
	}

	/** Tests a*b+c/d-e+f/g */
	@Test
	public void testOperationOrder6() {
		testExpression("a*b+c/d*e-f+g/h", "result-producer", true, TIMEOUT, //
			new ExpressionTester("a*b+c/d-e+f/g")//
				.withFieldContent("name", "+")//
				.withField("left",
					left -> left//
						.withFieldContent("name", "-")//
						.withField("left",
							ll -> ll//
								.withFieldContent("name", "+")//
								.withField("left",
									lll -> lll//
										.withFieldContent("name", "*")//
										.withFieldContent("left", "a")//
										.withFieldContent("right", "b"))//
								.withField("right",
									llr -> llr//
										.withFieldContent("name", "*")//
										.withField("left",
											llrl -> llrl//
												.withFieldContent("name", "/")//
												.withFieldContent("left", "c")//
												.withFieldContent("right", "d"))//
										.withFieldContent("right", "e")))//
						.withFieldContent("right", "e"))//
				.withField("right",
					right -> right//
						.withFieldContent("name", "/")//
						.withFieldContent("left", "f")//
						.withFieldContent("right", "g")));
	}
}
