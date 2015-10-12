package org.expresso.eval;

import java.util.List;
import java.util.function.Predicate;

import org.expresso.parse.ParseMatch;

public class DefaultExpressoCompiler implements ExpressoCompiler {
	private static class CompilerElement {
		final Predicate<? super ParseMatch<?>> pattern;
		final ExpressoCompiler compiler;

		public CompilerElement(Predicate<? super ParseMatch<?>> o, ExpressoCompiler c) {
			pattern = o;
			compiler = c;
		}
	}

	private final List<CompilerElement> theElements;

}
