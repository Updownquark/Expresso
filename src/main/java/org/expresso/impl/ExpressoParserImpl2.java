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
			return new ExpressoParserImpl2<>(theSession, (S) theStream.advance(spaces));
	}

	@Override
	public Expression<S> parseWith(ExpressionType<? super S> type, Expression<S> lowBound, Expression<S> highBound) throws IOException {
		DebugExpressionParsing debug = theSession.getDebugger().begin(type, theStream, null);
		Expression<S> result = null;
		try {
			if (lowBound instanceof CachedExpression && ((CachedExpression<S>) lowBound).theNext != null)
				return result = ((CachedExpression<S>) lowBound).theNext;
			else if (highBound instanceof CachedExpression && ((CachedExpression<S>) highBound).thePrevious != null)
				return result = ((CachedExpression<S>) highBound).thePrevious;
		} finally {
			if (result != null)
				debug.finished(result, DebugResultMethod.UsedCache);
		}

		int id = type.getId();
		if (!theSession.push(type)) {
			debug.finished(null, DebugResultMethod.RecursiveInterrupt);
			return null;
		}
		DebugResultMethod method = null;
		try {
			if (id >= 0) {
				if (lowBound == null && highBound == null && type.isCacheable()) {
					CachedExpression<S> cached = theCache.get(id);
					if (cached != null) {
						method = DebugResultMethod.UsedCache;
						return cached;
					}
				}
				if (!theRecursiveFilter.add(type.getId())) {
					method = DebugResultMethod.RecursiveInterrupt;
					return null;
				}
			}

			method = DebugResultMethod.Parsed;
			try {
				Expression<S> parsed = type.parse(//
					this, unwrap(lowBound), unwrap(highBound));
				if (parsed == null)
					return null;
				if (id >= 0 && type.isCacheable()) {
					CachedExpression<S> cached = new CachedExpression<>(parsed);
					if (lowBound instanceof CachedExpression)
						cached.setPrevious(lowBound);
					if (highBound instanceof CachedExpression)
						cached.setNext(highBound);
					if (lowBound == null && highBound == null)
						theCache.put(id, cached);
					return cached;
				} else
					return parsed;
			} finally {
				if (id >= 0)
					theRecursiveFilter.remove(id);
			}
		} catch (RuntimeException e) {
			theSession.getDebugger().suspend();
			method = null;
			throw e;
		} finally {
			theSession.pop();
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

	@Override
	public String toString() {
		return theStream.toString();
	}

	private static class CachedExpression<S extends BranchableStream<?, ?>> implements Expression<S> {
		final Expression<S> theWrapped;
		CachedExpression<S> thePrevious;
		CachedExpression<S> theNext;

		CachedExpression(Expression<S> wrapped) {
			theWrapped = wrapped;
		}

		CachedExpression<S> setPrevious(Expression<S> previous) {
			thePrevious = (CachedExpression<S>) previous;
			thePrevious.theNext = this;
			return this;
		}

		CachedExpression<S> setNext(Expression<S> previous) {
			theNext = (CachedExpression<S>) previous;
			theNext.thePrevious = this;
			return this;
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
