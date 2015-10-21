package org.expresso.parse.impl;

import java.io.IOException;
import java.io.Reader;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.expresso.parse.ParseMatcher;
import org.jdom2.Element;
import org.jdom2.JDOMException;

public class DefaultGrammarParser {
	public static class PrioritizedMatcher implements Comparable<PrioritizedMatcher> {
		public final ParseMatcher<CharSequenceStream> matcher;

		final double priority;

		public final boolean isDefault;

		PrioritizedMatcher(ParseMatcher<CharSequenceStream> matcher, double priority, boolean def) {
			this.matcher = matcher;
			this.priority = priority;
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

	public static List<PrioritizedMatcher> getMatchers(Reader xml) throws IOException, JDOMException {
		List<PrioritizedMatcher> ret = new ArrayList<>();
		org.qommons.io.XmlUtils.parseTopLevelElements(xml, element -> {
			String priStr = element.getAttributeValue("priority");
			double priority = priStr == null ? 0 : Double.parseDouble(priStr);
			PrioritizedMatcher matcher = new PrioritizedMatcher(getMatcher(element), priority,
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

	public static ParseMatcher<CharSequenceStream> getMatcher(Element element) {
		String name=element.getAttributeValue("name");
		String tagStr=element.getAttributeValue("tag");
		Set<String> tags=tagStr==null ? Collections.EMPTY_SET : Collections.unmodifiableSet(Arrays.stream(tagStr.split(",")).collect(Collectors.toSet()));
		String typeStr = element.getAttributeValue("type");
		switch(element.getName()){
		case "literal":
			return new TextLiteralMatcher<>(name, tags, element.getTextTrim());
		case "pattern":
			return new TextPatternMatcher<>(name, tags, Pattern.compile(element.getTextTrim()));
		case "whitespace":
			return new WhitespaceMatcher<>();
		case "ref":
			return new ReferenceMatcher<>(name, tags, typeStr == null ? new String[0] : typeStr.split(","));
		}
		List<ParseMatcher<CharSequenceStream>> children = new ArrayList<>();
		for(Element child : element.getChildren())
			children.add(getMatcher(child));
		children = Collections.unmodifiableList(children);

		if(children.isEmpty())
			throw new IllegalArgumentException(element.getName() + " expects child matchers");
		// Check some constraints
		switch (element.getName()) {
		case "up-to":
		case "forbid":
			if(children.size() != 1)
				throw new IllegalArgumentException(element.getName() + " must have exactly one child matcher");
			break;
		case "without":
		case "with":
			if(typeStr == null)
				throw new IllegalArgumentException(element.getName() + " requires a type attribute");
			break;
		case "one-of":
		case "sequence":
			if(children.size() == 1)
				System.err.println(element.getName()
					+ " is only useful with more than one child matcher--may be eliminated in the case where there is only one.");
		}

		switch (element.getName()) {
		case "up-to":
			return new UpToMatcher<>(name, tags, children.get(0));
		case "forbid":
			return new ForbiddenMatcher<>(name, tags, children.get(0));
		case "sequence": {
			SequenceMatcher.Builder<CharSequenceStream, SequenceMatcher<CharSequenceStream>> builder = SequenceMatcher.buildSequence(name);
			builder.tag(tags.toArray(new String[tags.size()]));
			for(ParseMatcher<CharSequenceStream> child : children)
				builder.addChild(child);
			return builder.build();
		}
		case "without": {
			WithoutMatcher.Builder<CharSequenceStream> builder = WithoutMatcher.buildWithout();
			builder.tag(tags.toArray(new String[tags.size()]));
			for(ParseMatcher<CharSequenceStream> child : children)
				builder.addChild(child);
			return builder.build();
		}
		case "with": {
			WithMatcher.Builder<CharSequenceStream> builder = WithMatcher.buildWith();
			builder.tag(tags.toArray(new String[tags.size()]));
			for(ParseMatcher<CharSequenceStream> child : children)
				builder.addChild(child);
			return builder.build();
		}
		case "option":
		case "repeat":
		case "one-of":
		default:
			throw new IllegalArgumentException("Unrecognized matcher name: "+element.getName());
		}
	}
}
