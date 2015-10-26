package org.expresso.parse.impl;

import java.io.IOException;
import java.io.Reader;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.expresso.parse.ParseMatcher;
import org.jdom2.Element;
import org.jdom2.JDOMException;

/** Contains methods to initialize a {@link DefaultExpressoParser} from XML documents obeying the default grammar schema */
public class DefaultGrammarParser {
	/** A structure containing a matcher and metadata needed to insert the matcher into a {@link DefaultExpressoParser} correctly */
	public static class PrioritizedMatcher implements Comparable<PrioritizedMatcher> {
		/** The matcher to parse a section of streamed data */
		public final ParseMatcher<CharSequenceStream> matcher;

		final double priority;

		/** Whether the matcher is to be matched by default (when not referred to explicitly by name or tag) */
		public final boolean isDefault;

		PrioritizedMatcher(ParseMatcher<CharSequenceStream> match, double pri, boolean def) {
			matcher = match;
			priority = pri;
			isDefault = def;
		}

		@Override
		public int compareTo(PrioritizedMatcher o) {
			double diff = priority - o.priority;
			if(diff == 0)
				return 0;
			if(diff < 0)
				return 1;
			else
				return -1;
		}
	}

	/**
	 * Parses a set of matchers from an XML document obeying the default grammar schema
	 *
	 * @param xml The XML text stream
	 * @return The prioritized matchers parsed from XML
	 * @throws IOException If the XML text cannot be read from the source
	 * @throws JDOMException If the XML is malformatted
	 */
	public static List<PrioritizedMatcher> getMatchers(Reader xml) throws IOException, JDOMException {
		List<PrioritizedMatcher> ret = new ArrayList<>();
		org.qommons.io.XmlUtils.parseTopLevelElements(xml, element -> {
			String priStr = element.getAttributeValue("priority");
			double priority = priStr == null ? 0 : Double.parseDouble(priStr);
			PrioritizedMatcher matcher = new PrioritizedMatcher(getMatcher(element, true), priority,
				!"false".equalsIgnoreCase(element.getAttributeValue("default")));
			insert(matcher, ret);
		});
		return ret;
	}

	private static void insert(PrioritizedMatcher matcher, List<PrioritizedMatcher> ret) {
		int idx = java.util.Collections.binarySearch(ret, matcher);
		if(idx < 0) {
			ret.add(-(idx + 1), matcher);
		} else {
			while(idx < ret.size() && ret.get(idx).priority == matcher.priority)
				idx++;
			ret.add(idx, matcher);
		}
	}

	/**
	 * Creates and configures a matcher from XML based on the default grammar schema
	 *
	 * @param element The XML element representing the matcher
	 * @param topLevel Whether the element represents a matcher to be added directly to a parser
	 * @return The configured matcher represented by the XML element
	 */
	public static ParseMatcher<CharSequenceStream> getMatcher(Element element, boolean topLevel) {
		String name = element.getAttributeValue("name");
		String tagStr = element.getAttributeValue("tag");
		Set<String> tags = tagStr == null ? Collections.EMPTY_SET
			: Collections.unmodifiableSet(Arrays.stream(tagStr.split(",")).collect(Collectors.toSet()));
		Set<String> attrs = new java.util.LinkedHashSet<>(
			element.getAttributes().stream().map(a -> a.getName()).collect(Collectors.toSet()));
		attrs.remove("name");
		attrs.remove("tag");
		attrs.remove("default");
		attrs.remove("priority");

		// Check some constraints
		if(topLevel) {
			switch (element.getName()) {
			case "whitespace":
			case "ref":
			case "forbid":
			case "option":
				throw new IllegalArgumentException(element.getName() + " may not be used as a top-level matcher");
			case "repeat":
				if(!attrs.contains("min") || Integer.parseInt(element.getAttributeValue("min")) == 0)
					throw new IllegalArgumentException(
						element.getName() + " may not be used as a top-level matcher unless a non-zero min attribute is given");
			}
		}

		switch (element.getName()) {
		case "literal":
		case "pattern":
		case "whitespace":
		case "ref":
			if(!element.getChildren().isEmpty())
				throw new IllegalArgumentException(element.getName() + " does not allow child matchers");
			break;
		case "up-to":
		case "forbid":
			if(element.getChildren().size() != 1)
				throw new IllegalArgumentException(element.getName() + " must have exactly one child matcher");
			break;
		case "one-of":
		case "sequence":
			// if(element.getChildren().size() == 1)
			// System.err.println(element.getName()
			// + " is only useful with more than one child matcher--may be eliminated in the case where there is only one.");
			//$FALL-THROUGH$
		default:
			if(element.getChildren().isEmpty())
				throw new IllegalArgumentException(element.getName() + " expects child matchers");
		}
		switch (element.getName()) {
		case "whitespace":
		case "without":
		case "with":
			if(name != null)
				System.err.println(element.getName() + " does not accept a name attribute");
			if(!tags.isEmpty())
				System.err.println(element.getName() + " does not accept a tag attribute");
		}
		String typeStr = element.getAttributeValue("type");
		int min = 0;
		int max = 0;
		switch (element.getName()) {
		case "without":
		case "with":
			if(typeStr == null)
				throw new IllegalArgumentException(element.getName() + " requires a type attribute");
			//$FALL-THROUGH$
		case "ref": // type attribute is optional for ref
			attrs.remove("type");
			break;
		case "option":
			min = 0;
			max = 1;
			break;
		case "repeat":
			if(attrs.remove("min")) {
				try {
					min = Integer.parseInt(element.getAttributeValue("min"));
				} catch(NumberFormatException e) {
					throw new IllegalArgumentException("Malformatted min attribute: " + element.getAttributeValue("min"), e);
				}
			} else
				min = 0;
			if(attrs.remove("max")) {
				try {
					max = Integer.parseInt(element.getAttributeValue("max"));
				} catch(NumberFormatException e) {
					throw new IllegalArgumentException("Malformatted max attribute: " + element.getAttributeValue("max"), e);
				}
			} else
				max = Integer.MAX_VALUE;
			if(max < min)
				throw new IllegalArgumentException(
					"Maximum (" + max + ") is less than minimum (" + min + ") for a " + element.getName() + " matcher");
			break;
		}
		if(!attrs.isEmpty())
			throw new IllegalArgumentException("Unrecognized attributes for matcher " + element.getName() + ": " + attrs);

		// Constraints all passed. Now create the matcher.

		List<ParseMatcher<CharSequenceStream>> children = new ArrayList<>();
		for(Element child : element.getChildren())
			children.add(getMatcher(child, false));
		children = Collections.unmodifiableList(children);

		switch (element.getName()) {
		case "literal":
			return new TextLiteralMatcher<>(name, tags, element.getTextTrim());
		case "pattern":
			return new TextPatternMatcher<>(name, tags, Pattern.compile(element.getTextTrim()));
		case "whitespace":
			return new WhitespaceMatcher<>();
		case "ref":
			Set<String> include = new java.util.LinkedHashSet<>();
			Set<String> exclude = new java.util.LinkedHashSet<>();
			for(String type : typeStr.split(",")) {
				type = type.trim();
				if(type.startsWith("!"))
					exclude.add(type.substring(1));
				else
					include.add(type);
			}
			return new ReferenceMatcher<>(name, tags, include, exclude);
		case "up-to":
			return new UpToMatcher<>(name, tags, children.get(0));
		case "forbid":
			return new ForbiddenMatcher<>(name, tags, children.get(0));
		}

		final ComposedMatcher.Builder<CharSequenceStream, ? extends ComposedMatcher<CharSequenceStream>> builder;
		switch (element.getName()) {
		case "sequence":
			builder = SequenceMatcher.buildSequence(name);
			break;
		case "without":
			builder = WithoutMatcher.<CharSequenceStream> buildWithout().exclude(typeStr.split(","));
			break;
		case "with":
			builder = WithMatcher.<CharSequenceStream> buildWith().include(typeStr.split(","));
			break;
		case "option":
		case "repeat":
			builder = RepeatingSequenceMatcher.<CharSequenceStream> buildRepeat(name).min(min).max(max);
			break;
		case "one-of": {
			builder = OneOfMatcher.buildOneOf(name);
			break;
		}
		default:
			throw new IllegalArgumentException("Unrecognized matcher name: " + element.getName());
		}
		builder.tag(tags.toArray(new String[tags.size()]));
		for(ParseMatcher<CharSequenceStream> child : children)
			builder.addChild(child);
		return builder.build();
	}
}
