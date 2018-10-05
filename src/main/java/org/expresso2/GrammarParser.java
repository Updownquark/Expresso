package org.expresso2;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import org.expresso.parse.BranchableStream;

public interface GrammarParser<S extends BranchableStream<?, ?>> {
	ExpressoGrammar<S> parseGrammar(String name, InputStream stream) throws IOException;

	default ExpressoGrammar<S> parseGrammar(URL url) throws IOException {
		try (InputStream stream = url.openStream()) {
			return parseGrammar(url.toString(), stream);
		}
	}
}
