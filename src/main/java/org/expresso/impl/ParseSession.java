package org.expresso.impl;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.expresso.*;
import org.expresso.debug.ExpressoDebugUI;
import org.expresso.debug.ExpressoDebugger;
import org.expresso.impl.ExpressoParserImpl.ComponentRecursiveInterrupt;
import org.expresso.stream.BranchableStream;
import org.expresso.types.TrailingIgnorableExpressionType;
import org.qommons.BreakpointHere;
import org.qommons.collect.BetterHashSet;
import org.qommons.collect.BetterSet;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.ElementId;

/**
 * Does the work of parsing a stream
 *
 * @param <S> The type of the stream
 */
public class ParseSession<S extends BranchableStream<?, ?>> {
	private static final boolean DEBUG_UI = false;

	private final ExpressoGrammar<? super S> theGrammar;
	private final Map<ExpressoParserImpl.Template, ExpressoParserImpl<S>> theParsers;
	private final Map<Integer, BetterSet<ComponentRecursiveInterrupt<S>>> theStacks;
	private final Map<Integer, Boolean> theRecursiveCache;
	private final BetterSet<Integer> theRecursiveVisited;
	private ExpressoDebugger theDebugger;
	private int theQualityLevel;

	/** @param grammar The grammar to use to parse expressions */
	public ParseSession(ExpressoGrammar<? super S> grammar) {
		theGrammar = grammar;
		theParsers = new HashMap<>();
		theStacks = new HashMap<>();
		theRecursiveCache = new HashMap<>();
		theRecursiveVisited = BetterHashSet.build().unsafe().buildSet();

		if (DEBUG_UI && BreakpointHere.isDebugEnabled() != null) {
			ExpressoDebugUI debugger = new ExpressoDebugUI();
			setDebugger(debugger);
			debugger.buildFrame();
		} else
			setDebugger(null);
	}

	/** @return The current minimum quality level for matches parsed in this session */
	public int getQualityLevel() {
		return theQualityLevel;
	}

	/**
	 * @param stream The stream whose content to parse
	 * @param component The root component with which to parse the stream
	 * @param minQuality The minimum quality of the match to produce
	 * @return The best interpretation of the stream content
	 * @throws IOException If an error occurs reading the stream data
	 */
	public Expression<S> parse(S stream, ExpressionType<? super S> component, int minQuality) throws IOException {
		theDebugger.init(theGrammar, stream, component);
		theQualityLevel = 0;
		Expression<S> best = null;
		// ExpressionClass<? super S> ignorable = theGrammar.getExpressionClasses().get(DefaultGrammarParser.IGNORABLE);
		// if (ignorable != null) { // Account for ignorables at the end of the content
		// component = new SequenceExpressionType<>(-1,
		// Collections.<ExpressionType<? super S>> unmodifiableList(Arrays.<ExpressionType<? super S>> asList(component, //
		// new OptionalExpressionType<>(-1,
		// Arrays.asList(new RepeatExpressionType<>(-1, 0, Integer.MAX_VALUE, Arrays.asList(ignorable)))))));
		// }
		ExpressoParser<S> parser = getParser(stream, 0, new int[0]);
		roundLoop: for (int round = 1; round < 10; round++) {
			Expression<S> match = parser.parseWith(component);
			for (; match != null; match = parser.nextMatch(match)) {
				if (best == null || match.compareTo(best) < 0) {
					best = match;
					if (isSatisfied(best, stream))
						break roundLoop;
				}
			}
			// Clear the cache between rounds
			theParsers.clear();
			theRecursiveCache.clear();

			theQualityLevel--;
			if (theQualityLevel < minQuality)
				break;

		}
		if (best != null && (!stream.isFullyDiscovered() || best.length() < stream.getDiscoveredLength())) {
			// Account for trailing ignorables
			ExpressionClass<? super S> ignorable = theGrammar.getExpressionClasses().get(DefaultGrammarParser.IGNORABLE);
			ExpressoParser<S> ignoreParser = parser.advance(best.length());
			Expression<S> ignoreExp = ignoreParser.parseWith(ignorable);
			if (ignoreExp != null) {
				List<Expression<S>> ignorables = new LinkedList<>();
				do {
					ignorables.add(ignoreExp);
					ignoreParser = ignoreParser.advance(ignoreExp.length());
					ignoreExp = ignoreParser.parseWith(ignorable);
				} while (ignoreExp != null);
				best = new TrailingIgnorableExpressionType.TrailingIgnorableExpression<>(ignorable, parser, best, ignorables);
			}
		}
		return best;
	}

	/** @return The debugger being used to track parsing progress */
	public ExpressoDebugger getDebugger() {
		return theDebugger;
	}

	/** @param debugger The debugger to use to track parsing progress */
	public void setDebugger(ExpressoDebugger debugger) {
		if (debugger != null)
			theDebugger = debugger;
		else
			theDebugger = ExpressoDebugger.IDLE;
	}

	public boolean isRecursive(ExpressionType<?> type) {
		if (type.getId() < 0 || !(type instanceof GrammarExpressionType))
			return false;
		return theRecursiveCache.computeIfAbsent(type.getId(), //
			id -> isRecursive(type, type, theQualityLevel));
	}

	private boolean isRecursive(ExpressionType<?> toSearch, ExpressionType<?> target, int minQuality) {
		if (toSearch == target || Boolean.TRUE.equals(theRecursiveCache.get(toSearch.getId())))
			return true;
		ElementId added = null;
		if (toSearch.getId() >= 0) {
			added = CollectionElement.getElementId(theRecursiveVisited.addElement(toSearch.getId(), false));
			if (added == null)
				return false; // Found a loop, but not with the expression type we're searching for
		}
		try {
			for (ExpressionType<?> child : toSearch.getComponents()) {
				if (isRecursive(child, target, minQuality))
					return true;
				else {
					minQuality -= child.getEmptyQuality(minQuality);
					if (minQuality > 0)
						break;
				}
			}
			return false;
		} finally {
			if (added != null)
				theRecursiveVisited.mutableElement(added).remove();
		}
	}

	private static boolean isSatisfied(Expression<?> best, BranchableStream<?, ?> stream) {
		return stream.isFullyDiscovered() && best.length() == stream.getDiscoveredLength();
	}

	ExpressoParser<S> getParser(S stream, int advance, int[] excludedTypes) throws IOException {
		if (!stream.hasMoreData(advance))
			return null;
		int streamPosition = stream.getPosition() + advance;
		ExpressoParserImpl.Template key = new ExpressoParserImpl.Template(streamPosition, excludedTypes);
		ExpressoParserImpl<S> parser = theParsers.get(key);
		if (parser == null) {
			parser = new ExpressoParserImpl<>(this, (S) stream.advance(advance), excludedTypes, //
				theStacks.computeIfAbsent(streamPosition, s -> BetterHashSet.build().unsafe().buildSet()));
			theParsers.put(key, parser);
		}
		return parser;
	}
}
