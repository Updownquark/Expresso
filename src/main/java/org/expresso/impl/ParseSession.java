package org.expresso.impl;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.expresso.Expression;
import org.expresso.ExpressionType;
import org.expresso.ExpressoGrammar;
import org.expresso.ExpressoParser;
import org.expresso.GrammarExpressionType;
import org.expresso.debug.ExpressoDebugUI;
import org.expresso.debug.ExpressoDebugger;
import org.expresso.stream.BranchableStream;
import org.expresso.types.TrailingIgnorableExpressionType;
import org.qommons.BreakpointHere;

/**
 * Does the work of parsing a stream
 *
 * @param <S> The type of the stream
 */
public class ParseSession<S extends BranchableStream<?, ?>> {
	private static final boolean DEBUG_UI = true;

	private final ExpressoGrammar<? super S> theGrammar;
	private final Map<Integer, ExpressoParser<S>> theParsers;
	// private final Map<Integer, BetterSet<ComponentRecursiveInterrupt<S>>> theStacks;
	// private final Map<Integer, Boolean> theRecursiveCache;
	// private final BetterSet<Integer> theRecursiveVisited;
	private ExpressoStack<S> theStack;
	private ExpressoDebugger theDebugger;
	private int theQualityLevel;

	/** @param grammar The grammar to use to parse expressions */
	public ParseSession(ExpressoGrammar<? super S> grammar) {
		theGrammar = grammar;
		theParsers = new HashMap<>();
		// theStacks = new HashMap<>();
		// theRecursiveCache = new HashMap<>();
		// theRecursiveVisited = BetterHashSet.build().unsafe().buildSet();
		theStack = new ExpressoStack<>();

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
	public Expression<S> parse(S stream, GrammarExpressionType<? super S> component, int minQuality) throws IOException {
		theDebugger.init(theGrammar, stream, component);
		theQualityLevel = 0;
		Expression<S> best = null;
		ExpressoParser<S> parser = getParser(stream, 0);
		roundLoop: for (int round = 1; round < 10; round++) {
			Expression<S> match = parser.parseWith(component, null);
			for (; match != null; match = parser.parseWith(component, match)) {
				if (best == null || match.compareTo(best) < 0) {
					best = match;

					if (!stream.isFullyDiscovered() || best.length() < stream.getDiscoveredLength()) {
						// Account for trailing ignorables
						ExpressoParser<S> ignoreParser = parser.advance(best.length());
						Expression<S> ignoreExp;
						List<Expression<S>> ignorables = null;
						do {
							ignoreExp = null;
							for (GrammarExpressionType<? super S> ig : component.getIgnorables()) {
								ignoreExp = ignoreParser.parseWith(ig, null);
								if (ignoreExp != null) {
									if (ignorables == null)
										ignorables = new LinkedList<>();
									ignorables.add(ignoreExp);
									ignoreParser = ignoreParser.advance(ignoreExp.length());
									break;
								}
							}
						} while (ignoreExp != null);
						if (ignorables != null) {
							best = new TrailingIgnorableExpressionType.TrailingIgnorableExpression<>(parser, best, ignorables);
						}
					}

					if (isSatisfied(best, stream))
						break roundLoop;
				}
			}
			// Clear the cache between rounds, since the quality will affect what should be in the cache
			theParsers.clear();
			// theRecursiveCache.clear();

			theQualityLevel--;
			if (theQualityLevel < minQuality)
				break;

		}
		// TODO Shouldn't be doing this twice, right?
		// if (best != null && (!stream.isFullyDiscovered() || best.length() < stream.getDiscoveredLength())) {
		// // Account for trailing ignorables
		// ExpressoParser<S> ignoreParser = parser.advance(best.length());
		// Expression<S> ignoreExp = ignoreParser.parseWith(ignorable);
		// if (ignoreExp != null) {
		// List<Expression<S>> ignorables = new LinkedList<>();
		// do {
		// ignorables.add(ignoreExp);
		// ignoreParser = ignoreParser.advance(ignoreExp.length());
		// ignoreExp = ignoreParser.parseWith(ignorable);
		// } while (ignoreExp != null);
		// best = new TrailingIgnorableExpressionType.TrailingIgnorableExpression<>(ignorable, parser, best, ignorables);
		// }
		// }
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

	ExpressoStack<S>.Frame push(ExpressionType<? super S> type, S stream) {
		return theStack.push(type, stream);
	}

	// /**
	// * @param type The component to test
	// * @return If the component could possibly use itself as a component
	// */
	// @SuppressWarnings("unused")
	// private boolean isRecursive(ExpressionType<?> type) {
	// if (!cacheRecursive(type))
	// return false;
	// return theRecursiveCache.computeIfAbsent(type.getId(), //
	// id -> isRecursive(type, type, theQualityLevel, true));
	// }
	//
	// private boolean isRecursive(ExpressionType<?> toSearch, ExpressionType<?> target, int minQuality, boolean topLevel) {
	// if (!topLevel && (toSearch == target || Boolean.TRUE.equals(theRecursiveCache.get(toSearch.getId()))))
	// return true;
	// ElementId added = null;
	// if (toSearch.getId() >= 0) {
	// added = CollectionElement.getElementId(theRecursiveVisited.addElement(toSearch.getId(), false));
	// if (added == null)
	// return false; // Found a loop, but not with the expression type we're searching for
	// }
	// try {
	// ExpressionType<?> prevChild = null;
	// for (ExpressionType<?> child : toSearch.getComponents()) {
	// if (prevChild == child)
	// return false;
	// prevChild = child;
	// if (isRecursive(child, target, minQuality, false)) {
	// return true;
	// } else {
	// if (toSearch instanceof SequenceExpressionType) {
	// minQuality -= child.getEmptyQuality(minQuality);
	// if (minQuality > 0)
	// break;
	// }
	// }
	// }
	// return false;
	// } finally {
	// if (added != null)
	// theRecursiveVisited.mutableElement(added).remove();
	// }
	// }
	//
	// private static boolean cacheRecursive(ExpressionType<?> type) {
	// return type.getId() >= 0 || type instanceof GrammarExpressionType;
	// }

	private static boolean isSatisfied(Expression<?> best, BranchableStream<?, ?> stream) {
		return stream.isFullyDiscovered() && best.length() == stream.getDiscoveredLength();
	}

	ExpressoParser<S> getParser(S stream, int advance) throws IOException {
		if (!stream.hasMoreData(advance))
			return null;
		int streamPosition = stream.getPosition() + advance;
		ExpressoParser<S> parser = theParsers.get(streamPosition);
		if (parser == null) {
			parser = new ExpressoParserImpl2<>(this, (S) stream.advance(advance));
			theParsers.put(streamPosition, parser);
		}
		return parser;
	}
}
