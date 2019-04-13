package org.expresso.impl;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.expresso.Expression;
import org.expresso.ExpressionType;
import org.expresso.ExpressoGrammar;
import org.expresso.ExpressoParser;
import org.expresso.debug.ExpressoDebugUI;
import org.expresso.debug.ExpressoDebugger;
import org.expresso.stream.BranchableStream;
import org.qommons.BreakpointHere;

/**
 * Does the work of parsing a stream
 *
 * @param <S> The type of the stream
 */
public class ParseSession<S extends BranchableStream<?, ?>> {
	private static final boolean DEBUG_UI = true;

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
	 * @param minQuality The minimum quality of the match to produce
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
			if (theQualityLevel < minQuality)
				break;
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
}
