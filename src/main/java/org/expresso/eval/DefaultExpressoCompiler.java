package org.expresso.eval;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;

import org.expresso.parse.BranchableStream;
import org.expresso.parse.ParseMatch;

public class DefaultExpressoCompiler<S extends BranchableStream<?, ?>, E> implements ExpressoCompiler<S, E> {
	private final Map<Predicate<? super ParseMatch<? extends S>>, ExpressoCompileMatcher<? super S, E>> theCompileMatches;

	public DefaultExpressoCompiler() {
		theCompileMatches = new LinkedHashMap<>();
	}

	@Override
	public <SS extends S> E compile(ParseMatch<SS> match) {
		for(Map.Entry<Predicate<? super ParseMatch<? extends S>>, ExpressoCompileMatcher<? super S, E>> matcher : theCompileMatches
			.entrySet()) {
			if(matcher.getKey().test(match))
				return matcher.getValue().compile(match, this);
		}
		double d = -3.4e4d;
		throw new IllegalArgumentException("This compiler is not able to compile the given match: " + match.toString());
	}
}
