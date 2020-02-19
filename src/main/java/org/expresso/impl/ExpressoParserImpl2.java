package org.expresso.impl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.expresso.Expression;
import org.expresso.ExpressionType;
import org.expresso.ExpressoParser;
import org.expresso.debug.ExpressoDebugger.DebugExpressionParsing;
import org.expresso.debug.ExpressoDebugger.DebugResultMethod;
import org.expresso.stream.BranchableStream;

public class ExpressoParserImpl2<S extends BranchableStream<?, ?>> implements ExpressoParser<S> {
	private final ParseSession<S> theSession;
	private final S theStream;
	private final Map<Integer, CachedExpressionSequence<S>> theCache;

	ExpressoParserImpl2(ParseSession<S> session, S stream) {
		theSession = session;
		theStream = stream;
		theCache = new HashMap<>();
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
	public Expression<S> parseWith(ExpressionType<? super S> type, Expression<S> lowBound) throws IOException {
		DebugExpressionParsing debug = theSession.getDebugger().begin(type, theStream, null);

		int id = type.getId();
		ExpressoStack<S>.Frame frame = theSession.push(type, theStream);
		if (frame == null) {
			debug.finished(null, DebugResultMethod.RecursiveInterrupt);
			return null;
		}

		DebugResultMethod[] method = new DebugResultMethod[1];
		Expression<S> result = null;
		try {
			if (id >= 0) {
				CachedExpression<S> cachedLow = (CachedExpression<S>) lowBound;
				CachedExpressionSequence<S> cache;
				if (cachedLow != null)
					cache = cachedLow.theSequence;
				else
					cache = theCache.computeIfAbsent(id, __ -> new CachedExpressionSequence<>(type));
				result = parseCached(cache, cachedLow, frame, method);
			} else {
				method[0] = DebugResultMethod.Parsed;
				result = type.parse(this, lowBound);
			}
			return result;
		} catch (RuntimeException e) {
			theSession.getDebugger().suspend();
			method[0] = null;
			throw e;
		} finally {
			frame.pop();
			if (method[0] != null)
				debug.finished(result, method[0]);
		}
	}

	private Expression<S> parseCached(CachedExpressionSequence<S> cache, CachedExpression<S> lowBound, ExpressoStack<S>.Frame frame,
		DebugResultMethod[] method) throws IOException {
		while (lowBound != null && !lowBound.isValid())
			lowBound = lowBound.thePrevious;

		ExpressoStack<S>.Frame interruptFrame;
		Expression<S> unwrappedLow;
		CachedExpression<S> cached;
		if (lowBound != null) {
			interruptFrame = lowBound.parsingNext;
			unwrappedLow = lowBound.theWrapped;
			cached = lowBound.theNext;
		} else {
			interruptFrame = cache.parsingStart;
			unwrappedLow = null;
			cached = cache.theStart;
		}

		if (cached != null) {
			method[0] = DebugResultMethod.UsedCache;
			if (interruptFrame != null)
				interruptFrame.interrupt(cached);
		} else if (interruptFrame != null) {
			method[0] = DebugResultMethod.RecursiveInterrupt;
			cached = new CachedExpression<>(cache, null, lowBound, null);
			interruptFrame.interrupt(cached);
		} else {
			method[0] = DebugResultMethod.Parsed;
			if (lowBound != null)
				lowBound.parsingNext = frame;
			else
				cache.parsingStart = frame;
			Expression<S> uncached = cache.theType.parse(this, unwrappedLow);
			/* I've found a problem with the currently implemented solution to the cache-poisoning solution I implemented to solve the
			 * problem documented in OptionalExpression.
			 * 
			 * E.g. parsing java.util.Arrays.asList() with
			 * <result-producer>
			 * 		...
			 * 		<field-ref>
			 * 			<one-of>
			 * 				<result-producer>
			 * 				<type>
			 *			</one-of>
			 *			<literal>.</literal>
			 *			<identifier />
			 *		</field-ref>
			 * <result-producer>
			 * 
			 * It's difficult to explain, but the one-of's match with a result-producer can be poisoned by the root, which causes a cycle.
			 * 
			 * The next 2 lines were an attempt to mitigate this problem, but the sequence can be cyclic, in which case this still breaks
			 */
			if (uncached != null && unwrappedLow != null && equal(unwrappedLow, uncached))
				uncached = cache.theType.parse(this, uncached);// Cache poisoning can cause this issue
			cached = new CachedExpression<>(cache, uncached, lowBound, frame.getInterrupted());
			if (lowBound != null)
				lowBound.parsingNext = interruptFrame;
			else
				cache.parsingStart = interruptFrame;
		}

		return getReturn(cached);
	}

	static <S extends BranchableStream<?, ?>> Expression<S> getReturn(CachedExpression<S> ex) {
		if (ex.theWrapped == null)
			return null;
		else
			return ex;
	}

	static boolean equal(Expression<?> ex1, Expression<?> ex2) {
		if (ex1.getType() != ex2.getType()//
			|| ex1.getStream().getPosition() != ex2.getStream().getPosition()//
			|| ex1.getChildren().size() != ex2.getChildren().size())
			return false;
		for (int i = 0; i < ex1.getChildren().size(); i++)
			if (!equal(ex1.getChildren().get(i), ex2.getChildren().get(i)))
				return false;
		return true;
	}

	@Override
	public String toString() {
		return theStream.toString();
	}

	private static class CachedExpressionSequence<S extends BranchableStream<?, ?>> {
		final ExpressionType<? super S> theType;
		ExpressoStack<S>.Frame parsingStart;
		CachedExpression<S> theStart;

		CachedExpressionSequence(ExpressionType<? super S> type) {
			theType = type;
		}
	}

	private static boolean USE_INTERRUPTS = true;

	private static class CachedExpression<S extends BranchableStream<?, ?>> implements Expression<S> {
		final CachedExpressionSequence<S> theSequence;
		final Expression<S> theWrapped;
		final CachedExpression<S> thePrevious;
		boolean trimmed;
		ExpressoStack<S>.Frame parsingNext;
		CachedExpression<S> theNext;
		private List<CachedExpression<S>> theReferences;

		CachedExpression(CachedExpressionSequence<S> sequence, Expression<S> wrapped, CachedExpression<S> previous,
			List<Expression<S>> interrupts) {
			theSequence = sequence;
			theWrapped = wrapped;

			CachedExpression<S> replaced;
			if (previous != null) {
				thePrevious = previous;
				replaced = thePrevious.theNext;
				thePrevious.theNext = this;
			} else {
				thePrevious = null;
				replaced = theSequence.theStart;
				theSequence.theStart = this;
			}
			if (USE_INTERRUPTS) {
				if (replaced != null)
					replaced.trim();

				if (interrupts != null)
					for (Expression<S> interrupt : interrupts)
						((CachedExpression<S>) interrupt).addReference(this);
			}
		}

		boolean isValid() {
			return !trimmed;
		}

		private CachedExpression<S> trim() {
			trimmed = true;
			if (theNext != null)
				theNext.trim();
			theNext = null;
			if (thePrevious != null) {
				if (thePrevious.theNext == this)
					thePrevious.theNext = null;
			} else {
				if (theSequence.theStart == this)
					theSequence.theStart = null;
			}
			if (theReferences != null) {
				for (CachedExpression<S> reference : theReferences)
					reference.trim();
			}
			return this;
		}

		private void addReference(CachedExpression<S> reference) {
			if (theReferences == null)
				theReferences = new ArrayList<>();
			theReferences.add(reference);
		}

		@Override
		public ExpressionType<? super S> getType() {
			return theSequence.theType;
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
			return theWrapped == null ? "(empty)" : theWrapped.toString();
		}
	}
}
