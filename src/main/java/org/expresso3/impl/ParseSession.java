package org.expresso3.impl;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.expresso.stream.BranchableStream;
import org.expresso3.Expression;
import org.expresso3.ExpressionType;
import org.expresso3.ExpressoGrammar;
import org.expresso3.ExpressoParser;
import org.expresso3.debug.ExpressoDebugUI;
import org.expresso3.debug.ExpressoDebugger;
import org.qommons.BreakpointHere;

/**
 * Does the work of parsing a stream
 *
 * @param <S> The type of the stream
 */
public class ParseSession<S extends BranchableStream<?, ?>> {
	private static final boolean DEBUG_UI = true;

	@SuppressWarnings("unused") // Not used currently, but seems like it belongs here
	private final ExpressoGrammar<? super S> theGrammar;
	private final Map<ExpressoParserImpl.Template, ExpressoParserImpl<S>> theParsers;
	private ExpressoDebugger theDebugger;
	private int theQualityLevel;

	/** @param grammar The grammar to use to parse expressions */
	public ParseSession(ExpressoGrammar<? super S> grammar) {
		theGrammar = grammar;
		theParsers = new HashMap<>();

		if (DEBUG_UI && BreakpointHere.isDebugEnabled() != null) {
			ExpressoDebugUI debugger = new ExpressoDebugUI();
			setDebugger(debugger);
			debugger.buildFrame();
		} else
			setDebugger(null);
	}

	public int getQualityLevel() {
		return theQualityLevel;
	}

	/**
	 * @param stream The stream whose content to parse
	 * @param component The root component with which to parse the stream
	 * @return The best interpretation of the stream content
	 * @throws IOException If an error occurs reading the stream data
	 */
	public Expression<S> parse(S stream, ExpressionType<? super S> component, int minQuality) throws IOException {
		theDebugger.init(theGrammar, stream, component);
		theQualityLevel = 0;
		Expression<S> best = null;
		ExpressoParser<S> parser = getParser(stream, 0, new int[0]);
		// TrackNode node = ExpressionType.TRACKER.start("parse");
		roundLoop: for (int round = 1; round < 10; round++) {
			Expression<S> match = parser.parseWith(component);
			for (; match != null; match = parser.nextMatch(match)) {
				if (best == null || match.compareTo(best) < 0) {
					best = match;
					if (isSatisfied(best, stream))
						break roundLoop;
				}
			}
			theParsers.clear(); // Clear the cache between rounds
			theQualityLevel--;
		}
		// node.end();
		// ExpressionType.TRACKER.printData();
		// ExpressionType.TRACKER.clear();
		return best;
	}

	public ExpressoDebugger getDebugger() {
		return theDebugger;
	}

	public void setDebugger(ExpressoDebugger debugger) {
		if (debugger != null)
			theDebugger = debugger;
		else
			theDebugger = ExpressoDebugger.IDLE;
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
			parser = new ExpressoParserImpl<>(this, (S) stream.advance(advance), excludedTypes, null);
			theParsers.put(key, parser);
		}
		return parser;
	}

	private static int compareParseStates(ExpressoParserImpl<?> p1, ExpressoParserImpl<?> p2) {
		if (p1 == p2)
			return 0;
		return compareParseStates(p1.getStream().getPosition(), p1.getExcludedTypes(), p2.getStream().getPosition(), p2.getExcludedTypes());
	}

	private static int compareParseStates(int position1, int[] excludedTypes1, int position2, int[] excludedTypes2) {
		int compare = Integer.compare(position1, position2);
		if (compare == 0) {
			int i;
			for (i = 0; i < excludedTypes1.length && i < excludedTypes2.length; i++) {
				if (excludedTypes1[i] != excludedTypes2[i] || excludedTypes1[i] == -1) {
					compare = Integer.compare(excludedTypes1[i], excludedTypes2[i]);
					break;
				}
			}
			if (i == excludedTypes1.length)
				compare = 1;
			else if (i == excludedTypes2.length)
				compare = -1;
		}
		return compare;
	}
}
