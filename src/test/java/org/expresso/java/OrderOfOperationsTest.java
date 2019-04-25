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
				.withField("name", "-")//
				.withField("operand",
					op -> op//
						.withField("name", "*")//
						.withField("left", "a")//
						.withField("right", "b")));
	}

	/** Tests a*-b */
	@Test
	public void testOperationOrder2() {
		testExpression("a*-b", "result-producer", true, TIMEOUT, //
			new ExpressionTester("a*-b")//
				.withField("name", "*")//
				.withField("left", "a")//
				.withField("right",
					right -> right//
						.withField("name", "-")//
						.withField("operand", "b")));
	}

	/** Tests 5+(a-b) */
	@Test
	public void testOperationOrder3() {
		testExpression("5+(a-b)", "result-producer", true, TIMEOUT, //
			new ExpressionTester("5+(a-b)")//
				.withField("name", "+")//
				.withField("left", "5")//
				.withField("right",
					right -> right.withType("parenthetic")//
						.withField("content",
							content -> content//
								.withField("name", "-")//
								.withField("left", "a")//
								.withField("right", "b"))));
	}

	/** Tests -a*b+5 */
	@Test
	public void testOperationOrder4() {
		testExpression("-a*b+5", "result-producer", true, TIMEOUT, //
			new ExpressionTester("-a*b+5")//
				.withField("name", "+")//
				.withField("left",
					left -> left//
						.withField("name", "-")//
						.withField("operand",
							op -> op//
								.withField("name", "*")//
								.withField("left", "a")//
								.withField("right", "b")))
				.withField("right", right -> right.withContent("5")));
	}

	/** Tests a/b/c */
	@Test
	public void testOperationOrder5() {
		testExpression("a/b/c", "result-producer", true, TIMEOUT, //
			new ExpressionTester("a/b/c")//
				.withField("name", "/")//
				.withField("left",
					left -> left//
						.withField("name", "/")//
						.withField("left", "a")//
						.withField("right", "b"))//
				.withField("right", "c"));
	}

	/** Tests a*b+c/d-e+f/g */
	@Test
	public void testOperationOrder6() {
		testExpression("a*b+c/d*e-f+g/h", "result-producer", true, TIMEOUT, //
			new ExpressionTester("a*b+c/d-e+f/g")//
				.withField("name", "+")//
				.withField("left",
					left -> left//
						.withField("name", "-")//
						.withField("left",
							ll -> ll//
								.withField("name", "+")//
								.withField("left",
									lll -> lll//
										.withField("name", "*")//
										.withField("left", "a")//
										.withField("right", "b"))//
								.withField("right",
									llr -> llr//
										.withField("name", "*")//
										.withField("left",
											llrl -> llrl//
												.withField("name", "/")//
												.withField("left", "c")//
												.withField("right", "d"))//
										.withField("right", "e")))//
						.withField("right", "e"))//
				.withField("right",
					right -> right//
						.withField("name", "/")//
						.withField("left", "f")//
						.withField("right", "g")));
	}
}
