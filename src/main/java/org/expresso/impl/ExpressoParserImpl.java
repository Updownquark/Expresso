package org.expresso.impl;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.expresso.Expression;
import org.expresso.ExpressionType;
import org.expresso.ExpressoParser;
import org.expresso.GrammarExpressionType;
import org.expresso.debug.ExpressoDebugger.DebugExpressionParsing;
import org.expresso.debug.ExpressoDebugger.DebugResultMethod;
import org.expresso.stream.BranchableStream;
import org.qommons.collect.BetterSet;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.ElementId;

/**
 * Default implementation of {@link ExpressoParser}
 *
 * @param <S> The type of the stream to parse
 */
public class ExpressoParserImpl<S extends BranchableStream<?, ?>> implements ExpressoParser<S> {
	/** A hash key representing a parser of this type */
	public static class Template {
		final int position;
		final int[] excludedTypes;

		/**
		 * @param position The position in the stream
		 * @param excludedTypes The types excluded from parsing
		 */
		public Template(int position, int[] excludedTypes) {
			this.position = position;
			this.excludedTypes = excludedTypes;
		}

		@Override
		public int hashCode() {
			int hash = position;
			if (excludedTypes != null)
				for (int et : excludedTypes)
					hash = hash ^ et;
			return hash;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			else if (!(obj instanceof Template))
				return false;
			Template other = (Template) obj;
			return position == other.position && Arrays.equals(excludedTypes, other.excludedTypes);
		}
	}

	private final ParseSession<S> theSession;
	private final S theStream;
	private final int[] theExcludedTypes;
	private final BetterSet<ComponentRecursiveInterrupt<S>> theStack;
	private final Map<Integer, CachedExpression<S>> theCache;

	/**
	 * @param session The parsing session
	 * @param stream The stream (with position)
	 * @param excludedTypes IDs of types that this parser will not return expressions for
	 * @param stack The expression stack for this position
	 */
	public ExpressoParserImpl(ParseSession<S> session, S stream, int[] excludedTypes, BetterSet<ComponentRecursiveInterrupt<S>> stack) {
		theSession = session;
		theStream = stream;
		theExcludedTypes = excludedTypes;
		theStack = stack;
		theCache = new HashMap<>();
	}

	@Override
	public S getStream() {
		return theStream;
	}

	/** @return The IDs of the types that this parser will not return expressions for */
	public int[] getExcludedTypes() {
		return theExcludedTypes;
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
			return theSession.getParser(theStream, spaces, theExcludedTypes);
	}

	@Override
	public ExpressoParser<S> exclude(int... expressionIds) {
		int[] union = union(theExcludedTypes, expressionIds);
		if (union == theExcludedTypes)
			return this;
		else {
			try {
				// theSession.debug(() -> "Excluding " + Arrays.toString(expressionIds));
				return theSession.getParser(theStream, 0, union);
			} catch (IOException e) {
				throw new IllegalStateException("Should not happen--not advancing stream", e);
			}
		}
	}

	class StackPushResult {
		private ComponentRecursiveInterrupt<S> rootFrame;
		final CollectionElement<ComponentRecursiveInterrupt<S>> frame;
		final Expression<S> interrupt;

		StackPushResult(CollectionElement<ComponentRecursiveInterrupt<S>> frame, //
			ComponentRecursiveInterrupt<S> rootFrame) {
			this.frame = frame;
			this.rootFrame = rootFrame;
			this.interrupt = null;
		}

		StackPushResult(Expression<S> interrupt) {
			this.interrupt = interrupt;
			this.frame = null;
		}

		void pop() {
			if (frame == null)
				return;
			theStack.mutableElement(frame.getElementId()).remove();
			if (rootFrame != null) {
				rootFrame.leaf = frame.get().parent;
				rootFrame.numDownstream--;
			}
		}

		boolean isRoot() {
			return frame != null && frame.get().parent == null;
		}
	}

	StackPushResult pushOnStack(ExpressionType<? super S> component, Expression<S> recursiveInterrupt, boolean interruptible,
		boolean interrupting) {
		CollectionElement<ComponentRecursiveInterrupt<S>> stackFrame = null;
		boolean[] added = new boolean[1];
		stackFrame = theStack.getOrAdd(new ComponentRecursiveInterrupt<>(component, interrupting, recursiveInterrupt, 0), false,
			() -> added[0] = true);
		if (added[0]) {
			stackFrame.get().element = stackFrame.getElementId();
			return new StackPushResult(stackFrame, null);
		}
		if (interruptible) {
			ComponentRecursiveInterrupt<S> interruptingCRI = stackFrame.get().leaf;
			while (interruptingCRI != null && !interruptingCRI.interrupting)
				interruptingCRI = interruptingCRI.parent;
			if (interruptingCRI != null) { // Interrupted
				Expression<S> ri = interruptingCRI.result;
				// The interrupting frame should itself be interrupted unless it is top level and the interrupt expression is null
				if (interruptingCRI.parent != null || ri != null)
					interruptingCRI.wasInterrupted = true;
				// Interrupts everything downstream of the interrupting frame
				stackFrame = theStack.getAdjacentElement(interruptingCRI.element, true);
				while (stackFrame != null) {
					stackFrame.get().wasInterrupted = true;
					stackFrame = theStack.getAdjacentElement(stackFrame.getElementId(), true);
				}
				return new StackPushResult(ri);
			}
		}
		ComponentRecursiveInterrupt<S> newCRI = new ComponentRecursiveInterrupt<>(component, interrupting, recursiveInterrupt,
			++stackFrame.get().numDownstream);
		newCRI.parent = stackFrame.get().leaf;
		CollectionElement<ComponentRecursiveInterrupt<S>> lowStackFrame = theStack.addElement(newCRI, false);
		newCRI.element = lowStackFrame.getElementId();
		stackFrame.get().leaf = newCRI;
		return new StackPushResult(lowStackFrame, stackFrame.get());
	}

	@Override
	public Expression<S> parseWith(ExpressionType<? super S> component, Expression<S> lowBound, Expression<S> highBound)
		throws IOException {
		return parseWith(component, lowBound, highBound, null);
	}

	Expression<S> parseWith(ExpressionType<? super S> component, Expression<S> lowBound, Expression<S> highBound,
		Expression<S> recursiveInterrupt) throws IOException {
		DebugExpressionParsing debug = theSession.getDebugger().begin(component, theStream, null);
		boolean recursive = component instanceof GrammarExpressionType;
		StackPushResult stackFrame = null;
		Expression<S> result;
		DebugResultMethod method;
		CachedExpression<S> cached = null;
		try {
			int cacheId = component.getId();
			if (cacheId < 0) {
				result = component.parse(this, unwrap(lowBound), unwrap(highBound));
				method = DebugResultMethod.Parsed;
			} else if (theExcludedTypes != null && Arrays.binarySearch(theExcludedTypes, cacheId) >= 0) {
				result = null;
				method = DebugResultMethod.Excluded;
			} else {
				if (component.isCacheable() || recursive)
					stackFrame = pushOnStack(component, recursiveInterrupt, lowBound == null && highBound == null, true);

				if (stackFrame != null && stackFrame.frame == null) {
					result = stackFrame.interrupt;
					method = DebugResultMethod.RecursiveInterrupt;
				} else if (lowBound instanceof BranchingMatch) {
					result = ((BranchingMatch<S>) lowBound).nextMatch(this, unwrap(highBound));
					method = DebugResultMethod.Parsed;
					// } else if (recursiveInterrupt != null) {
					// result = component.parse(this, unwrap(lowBound), unwrap(highBound));
					// while (result != null && !find(result, recursiveInterrupt))
					// result = result.nextMatch(this);
					// method = DebugResultMethod.Parsed;
				} else/* if (!component.isCacheable()) */ {
					result = component.parse(this, unwrap(lowBound), unwrap(highBound));
					method = DebugResultMethod.Parsed;
				} /*else if(lowBound instanceof CachedExpression) {
					CachedExpression<S> cachedLow=(CachedExpression<S>) lowBound;
					if(cachedLow.hasNextMatch())
						
					} else{
					boolean[] newCache = new boolean[1];
					cached = theCache.computeIfAbsent(cacheId, k -> {
						newCache[0] = true;
						return new CachedExpression<>(component);
					});
					if (newCache[0]) {
						result = component.parse(this, unwrap(lowBound), unwrap(highBound));
						method = DebugResultMethod.Parsed;
						if (stackFrame.frame.get().wasInterrupted) {
							theCache.remove(cacheId);
							cached = null;
						}
					} else {
						result = cached.asPossibility();
						cached = null; // Don't cache again below
						method = DebugResultMethod.UsedCache;
					}
					}*/
			}
		} catch (RuntimeException e) {
			theSession.getDebugger().suspend();
			throw e;
		} finally {
			if (stackFrame != null)
				stackFrame.pop();
		}
		if (result != null && !(result instanceof BranchingMatch) && recursive && method == DebugResultMethod.Parsed)
			result = new BranchingMatch<>(null, result, null, 0);
		if (cached != null)
			result = cached.setPossibility(result).asPossibility();
		debug.finished(result, method);
		return result;
	}

	private static <S extends BranchableStream<?, ?>> Expression<S> unwrap(Expression<S> ex) {
		if (ex instanceof CachedExpression)
			ex = ((CachedExpression<S>) ex).thePossibility;
		if (ex instanceof BranchingMatch)
			ex = ((BranchingMatch<S>) ex).theMatch;
		return ex;
	}

	@Override
	public Expression<S> nextMatch(Expression<S> expression) throws IOException {
		DebugExpressionParsing debug = theSession.getDebugger().begin(expression.getType(), theStream, expression);
		StackPushResult stackFrame = null;
		try {
			int cacheId = expression.getType().getId();
			if (cacheId < 0) {
				Expression<S> result = expression.nextMatch(this);
				debug.finished(result, DebugResultMethod.Parsed);
				return result;
			} else if (theExcludedTypes != null && Arrays.binarySearch(theExcludedTypes, cacheId) >= 0) {
				debug.finished(null, DebugResultMethod.Excluded);
				return null;
			}
			if (expression.getType().isCacheable() || expression instanceof BranchingMatch)
				stackFrame = pushOnStack(expression.getType(), null, false);
			DebugResultMethod method;
			if (expression instanceof CachedExpression && ((CachedExpression<?>) expression).hasNextMatch())
				method = DebugResultMethod.UsedCache;
			else
				method = DebugResultMethod.Parsed;
			Expression<S> match = expression.nextMatch(this);
			if (stackFrame != null && stackFrame.isRoot() && !stackFrame.frame.get().wasInterrupted && method == DebugResultMethod.Parsed) {
				if (expression instanceof CachedExpression)
					match = ((CachedExpression<S>) expression).cacheNext(match);
			}
			debug.finished(match, method);
			return match;
		} catch (RuntimeException e) {
			theSession.getDebugger().suspend();
			throw e;
		} finally {
			if (stackFrame != null)
				stackFrame.pop();
		}
	}

	@Override
	public Expression<S> nextMatchHighPriority(Expression<S> expression) throws IOException {
		return nextMatchDifferentPriority(expression, null, null, false);
	}

	@Override
	public Expression<S> nextMatchLowPriority(Expression<S> expression, Expression<S> limit) throws IOException {
		return nextMatchDifferentPriority(expression, limit, null, false);
	}

	Expression<S> nextMatchDifferentPriority(Expression<S> expression, Expression<S> limit, Expression<S> recursiveInterrupt,
		boolean highPriority)
		throws IOException {
		DebugExpressionParsing debug = theSession.getDebugger().begin(expression.getType(), theStream, expression);
		StackPushResult stackFrame = null;
		try {
			int cacheId = expression.getType().getId();
			if (cacheId < 0) {
				Expression<S> result;
				if (highPriority)
					result = expression.nextMatchHighPriority(this);
				else
					result = expression.nextMatchLowPriority(this, limit);
				debug.finished(result, DebugResultMethod.Parsed);
				return result;
			} else if (theExcludedTypes != null && Arrays.binarySearch(theExcludedTypes, cacheId) >= 0) {
				debug.finished(null, DebugResultMethod.Excluded);
				return null;
			}
			stackFrame = pushOnStack(expression.getType(), recursiveInterrupt, false);
			DebugResultMethod method;
			if (limit == null && expression instanceof CachedExpression && ((CachedExpression<?>) expression).hasDiffPriMatch(highPriority))
				method = DebugResultMethod.UsedCache;
			else
				method = DebugResultMethod.Parsed;
			Expression<S> match;
			if (highPriority)
				match = expression.nextMatchHighPriority(this);
			else
				match = expression.nextMatchLowPriority(this, limit);
			while (match != null && recursiveInterrupt != null && !find(match, recursiveInterrupt)) {
				Expression<S> next = match;
				do {
					next = next.nextMatch(this);
				} while (next != null && !find(next, recursiveInterrupt));
				if (next != null) {
					match = next;
					break;
				} else if (highPriority)
					match = match.nextMatchLowPriority(this, expression);
				else
					match = match.nextMatchLowPriority(this, limit);
			}
			if (limit == null && stackFrame != null && stackFrame.isRoot() && !stackFrame.frame.get().wasInterrupted
				&& method == DebugResultMethod.Parsed) {
				if (expression instanceof CachedExpression)
					match = ((CachedExpression<S>) expression).cacheNextDiffPriority(match, highPriority);
			}
			debug.finished(match, method);
			return match;
		} catch (RuntimeException e) {
			theSession.getDebugger().suspend();
			throw e;
		} finally {
			if (stackFrame != null)
				stackFrame.pop();
		}
	}

	private static int[] union(int[] i1, int[] i2) {
		int[] newIs = null;
		int i = 0, j = 0, r = 0;
		while (i < i1.length && j < i2.length) {
			if (i1[i] == i2[j]) {
				if (newIs != null)
					newIs[r] = i1[i];
				i++;
				j++;
				r++;
			} else if (i1[i] < i2[j]) {
				if (newIs == null) {
					// Assume all the remaining elements are different
					newIs = new int[i1.length + i2.length - 1];
					System.arraycopy(i1, 0, newIs, 0, i);
					Arrays.fill(newIs, i, newIs.length, -1);// Fill the rest of the array with -1s
				}
				newIs[r++] = i1[i++];
			} else {
				if (newIs == null) {
					// Assume all the remaining elements are different
					newIs = new int[i1.length + i2.length - 1];
					System.arraycopy(i1, 0, newIs, 0, i);
					Arrays.fill(newIs, i, newIs.length, -1);// Fill the rest of the array with -1s
				}
				newIs[r++] = i2[j++];
			}
		}
		if (i < i1.length) {
			if (newIs == null)
				return i1;
			System.arraycopy(i1, i, newIs, r, i1.length - i);
		} else if (j < i2.length) {
			if (newIs == null)
				return i2;
			System.arraycopy(i2, j, newIs, r, i2.length - j);
		}
		if (newIs != null)
			return newIs;
		else
			return i1; // Arrays were the same
	}

	@Override
	public String toString() {
		return theStream.toString();
	}

	/** Takes care of recursive branching for recursive expression types */
	static class BranchingMatch<S extends BranchableStream<?, ?>> implements Expression<S> {
		private final BranchingMatch<S> theSource;
		private final Expression<S> theMatch;
		private final Expression<S> theInvariantContent;
		private final int theSourceStage;
		private boolean isInvariant;

		public BranchingMatch(BranchingMatch<S> source, Expression<S> match, Expression<S> invariantContent, int sourceStage) {
			theSource = source;
			theMatch = match;
			theInvariantContent = invariantContent;
			theSourceStage = sourceStage;
		}

		@Override
		public ExpressionType<? super S> getType() {
			return theMatch.getType();
		}

		@Override
		public S getStream() {
			return theMatch.getStream();
		}

		@Override
		public int length() {
			return theMatch.length();
		}

		@Override
		public List<? extends Expression<S>> getChildren() {
			return theMatch.getChildren();
		}

		Expression<S> nextMatch(ExpressoParserImpl<S> parser, Expression<S> highBound) throws IOException {
			if (isInvariant)
				return null;
			// First, try to branch the component itself
			// TODO I don't remember or understand why exactly I pushed to the stack here, since the parser's nextMatch already did it
			// but when I take it out, things go much slower. Something to do with caching, I'm guessing.
			ExpressoParserImpl<S>.StackPushResult stackFrame = parser.pushOnStack(theMatch.getType(), null, false, false);
			Expression<S> next;
			next = theMatch.getType().parse(parser, theMatch, highBound);
			while (next != null && theInvariantContent != null && !find(next, theInvariantContent))
				next = next.nextMatch(parser);
			stackFrame.pop();
			if (next != null)
				return new BranchingMatch<>(this, next, theInvariantContent, 2);

			return nextMatch2(parser, highBound);
		}

		@Override
		public Expression<S> nextMatch(ExpressoParser<S> parser) throws IOException {
			if (isInvariant)
				return null;
			ExpressoParserImpl<S> p = (ExpressoParserImpl<S>) parser;
			// First, try to branch the component itself
			// TODO I don't remember or understand why exactly I pushed to the stack here, since the parser's nextMatch already did it
			// but when I take it out, things go much slower. Something to do with caching, I'm guessing.
			ExpressoParserImpl<S>.StackPushResult stackFrame = p.pushOnStack(theMatch.getType(), null, false);
			Expression<S> next;
			next = theMatch.nextMatch(parser);
			while (next != null && theInvariantContent != null && !find(next, theInvariantContent))
				next = next.nextMatch(parser);
			stackFrame.pop();
			if (next != null)
				return new BranchingMatch<>(this, next, theInvariantContent, 2);

			return nextMatch2(p);
		}

		private Expression<S> nextMatch2(ExpressoParserImpl<S> parser, Expression<S> highBound) throws IOException {
			Expression<S> next;
			// Next, parse other matches, using this match for the initial content
			// Only do this and the rest at the top level, but don't let the caller cache results when they're interrupted like this
			ExpressoParserImpl<S>.StackPushResult stackFrame = parser.pushOnStack(getType(), this, true, true);
			// boolean proceed = stackFrame.frame != null && stackFrame.frame.get().parent.parent == null;
			boolean proceed = stackFrame.frame != null && stackFrame.frame.get().parent == null;
			stackFrame.pop();
			if (!proceed)
				return null; // Only do the rest at the top level
			isInvariant = true;
			next = parser.parseWith(theMatch.getType(), null, theMatch, this);
			// next = parser.nextMatchDifferentPriority(theMatch, null, this, true);
			isInvariant = false;
			if (next != null)
				return new BranchingMatch<>(this, next, this, 4);

			return nextMatch3(parser, highBound);
		}

		private Expression<S> nextMatch3(ExpressoParserImpl<S> parser, Expression<S> highBound) throws IOException {
			isInvariant = true;
			Expression<S> next = parser.parseWith(theMatch.getType(), theMatch, highBound, this);
			// Expression<S> next = parser.nextMatchDifferentPriority(theMatch, null, this, false);
			isInvariant = false;
			if (next != null)
				return new BranchingMatch<>(this, next, this, 4);

			return nextMatch4(parser, highBound);
		}

		private Expression<S> nextMatch4(ExpressoParserImpl<S> parser, Expression<S> highBound) throws IOException {
			if (theSource != null) {
				Expression<S> next;
				switch (theSourceStage) {
				case 2:
					next = theSource.nextMatch2(parser, highBound);
					break;
				case 3:
					next = theSource.nextMatch3(parser, highBound);
					break;
				case 4:
					next = theSource.nextMatch4(parser, highBound);
					break;
				default:
					throw new IllegalStateException("There is no stage " + theSourceStage + "!");
				}
				return next; // No need to wrap with a reference back to this as the source, because all the stages are done.
			} else
				return null;
		}

		@Override
		public Expression<S> nextMatchHighPriority(ExpressoParser<S> parser) throws IOException {
			return getType().parse(parser);
		}

		@Override
		public Expression<S> nextMatchLowPriority(ExpressoParser<S> parser, Expression<S> limit) throws IOException {
			return null;
		}

		@Override
		public int getErrorCount() {
			return theMatch.getErrorCount();
		}

		@Override
		public Expression<S> getFirstError() {
			return theMatch.getFirstError();
		}

		@Override
		public int getLocalErrorRelativePosition() {
			return theMatch.getLocalErrorRelativePosition();
		}

		@Override
		public String getLocalErrorMessage() {
			return theMatch.getLocalErrorMessage();
		}

		@Override
		public Expression<S> unwrap() {
			return theMatch.unwrap();
		}

		@Override
		public int getMatchQuality() {
			return theMatch.getMatchQuality();
		}

		@Override
		public StringBuilder print(StringBuilder str, int indent, String metadata) {
			return theMatch.print(str, indent, metadata);
		}

		@Override
		public String toString() {
			return theMatch.toString();
		}
	}

	static boolean find(Expression<?> ancestor, Expression<?> descendant) {
		if (ancestor == descendant)
			return true;
		for (Expression<?> child : ancestor.getChildren()) {
			if (find(child, descendant))
				return true;
			else if (child.length() > 0)
				return false;
		}
		return false;
	}

	static class ComponentRecursiveInterrupt<S extends BranchableStream<?, ?>> {
		public ComponentRecursiveInterrupt<S> parent;
		final ExpressionType<? super S> type;
		final boolean interrupting;
		ElementId element;
		final int depth;
		ComponentRecursiveInterrupt<S> leaf;
		int numDownstream;
		final Expression<S> result;
		boolean wasInterrupted;

		public ComponentRecursiveInterrupt(ExpressionType<? super S> type, boolean interruptible, Expression<S> result, int depth) {
			this.type = type;
			this.interrupting = interruptible;
			this.depth = depth;
			this.result = result;
			leaf = this;
		}

		@Override
		public int hashCode() {
			int hash = type.getId();
			if (depth > 0)
				hash = (hash << 8) | depth;
			return hash;
		}

		@Override
		public boolean equals(Object obj) {
			if (depth > 0)
				return false;
			return obj instanceof ComponentRecursiveInterrupt && ((ComponentRecursiveInterrupt<?>) obj).type == type;
		}

		@Override
		public String toString() {
			return type + "->" + result;
		}
	}
}
