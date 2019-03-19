package org.expresso2.impl;

import java.io.IOException;

import org.expresso.parse.BranchableStream;
import org.expresso2.Expression;
import org.expresso2.ExpressionComponent;
import org.expresso2.ExpressionPossibility;
import org.expresso2.ExpressoGrammar;
import org.expresso2.ExpressoParser;
import org.qommons.collect.BetterSortedSet;
import org.qommons.tree.BetterTreeSet;

public class ParseSession<S extends BranchableStream<?, ?>> {
	private final ExpressoGrammar<? super S> theGrammar;
	private final BetterTreeSet<ExpressoParserImpl<S>> theParsers;

	public ParseSession(ExpressoGrammar<? super S> grammar) {
		theGrammar = grammar;
		theParsers = new BetterTreeSet<>(false, ParseSession::compareParseStates);
	}

	public Expression<S> parse(S stream, ExpressionComponent<? super S> component, boolean bestError)
		throws IOException {
		ExpressionPossibility<S> sequence = getParser(stream, 0, new int[0]).parseWith(component);
		BetterSortedSet<ExpressionPossibility<S>> possibilities = new BetterTreeSet<>(false, ParseSession::comparePossibilities);
		possibilities.add(sequence);
		ExpressionPossibility<S> bestComplete = null;
		while (true) {
			ExpressionPossibility<S> nextBest = possibilities.pollFirst();
			if (nextBest == null)
				break;
			ExpressionPossibility<S> branch;
			if (nextBest.isComplete() && (bestError || nextBest.getErrorCount() == 0)) {
				if (bestComplete == null || comparePossibilities(nextBest, bestComplete) < 0)
					bestComplete = nextBest;
				branch = nextBest.advance();
				if (branch != null)
					possibilities.add(branch);
			}
			branch = nextBest.rightFork();
			if (branch != null)
				possibilities.add(branch);
			branch = nextBest.leftFork();
			if (branch != null)
				possibilities.add(branch);
		}
		if (bestComplete != null)
			return bestComplete.getExpression();
		else if (possibilities.isEmpty())
			throw new IllegalStateException("No possibilities?!");
		else
			return possibilities.getFirst().getExpression();
	}

	ExpressoParser<S> getParser(S stream, int advance, int[] excludedTypes) throws IOException {
		int streamPosition = stream.getPosition() + advance;
		ExpressoParserImpl<S> parser = theParsers.searchValue(
			p -> compareParseStates(p.getStream().getPosition(), p.getExcludedTypes(), streamPosition, excludedTypes),
			BetterSortedSet.SortedSearchFilter.OnlyMatch);
		if (parser == null) {
			parser = new ExpressoParserImpl<>(this, (S) stream.advance(advance), excludedTypes);
			theParsers.add(parser);
		}
		return parser;
	}

	private static int comparePossibilities(ExpressionPossibility<?> p1, ExpressionPossibility<?> p2) {
		if (p1.getErrorCount() != p2.getErrorCount())
			return p1.getErrorCount() - p2.getErrorCount();
		return p2.length() - p1.length();
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
				if (excludedTypes1[i] != excludedTypes2[i] || excludedTypes1[i] == -1)
					break;
			}
			if (i < excludedTypes1.length && excludedTypes1[i] != -1)
				compare = 1;
			else if (i < excludedTypes2.length && excludedTypes2[i] != -1)
				compare = -1;
			else
				compare = excludedTypes1[i] - excludedTypes2[i];
		}
		return compare;
	}
}
