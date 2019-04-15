package org.expresso;

import java.util.*;
import java.util.function.Consumer;

import org.junit.Assert;
import org.qommons.ArrayUtils;
import org.qommons.collect.BetterList;

public class MockExpression {
	private final String theName;
	private Consumer<String> theTypeTest;
	private Consumer<String> theContentTest;
	private Map<List<String>, MockExpressionFields> theFieldTests;

	public MockExpression(String name) {
		theName = name;
	}

	public MockExpression withType(String type) {
		return withType(t -> Assert.assertEquals(theName + ": type should be " + type + ", not " + t, type, t));
	}

	public MockExpression withType(String... types) {
		return withType(t -> Assert.assertTrue(theName + ": type must be one of " + Arrays.toString(types) + ", not " + t,
			ArrayUtils.contains(types, t)));
	}

	public MockExpression withType(Consumer<String> typeTest) {
		theTypeTest = typeTest;
		return this;
	}

	public MockExpression withContent(String content) {
		return withContent(c -> Assert.assertEquals(theName + ": content should be " + content + ", not " + c, content, c));
	}

	public MockExpression withContent(Consumer<String> contentTest) {
		theContentTest = contentTest;
		return this;
	}

	public MockExpressionFields withField(String field) {
		if (theFieldTests == null)
			theFieldTests = new LinkedHashMap<>();
		List<String> fieldSplit = Arrays.asList(field.split("\\."));
		return theFieldTests.computeIfAbsent(fieldSplit, k -> new MockExpressionFields(theName + "." + field));
	}

	public void test(Expression<?> expression) {
		expression = expression.unwrap();
		if (theTypeTest != null) {
			Assert.assertTrue(theName + " does not resolve to a configured expression type",
				expression instanceof ConfiguredExpressionType);
			theTypeTest.accept(((ConfiguredExpressionType<?>) expression.getType()).getName());
		}
		if (theContentTest != null)
			theContentTest.accept(expression.printContent(false).trim());
		if (theFieldTests != null) {
			for (Map.Entry<List<String>, MockExpressionFields> field : theFieldTests.entrySet()) {
				BetterList<? extends ExpressionField<?>> fieldExs = expression
					.getField(field.getKey().toArray(new String[field.getKey().size()]));
				Assert.assertEquals(theName + field.getKey(), field.getValue().expressions.size(), fieldExs.size());
				Iterator<? extends ExpressionField<?>> exIter = fieldExs.iterator();
				Iterator<MockExpression> mockIter = field.getValue().expressions.iterator();
				while (exIter.hasNext())
					mockIter.next().test(exIter.next().unwrap());
			}
		}
	}

	public class MockExpressionFields {
		private final String theName;
		final List<MockExpression> expressions;

		MockExpressionFields(String name) {
			theName = name;
			expressions = new LinkedList<>();
		}

		MockExpressionFields add(Consumer<MockExpression> ex) {
			MockExpression mock = new MockExpression(theName + "[" + expressions.size() + "]");
			ex.accept(mock);
			expressions.add(mock);
			return this;
		}

		MockExpressionFields withField(String field) {
			return MockExpression.this.withField(field);
		}

		MockExpression out() {
			return MockExpression.this;
		}
	}
}
