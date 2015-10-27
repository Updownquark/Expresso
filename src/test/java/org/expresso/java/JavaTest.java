package org.expresso.java;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;

import javax.swing.UIDefaults;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

import org.expresso.parse.ParseMatch;
import org.expresso.parse.impl.CharSequenceStream;
import org.expresso.parse.impl.DefaultExpressoParser;
import org.expresso.parse.impl.DefaultGrammarParser;
import org.expresso.parse.impl.DefaultGrammarParser.PrioritizedMatcher;
import org.expresso.parse.impl.WhitespaceMatcher;
import org.jdom2.JDOMException;
import org.junit.Before;
import org.junit.Test;

/** Tests the capabilities of the java parsing */
public class JavaTest {
	private DefaultExpressoParser<CharSequenceStream> theParser;

	/** Sets up the java parser */
	@Before
	public void setupParser() {
		try {
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		} catch(ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException e) {
			System.err.println("Unable to set system L&F");
			e.printStackTrace();
		}
		UIDefaults uiDefs = UIManager.getDefaults();
		uiDefs.put("Tree.leftChildIndent", 1);

		List<PrioritizedMatcher> matchers;
		try {
			matchers = DefaultGrammarParser.getMatchers(
				new InputStreamReader(DefaultGrammarParser.class.getResourceAsStream("/org/expresso/java/Grammar.xml"), "UTF-8"));
		} catch(IOException | JDOMException e) {
			throw new IllegalStateException("Could not setup default java parsing", e);
		}

		DefaultExpressoParser.Builder<CharSequenceStream> builder = DefaultExpressoParser.build("Java");
		builder.addMatcher(new WhitespaceMatcher<>(), false);
		for(PrioritizedMatcher matcher : matchers)
			builder.addMatcher(matcher.matcher, matcher.isDefault);
		theParser = builder.build();
		org.expresso.parse.debug.ExpressoParserDebugGUI<CharSequenceStream> debugger;
		debugger = new org.expresso.parse.debug.ExpressoParserDebugGUI<>();
		theParser.setDebugger(debugger);
		org.expresso.parse.debug.ExpressoParserDebugGUI.getDebuggerFrame(debugger);
	}

	/** Parses all the .java files in this project */
	@Test
	public void parseExpressoClasses() {
		File current = new File(System.getProperty("user.dir"));
		if(!current.getName().equals("Expresso"))
			throw new IllegalStateException("Expected working directory to be the root of the Expresso project");
		parseClasses(new File(current, "src/main/java"));
		parseClasses(new File(current, "src/test/java"));
	}

	private void parseClasses(File dir) {
		for(File file : dir.listFiles()) {
			if(file.isDirectory())
				continue;
			System.out.println("Parsing " + file);
			ParseMatch<CharSequenceStream> match;
			try {
				match = theParser.parseByType(CharSequenceStream.from(file, 4096), null, "java-file");
			} catch(IOException e) {
				throw new IllegalStateException("Could not read " + file, e);
			}
			if(match == null)
				System.err.println("Could not parse " + file);
			else if(match.getError() != null)
				System.err.println("Error parsing " + file + ": " + match.getError());
		}
		for(File file : dir.listFiles())
			if(file.isDirectory())
				parseClasses(file);
	}
}
