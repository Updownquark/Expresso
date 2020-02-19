package org.expresso.types;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.expresso.Expression;
import org.expresso.ExpressionType;
import org.expresso.ExpressoParser;
import org.expresso.stream.BranchableStream;

/**
 * An expression whose sequence may be present as a component in the stream, but also may be absent without invalidating the composite
 * parent
 *
 * @param <S> The type of the stream
 */
public class OptionalExpressionType<S extends BranchableStream<?, ?>> extends SequenceExpressionType<S> {
	/**
	 * @param id The cache ID for this expression type
	 * @param components The components of the sequence
	 */
	public OptionalExpressionType(int id, List<ExpressionType<S>> components) {
		super(id, components);
	}

	@Override
	public <S2 extends S> Expression<S2> parse(ExpressoParser<S2> parser, Expression<S2> lowBound)
		throws IOException {
		if (lowBound == null)
			return new OptionalPossibility<>(this, parser, Expression.empty(parser.getStream(), this), true);
		OptionalPossibility<S2> low = (OptionalPossibility<S2>) lowBound;
		Expression<S2> superPossibility = super.parse(parser, //
			low.isEmpty() ? null : low.theOption);
		if (superPossibility == null)
			return null;
		return new OptionalPossibility<>(this, parser, superPossibility, false);

		/* TODO For posterity, in case I don't get to come back to this soon, there's an extremely difficult bug here.
		 * 
		 * This code is meant to generate a sequence of all the expression space of the component(s), followed by an empty expression. So
		 * the empty expression is always last, and asking for more content will return null.
		 * 
		 * However, this is causing a problem, specifically (for example) with the qualified-name expression in Java.
		 * Qualified name is defined like so:
		 * 	<expression name="qualified-name"...>
		 * 		<option>
		 * 			<qualified-name field="target" />
		 * 			<literal>.</literal>
		 * 		</option>
		 * 		<identifier field="name" />
		 * </expression>
		 * When the option above is evaluated the first time, it always results in the empty expression, because evaluating qualified-name
		 * recursively is blocked by the parser.  The idea is that when evaluating the next qualified name in the sequence, the option
		 * would attempt the parsing again, in which case the cache would return the previously parsed expression (with empty option),
		 * enabling something like "a.b.c" to be parsed, first as "a", then "a.b", then "a.b.c".
		 * 
		 * But since the first option is empty, re-evaluating just gives null, i.e. no more expressions in the sequence.
		 * 
		 * One solution I thought of is:
		 * * To advertise from the parser that the most recent parsing result was only null as a result of a recursive interrupt,
		 * * then the option could tag its expression with that information,
		 * * then, when evaluating the next expression in the sequence, the option would see the recursive interrupt flag and attempt
		 * to re-evaluate.
		 * 
		 * This solution is not valid though, for several reasons:
		 * 1. If the next expression in the sequence is asked for and the cache has not changed, this results in an infinite loop.
		 * 2. Although I'm first seeing the problem manifest here, all the other composite expressions (one-of, sequence) have the same
		 * vulnerability, where a parse operation may return null, but really it should be re-evaluated when the cache changes.  Just
		 * thinking about the code changes to accommodate this change makes me cringe.
		 * 
		 * Another possible solution:
		 * * Every single expression returned from the parser is wrapped by an expression implementation that has some information about
		 * the types of expressions that were interrupted by recursive parsing from outside the expression type.
		 * * When another element in the sequence returns null, the parser would inspect this information to determine if it's
		 * possible there may have be new elements in the sequence due to new cache entries.  Ideally this should be extremely targeted
		 * (instead of just a stamp or something) for performance's sake.
		 * * If the possibility exists, the parser would then return the start of the sequence to try again.
		 * * An idea that might not be valuable or easy to do is to include further information in expressions from subsequent iterations
		 * through the sequence so that elements that were encountered before do not need to be returned again.
		 * 
		 * TODO At the moment, it seems to me that this is a fundamental problem with the recursive solution that Expresso is intended to be.
		 * If I can't solve it, this project may be fundamentally broken.
		 */
		// if (low != null && low.isEmpty())
		// return null;
		// Expression<S2> superPossibility = super.parse(parser, //
		// (low == null || low.isEmpty()) ? null : low.theOption);
		// boolean empty = superPossibility == null;
		// if (empty)
		// superPossibility = Expression.empty(parser.getStream(), this);
		// return new OptionalPossibility<>(this, parser, superPossibility, empty);
	}

	@Override
	public int compare(Expression<? extends S> o1, Expression<? extends S> o2) {
		Expression<? extends S> opt1 = ((OptionalPossibility<? extends S>) o1).theOption;
		Expression<? extends S> opt2 = ((OptionalPossibility<? extends S>) o2).theOption;
		if (opt1.length() == 0) {
			if (opt2.length() == 0)
				return 0;
			else
				return 1;
		} else if (opt2.length() == 0)
			return -1;
		else
			return super.compare(opt1, opt2);
	}

	@Override
	public int getEmptyQuality(int minQuality) {
		return 0;
	}

	@Override
	public String toString() {
		return "Optional:" + super.toString();
	}

	private static class OptionalPossibility<S extends BranchableStream<?, ?>> extends ComposedExpression<S> {
		private final Expression<S> theOption;
		private final boolean isEmpty;

		OptionalPossibility(OptionalExpressionType<? super S> type, ExpressoParser<S> parser, Expression<S> option, boolean empty) {
			super(type, parser, Arrays.asList(option));
			theOption = option;
			isEmpty = empty;
		}

		OptionalPossibility(OptionalPossibility<S> toCopy, List<Expression<S>> children) {
			super(toCopy, children);
			theOption = toCopy.theOption.unwrap();
			isEmpty = toCopy.isEmpty;
		}

		public boolean isEmpty() {
			return isEmpty;
		}

		@Override
		public OptionalExpressionType<? super S> getType() {
			return (OptionalExpressionType<? super S>) super.getType();
		}

		@Override
		protected Expression<S> copyForChildren(List<Expression<S>> children) {
			return new OptionalPossibility<>(this, children);
		}

		// @Override
		// public Expression<S> nextMatch(ExpressoParser<S> parser) throws IOException {
		// Expression<S> optionNext = parser.nextMatch(theOption);
		// if (optionNext != null)
		// return new OptionalPossibility<>(getType(), parser, optionNext);
		// if (theOption.length() > 0)
		// new OptionalPossibility<>(getType(), parser, Expression.empty(parser.getStream(), theOption.getType()));
		// return null;
		// }
		//
		// @Override
		// public Expression<S> nextMatchHighPriority(ExpressoParser<S> parser) throws IOException {
		// return null;
		// }
		//
		// @Override
		// public Expression<S> nextMatchLowPriority(ExpressoParser<S> parser, Expression<S> limit) throws IOException {
		// return null;
		// }

		@Override
		public StringBuilder print(StringBuilder str, int indent, String metadata) {
			return theOption.print(str, indent, metadata + "(optional)");
		}
	}
}
