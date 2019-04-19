package org.expresso.debug;

import org.expresso.Expression;
import org.expresso.ExpressionType;
import org.expresso.ExpressoGrammar;
import org.expresso.stream.BranchableStream;

/** A debugger to be informed of parsing progress */
public interface ExpressoDebugger {
	/** The method used to obtain a particular expression result */
	enum DebugResultMethod {
		Parsed, UsedCache, RecursiveInterrupt, Excluded
	}

	/**
	 * Returned from {@link ExpressoDebugger#begin(ExpressionType, BranchableStream, Expression)}, must be
	 * {@link #finished(Expression, DebugResultMethod)} when some kind of result is obtained from the parsing
	 */
	interface DebugExpressionParsing {
		void finished(Expression<?> expression, DebugResultMethod methodUsed);

		/** A {@link DebugExpressionParsing} that does nothing */
		DebugExpressionParsing IDLE_PARSING = new DebugExpressionParsing() {
			@Override
			public void finished(Expression<?> expression, DebugResultMethod methodUsed) {}

			@Override
			public String toString() {
				return "Idle";
			}
		};
	}

	/**
	 * Initializes the debugger before parsing
	 * 
	 * @param grammar The grammer to parse with
	 * @param stream The stream content being parsed
	 * @param root The root expression type to parse the content as
	 */
	void init(ExpressoGrammar<?> grammar, BranchableStream<?, ?> stream, ExpressionType<?> root);

	/**
	 * Tells the debugger that the parser is about to try parsing a piece of the stream's content with a particular expression type
	 * 
	 * @param component The expression type to parse with
	 * @param stream The stream content to parse
	 * @param source The expression being branched off of
	 * @return The {@link DebugExpressionParsing} to {@link DebugExpressionParsing#finished(Expression, DebugResultMethod) finish} when some
	 *         result is obtained
	 */
	DebugExpressionParsing begin(ExpressionType<?> component, BranchableStream<?, ?> stream, Expression<?> source);

	/** Suspends the debugger, allowing the user to inspect or control the parsing process stepwise */
	void suspend();

	/** A debugger that does nothing */
	ExpressoDebugger IDLE = new ExpressoDebugger() {
		@Override
		public void init(ExpressoGrammar<?> grammar, BranchableStream<?, ?> stream, ExpressionType<?> root) {}

		@Override
		public DebugExpressionParsing begin(ExpressionType<?> component, BranchableStream<?, ?> stream, Expression<?> source) {
			return DebugExpressionParsing.IDLE_PARSING;
		}

		@Override
		public void suspend() {}

		@Override
		public String toString() {
			return "Idle";
		}
	};
}
