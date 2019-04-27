package org.expresso.types;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.expresso.Expression;
import org.expresso.ExpressionClass;
import org.expresso.ExpressionType;
import org.expresso.ExpressoParser;
import org.expresso.stream.BranchableStream;

/**
 * An expression type used when only trailing ignorables remain after parsing a structure
 * 
 * @param <S> The type of the stream parsed
 */
public class TrailingIgnorableExpressionType<S extends BranchableStream<?, ?>> extends AbstractExpressionType<S> {
	private final ExpressionClass<? super S> theIgnorableClass;
	private final ExpressionType<? super S> theTargetType;

	/**
	 * @param ignorableClass The ignorable expression class
	 * @param targetType The target expression type parsed
	 */
	public TrailingIgnorableExpressionType(ExpressionClass<? super S> ignorableClass, ExpressionType<? super S> targetType) {
		super(-1);
		theIgnorableClass = ignorableClass;
		theTargetType = targetType;
	}

	/** @return The ignorable expression class */
	public ExpressionClass<? super S> getIgnorableClass() {
		return theIgnorableClass;
	}

	/** @return The target expression type parsed */
	public ExpressionType<? super S> getTargetType() {
		return theTargetType;
	}

	@Override
	public int getEmptyQuality(int minQuality) {
		return 0;
	}

	@Override
	public List<? extends ExpressionType<? super S>> getComponents() {
		return Collections.<ExpressionType<? super S>> unmodifiableList(Arrays.asList(theIgnorableClass, theTargetType));
	}

	@Override
	public <S2 extends S> Expression<S2> parse(ExpressoParser<S2> parser, Expression<S2> lowBound, Expression<S2> highBound)
		throws IOException {
		throw new IllegalStateException("This type does not parse itself");
	}

	@Override
	public int compare(Expression<? extends S> o1, Expression<? extends S> o2) {
		throw new IllegalStateException("This type is not valid for parsing");
	}

	/**
	 * An expression used when only trailing ignorables are left after parsing an expression
	 * 
	 * @param <S> The type of the stream parsed
	 */
	public static class TrailingIgnorableExpression<S extends BranchableStream<?, ?>> extends ComposedExpression<S> {
		private final Expression<S> theTarget;
		private final List<Expression<S>> theIgnorables;

		/**
		 * @param ignorableClass The ignorable expression classs
		 * @param parser The parser that the expression was parsed out of
		 * @param target The content of the parsed expression
		 * @param ignorables The trailing ignorables
		 */
		public TrailingIgnorableExpression(ExpressionClass<? super S> ignorableClass, ExpressoParser<S> parser, Expression<S> target,
			List<Expression<S>> ignorables) {
			super(new TrailingIgnorableExpressionType<>(ignorableClass, target.getType()), parser, buildChildren(target, ignorables));
			theTarget = target;
			theIgnorables = ignorables;
		}

		private static <S extends BranchableStream<?, ?>> List<Expression<S>> buildChildren(Expression<S> target,
			List<Expression<S>> ignorables) {
			List<Expression<S>> children = new ArrayList<>(ignorables.size() + 1);
			children.add(target);
			children.addAll(ignorables);
			return Collections.unmodifiableList(children);
		}

		/** @return The content of the parsed expression */
		public Expression<S> getTarget() {
			return theTarget;
		}

		/** @return The trailing ignorables */
		public List<Expression<S>> getIgnorables() {
			return theIgnorables;
		}

		@Override
		public Expression<S> unwrap() {
			return theTarget.unwrap();
		}

		@Override
		public Expression<S> nextMatch(ExpressoParser<S> parser) throws IOException {
			throw new IllegalStateException("This type does not parse itself");
		}

		@Override
		public Expression<S> nextMatchHighPriority(ExpressoParser<S> parser) throws IOException {
			throw new IllegalStateException("This type does not parse itself");
		}

		@Override
		public Expression<S> nextMatchLowPriority(ExpressoParser<S> parser, Expression<S> limit) throws IOException {
			throw new IllegalStateException("This type does not parse itself");
		}
	}
}
