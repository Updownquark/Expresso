package org.expresso;

import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.junit.Assert;
import org.qommons.ArrayUtils;
import org.qommons.collect.BetterList;

import junit.framework.AssertionFailedError;

/** A utility to test the content of parsed expressions */
public class ExpressionTester {
	private final String theName;
	private Consumer<String> theTypeTest;
	private Consumer<String> theContentTest;
	private Map<List<String>, ExpressionFieldsTester> theFieldTests;

	/** @param name The name of the expression to test */
	public ExpressionTester(String name) {
		theName = name;
	}

	/**
	 * @param passes Whether the test passed
	 * @param error Supplies a message for the error to fire if the test failed
	 */
	public static void test(boolean passes, Supplier<String> error) {
		if (!passes)
			throw new AssertionFailedError(error.get());
	}

	/**
	 * @param type The expected type of the expression to test
	 * @return This tester
	 */
	public ExpressionTester withType(String type) {
		return withType(t -> test(type.equals(t), () -> theName + ": type should be " + type + ", not " + t));
	}

	/**
	 * @param types Possible types for the expression to test
	 * @return This tester
	 */
	public ExpressionTester withType(String... types) {
		return withType(
			t -> test(ArrayUtils.contains(types, t), () -> theName + ": type should be one of " + Arrays.toString(types) + ", not " + t));
	}

	/**
	 * @param typeTest The tester for this expression's type (should throw an {@link AssertionFailedError} if the the type is invalid)
	 * @return This tester
	 */
	public ExpressionTester withType(Consumer<String> typeTest) {
		theTypeTest = typeTest;
		return this;
	}

	/**
	 * @param content The expected printed content (trimmed) of the expression to test
	 * @return This tester
	 */
	public ExpressionTester withContent(String content) {
		return withContent(c -> test(content.equals(trimDos(c)), () -> theName + ": content should be " + content + ", not " + c));
	}

	/**
	 * @param contentTest The tester for this expression's printed (and trimmed) content (should throw an {@link AssertionFailedError} if
	 *        the the type is invalid)
	 * @return This tester
	 */
	public ExpressionTester withContent(Consumer<String> contentTest) {
		theContentTest = contentTest;
		return this;
	}

	/**
	 * @param field The '.'-delimited field name(s)
	 * @param fields A tester for each expression in the given field list
	 * @return This tester
	 */
	public ExpressionTester withField(String field, Consumer<ExpressionTester>... fields) {
		if (theFieldTests == null)
			theFieldTests = new LinkedHashMap<>();
		List<String> fieldSplit = Arrays.asList(field.split("\\."));
		ExpressionFieldsTester mockFields = theFieldTests.computeIfAbsent(fieldSplit,
			k -> new ExpressionFieldsTester(theName + "." + field));
		for (Consumer<ExpressionTester> fieldTest : fields)
			mockFields.add(fieldTest);
		return this;
	}

	/**
	 * @param field The '.'-delimited field name(s)
	 * @param content The content for the expected single field value
	 * @return This tester
	 */
	public ExpressionTester withField(String field, String content) {
		return withField(field, fieldExp -> fieldExp.withContent(content));
	}

	/**
	 * @param expression The expression to test
	 * @throws AssertionFailedError If the expression does not match this tester's configuration
	 */
	public void test(Expression<?> expression) throws AssertionFailedError {
		expression = expression.unwrap();
		if (theTypeTest != null) {
			Assert.assertTrue(theName + " does not resolve to a configured expression type",
				expression.getType() instanceof ConfiguredExpressionType);
			theTypeTest.accept(((ConfiguredExpressionType<?>) expression.getType()).getName());
		}
		if (theContentTest != null)
			theContentTest.accept(expression.printContent(false).trim());
		if (theFieldTests != null) {
			for (Map.Entry<List<String>, ExpressionFieldsTester> field : theFieldTests.entrySet()) {
				BetterList<? extends ExpressionField<?>> fieldExs = expression.getField(//
					field.getKey().toArray(new String[field.getKey().size()]));
				test(field.getValue().expressions.size() == fieldExs.size(), () -> theName + ": Expected "
					+ field.getValue().expressions.size() + " " + field.getKey() + " fields, but found " + fieldExs.size());
				Iterator<? extends ExpressionField<?>> exIter = fieldExs.iterator();
				Iterator<ExpressionTester> mockIter = field.getValue().expressions.iterator();
				while (exIter.hasNext())
					mockIter.next().test(exIter.next().unwrap());
			}
		}
	}

	private static String trimDos(String s) {
		int idx = s.indexOf('\r');
		if (idx >= 0)
			return s.replaceAll("\\r", "");
		else
			return s;
	}

	static class ExpressionFieldsTester {
		private final String theName;
		final List<ExpressionTester> expressions;

		ExpressionFieldsTester(String name) {
			theName = name;
			expressions = new LinkedList<>();
		}

		void add(Consumer<ExpressionTester> ex) {
			ExpressionTester mock = new ExpressionTester(theName + "[" + expressions.size() + "]");
			ex.accept(mock);
			expressions.add(mock);
		}
	}
}
