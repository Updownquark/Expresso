package org.expresso.impl;

import java.util.ArrayList;
import java.util.List;

import org.expresso.ConfiguredExpressionType;
import org.expresso.Expression;
import org.expresso.ExpressionClass;
import org.expresso.ExpressionType;
import org.expresso.stream.BranchableStream;

public class ExpressoStack<S extends BranchableStream<?, ?>> {
	private Frame theTop;

	public Frame getTop() {
		return theTop;
	}

	public Frame push(ExpressionType<? super S> type, S stream) {
		int priority = theTop == null ? 0 : theTop.priority;
		if (type.isEnclosed()) {
			priority = 0;
		} else if (type instanceof ConfiguredExpressionType) {
			ConfiguredExpressionType<S> configured = (ConfiguredExpressionType<S>) type;
			if (configured.getPriority() >= 0 && configured.getPriority() < priority)
				return null;
		} else if (type instanceof ExpressionClass) {
			Frame lastConfigType = theTop;
			while (lastConfigType != null && !(lastConfigType.type instanceof ConfiguredExpressionType))
				lastConfigType = lastConfigType.parent;
			ConfiguredExpressionType<S> configType = lastConfigType == null ? null : (ConfiguredExpressionType<S>) lastConfigType.type;
			if (lastConfigType != null && configType.getExtension((ExpressionClass<S>) type) != null) {
				// Recursion--enforce children having higher priority than the parent
				priority = configType.getPriority();
			}
		}

		return theTop = new Frame(theTop, type, stream, priority);
	}

	@Override
	public String toString() {
		StringBuilder str = new StringBuilder();
		Frame frame = theTop;
		while (frame != null) {
			if (str.length() > 0)
				str.append('\n');
			str.append(frame);
			frame = frame.parent;
		}
		return str.toString();
	}

	public class Frame {
		final Frame parent;
		public final ExpressionType<? super S> type;
		public final S stream;
		private Frame theChild;
		private List<Expression<S>> theInterrupts;
		final int priority;

		public Frame(Frame parent, ExpressionType<? super S> type, S stream, int priority) {
			this.parent = parent;
			if (parent != null)
				parent.theChild = this;
			this.type = type;
			this.stream = stream;
			this.priority = priority;
		}

		public void pop() {
			if (theTop != this)
				throw new IllegalStateException();
			theTop = parent;
			if (parent != null)
				parent.theChild = null;
		}

		public List<Expression<S>> getInterrupted() {
			return theInterrupts;
		}

		public void interrupt(Expression<S> interrupt) {
			Frame frame = theChild;
			while (frame != null) {
				if (frame.theInterrupts == null)
					frame.theInterrupts = new ArrayList<>();
				frame.theInterrupts.add(interrupt);
				frame = frame.theChild;
			}
		}

		@Override
		public String toString() {
			return type + "@" + stream;
		}
	}
}
