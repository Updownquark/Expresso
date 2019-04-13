package org.expresso.debug;

import org.expresso.Expression;
import org.expresso.ExpressionType;
import org.expresso.ExpressoGrammar;
import org.expresso.stream.BranchableStream;

public interface ExpressoDebugger {
	enum DebugResultMethod {
		Parsed, UsedCache, RecursiveInterrupt, Excluded
	}

	interface DebugExpressionParsing {
		void finished(Expression<?> expression, DebugResultMethod methodUsed);

		DebugExpressionParsing IDLE = new DebugExpressionParsing() {
			@Override
			public void finished(Expression<?> expression, DebugResultMethod methodUsed) {}

			@Override
			public String toString() {
				return "Idle";
			}
		};
	}

	void init(ExpressoGrammar<?> grammar, BranchableStream<?, ?> stream, ExpressionType<?> root);

	DebugExpressionParsing begin(ExpressionType<?> component, BranchableStream<?, ?> stream, Expression<?> source);
	
	void suspend();

	ExpressoDebugger IDLE = new ExpressoDebugger() {
		@Override
		public void init(ExpressoGrammar<?> grammar, BranchableStream<?, ?> stream, ExpressionType<?> root) {
		}

		@Override
		public DebugExpressionParsing begin(ExpressionType<?> component, BranchableStream<?, ?> stream, Expression<?> source) {
			return DebugExpressionParsing.IDLE;
		}

		@Override
		public void suspend() {}

		@Override
		public String toString() {
			return "Idle";
		}
	};
}
