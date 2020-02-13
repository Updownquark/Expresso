package org.expresso.impl;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.expresso.Expression;
import org.expresso.ExpressionType;
import org.expresso.ExpressoParser;
import org.expresso.debug.ExpressoDebugger.DebugExpressionParsing;
import org.expresso.debug.ExpressoDebugger.DebugResultMethod;
import org.expresso.stream.BranchableStream;

public class ExpressoParserImpl2<S extends BranchableStream<?, ?>> implements ExpressoParser<S> {
	private final ParseSession<S> theSession;
	private final S theStream;
	private final Map<Integer, CachedExpression<S>> theCache;
	private final Set<Integer> theRecursiveFilter;

	ExpressoParserImpl2(ParseSession<S> session, S stream) {
		theSession = session;
		theStream = stream;
		theCache = new HashMap<>();
		theRecursiveFilter = new TreeSet<>();
	}

	@Override
	public S getStream() {
		return theStream;
	}

	@Override
	public int getQualityLevel() {
		return theSession.getQualityLevel();
	}

	@Override
	public ExpressoParser<S> advance(int spaces) throws IOException {
		if (spaces == 0)
			return this;
		else
			return theSession.getParser(theStream, spaces);
	}

	@Override
	public Expression<S> parseWith(ExpressionType<? super S> type, Expression<S> lowBound, Expression<S> highBound) throws IOException {
		DebugExpressionParsing debug = theSession.getDebugger().begin(type, theStream, null);
		Expression<S> result = null;
		DebugResultMethod method = null;
		try {
			if (lowBound instanceof CachedExpression && ((CachedExpression<S>) lowBound).hasNext) {
				method = DebugResultMethod.UsedCache;
				return result = ((CachedExpression<S>) lowBound).getNext();
			} else if (highBound instanceof CachedExpression && ((CachedExpression<S>) highBound).hasPrevious) {
				method = DebugResultMethod.UsedCache;
				return result = ((CachedExpression<S>) highBound).getPrevious();
			}
		} finally {
			if (method != null)
				debug.finished(result, method);
		}

		int id = type.getId();
		ExpressoStack<S>.Frame frame = theSession.push(type, theStream);
		if (frame == null) {
			debug.finished(null, DebugResultMethod.RecursiveInterrupt);
			return null;
		}
		try {
			if (id >= 0) {
				if (type.isCacheable() && lowBound == null && highBound == null) {
					CachedExpression<S> cached = theCache.get(id);
					if (cached != null) {
						method = DebugResultMethod.UsedCache;
						return getReturn(cached);
					}
				}
				if (lowBound instanceof CachedExpression) {
					if (((CachedExpression<S>) lowBound).parsingNext != null) {
						((CachedExpression<S>) lowBound).parsingNext.interrupt();
						method = DebugResultMethod.RecursiveInterrupt;
						return null;
					}
				} else if (highBound instanceof CachedExpression) {
					if (((CachedExpression<S>) highBound).parsingPrevious != null) {
						((CachedExpression<S>) highBound).parsingPrevious.interrupt();
						method = DebugResultMethod.RecursiveInterrupt;
						return null;
					}
				} else if (!theRecursiveFilter.add(type.getId())) {
					method = DebugResultMethod.RecursiveInterrupt;
					return null;
				}
			}

			method = DebugResultMethod.Parsed;
			try {
				if (lowBound instanceof CachedExpression)
					((CachedExpression<S>) lowBound).parsingNext = frame;
				else if (highBound instanceof CachedExpression)
					((CachedExpression<S>) highBound).parsingPrevious = frame;
				result = type.parse(//
					this, unwrap(lowBound), unwrap(highBound));
				if (id >= 0 && type.isCacheable() && !frame.isInterrupted()) {
					CachedExpression<S> cached = new CachedExpression<>(result);
					if (lowBound instanceof CachedExpression || highBound instanceof CachedExpression)
						cached.setPrevious(lowBound);
					if (highBound instanceof CachedExpression)
						cached.setNext(highBound);
					if (lowBound == null && highBound == null)
						theCache.put(id, cached);
					return getReturn(cached);
				}
				return result;
			} finally {
				if (lowBound instanceof CachedExpression)
					((CachedExpression<S>) lowBound).parsingNext = null;
				else if (highBound instanceof CachedExpression)
					((CachedExpression<S>) highBound).parsingPrevious = null;
				if (id >= 0)
					theRecursiveFilter.remove(id);
			}
		} catch (RuntimeException e) {
			theSession.getDebugger().suspend();
			method = null;
			throw e;
		} finally {
			frame.pop();
			if (method != null)
				debug.finished(result, method);
		}
	}

	private static <S extends BranchableStream<?, ?>> Expression<S> unwrap(Expression<S> ex) {
		if (ex instanceof CachedExpression)
			return ((CachedExpression<S>) ex).theWrapped;
		else
			return ex;
	}

	static <S extends BranchableStream<?, ?>> Expression<S> getReturn(Expression<S> ex) {
		if (ex instanceof CachedExpression && ((CachedExpression<S>) ex).theWrapped == null)
			return null;
		else
			return ex;
	}

	@Override
	public String toString() {
		return theStream.toString();
	}

	private static class CachedExpression<S extends BranchableStream<?, ?>> implements Expression<S> {
		final Expression<S> theWrapped;
		boolean hasPrevious;
		ExpressoStack<S>.Frame parsingPrevious;
		private CachedExpression<S> thePrevious;
		boolean hasNext;
		ExpressoStack<S>.Frame parsingNext;
		private CachedExpression<S> theNext;

		CachedExpression(Expression<S> wrapped) {
			theWrapped = wrapped;
		}

		CachedExpression<S> setPrevious(Expression<S> previous) {
			hasPrevious = true;
			if (previous != null) {
				thePrevious = (CachedExpression<S>) previous;
				thePrevious.hasNext = true;
				thePrevious.theNext = this;
			}
			return this;
		}

		CachedExpression<S> setNext(Expression<S> next) {
			hasNext = true;
			if (next != null) {
				theNext = (CachedExpression<S>) next;
				theNext.hasPrevious = true;
				theNext.thePrevious = this;
			}
			return this;
		}

		Expression<S> getPrevious() {
			return getReturn(thePrevious);
		}

		Expression<S> getNext() {
			return getReturn(theNext);
		}

		@Override
		public ExpressionType<? super S> getType() {
			return theWrapped.getType();
		}

		@Override
		public S getStream() {
			return theWrapped.getStream();
		}

		@Override
		public int length() {
			return theWrapped.length();
		}

		@Override
		public List<? extends Expression<S>> getChildren() {
			return theWrapped.getChildren();
		}

		@Override
		public int getErrorCount() {
			return theWrapped.getErrorCount();
		}

		@Override
		public Expression<S> getFirstError() {
			return theWrapped.getFirstError();
		}

		@Override
		public int getLocalErrorRelativePosition() {
			return theWrapped.getLocalErrorRelativePosition();
		}

		@Override
		public String getLocalErrorMessage() {
			return theWrapped.getLocalErrorMessage();
		}

		@Override
		public Expression<S> unwrap() {
			return theWrapped.unwrap();
		}

		@Override
		public int getMatchQuality() {
			return theWrapped.getMatchQuality();
		}

		@Override
		public StringBuilder print(StringBuilder str, int indent, String metadata) {
			return theWrapped.print(str, indent, metadata);
		}

		@Override
		public int hashCode() {
			return theWrapped.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == this)
				return true;
			else if (obj instanceof CachedExpression)
				obj = ((CachedExpression<?>) obj).theWrapped;
			return theWrapped.equals(obj);
		}

		@Override
		public String toString() {
			return theWrapped.toString();
		}
	}
}
