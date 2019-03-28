package org.expresso;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

import org.expresso.stream.BinarySequenceStream;
import org.expresso.stream.BranchableStream;
import org.expresso.stream.CharSequenceStream;
import org.expresso.types.ExcludeExpressionType;
import org.expresso.types.ForbidExpressionType;
import org.expresso.types.LeadUpExpressionType;
import org.expresso.types.OneOfExpressionType;
import org.expresso.types.OptionalExpressionType;
import org.expresso.types.RepeatExpressionType;
import org.expresso.types.SequenceExpressionType;
import org.expresso.types.TextLiteralExpressionType;
import org.expresso.types.TextPatternExpressionType;
import org.qommons.IntList;
import org.qommons.QommonsUtils;
import org.qommons.collect.BetterCollections;
import org.qommons.collect.BetterSortedMap;
import org.qommons.collect.BetterSortedSet;
import org.qommons.config.QommonsConfig;
import org.qommons.tree.BetterTreeMap;
import org.qommons.tree.BetterTreeSet;
import org.qommons.tree.SortedTreeList;

/**
 * A default implementation of {@link ExpressoGrammarParser}
 *
 * @param <S> The super-type of streams this grammar parser can generate grammars for
 */
public class DefaultGrammarParser<S extends BranchableStream<?, ?>> implements ExpressoGrammarParser<S> {
	/** Reserved names which configured expressions and classes cannot use */
	public static final Set<String> RESERVED_EXPRESSION_NAMES = Collections.unmodifiableSet(new LinkedHashSet<>(//
		Arrays.asList("expresso", "expression", "class", "field", "name")));

	/** A grammar precursor, used by some grammar components to generate {@link ExpressionType}s */
	public interface PreGrammar {
		/**
		 * @param typeName The name of the
		 * @return The ID of the type with the given name
		 * @throws IllegalArgumentException
		 */
		int getTypeId(String typeName) throws IllegalArgumentException;
	}

	/**
	 * A component that knows how to generate {@link ExpressionType}s from configuration
	 *
	 * @param <S> The super type of streams that the component can generate expression types for
	 */
	public interface GrammarComponent<S extends BranchableStream<?, ?>> {
		/**
		 * Generates an {@link ExpressionType} from configuration
		 * 
		 * @param id The ID for the new expression
		 * @param config The attribute configuration for the component
		 * @param value The value configuration for the component
		 * @param untrimmedValue The untrimmed value configuration for the component
		 * @param children The children for the component
		 * @param grammar The precursor for the grammar using this component
		 * @return The built and configured {@link ExpressionType}
		 * @throws IllegalArgumentException If the configuration for the expression has an error
		 */
		ExpressionType<S> build(int id, Map<String, String> config, String value, String untrimmedValue, List<ExpressionType<S>> children,
			PreGrammar grammar) throws IllegalArgumentException;
	}

	private final Map<String, GrammarComponent<S>> theComponents;

	/**
	 * Creates a grammar parser
	 * 
	 * @param recognizedComponents The components for the grammar
	 */
	public DefaultGrammarParser(Map<String, GrammarComponent<S>> recognizedComponents) {
		theComponents = recognizedComponents;
	}

	@Override
	public ExpressoGrammar<S> parseGrammar(String name, InputStream stream) throws IOException {
		QommonsConfig config = QommonsConfig.fromXml(QommonsConfig.getRootElement(stream));
		if (!config.getName().equals("expresso"))
			throw new IllegalArgumentException("expresso expected as root, not " + config.getName());
		int[] id = new int[1];
		class PreParsedClass {
			final ExpressionClass<S> clazz;
			final List<String> memberNames;
			private final List<ConfiguredExpressionType<S>> types;

			PreParsedClass(String className) {
				types = new SortedTreeList<>(false, (t1, t2) -> -Integer.compare(t1.getPriority(), t2.getPriority()));
				memberNames = new LinkedList<>();
				clazz = new ExpressionClass<>(id[0]++, className, Collections.unmodifiableList(types));
			}

			ExpressionClass<S> addTypeName(String typeName) {
				memberNames.add(typeName);
				return clazz;
			}

			ExpressionClass<S> addType(ConfiguredExpressionType<S> type) {
				types.add(type);
				return clazz;
			}
		}
		Map<String, PreParsedClass> declaredClasses = new LinkedHashMap<>();
		Map<String, ParsedExpressionType<S>> declaredTypes = new LinkedHashMap<>();
		// One run-though to populate the type references
		for (QommonsConfig type : config.subConfigs()) {
			if (!type.getName().equals("expression"))
				throw new IllegalArgumentException("expression elements expected, not " + type.getName());
			String typeName = type.get("name");
			if (typeName == null)
				throw new IllegalArgumentException("expression has no name: " + type);
			else if (RESERVED_EXPRESSION_NAMES.contains(typeName))
				throw new IllegalArgumentException(typeName + " cannot be used as an expression name in expresso");
			else if (theComponents.containsKey(typeName))
				throw new IllegalArgumentException(typeName + " is a reserved component type name: " + type);
			else if (declaredTypes.containsKey(typeName))
				throw new IllegalArgumentException(typeName + " has already been declared as an expression: " + type);
			else if (declaredClasses.containsKey(typeName))
				throw new IllegalArgumentException(typeName + " has already been declared as a class: " + type);
			String classesStr = type.get("class");
			BetterSortedSet<ExpressionClass<S>> classes;
			if (classesStr != null) {
				String[] classesSplit = classesStr.split(",");
				classes = new BetterTreeSet<>(false, ExpressionClass::compareTo);
				for (String clazz : classesSplit) {
					classes.add(declaredClasses.computeIfAbsent(clazz.trim(), c -> {
						if (theComponents.containsKey(c))
							throw new IllegalArgumentException(c + " is a reserved component type name: " + type);
						else if (declaredTypes.containsKey(c))
							throw new IllegalArgumentException(c + " has already been declared as an expression: " + type);
						return new PreParsedClass(c);
					}).addTypeName(typeName));
				}
				classes = BetterCollections.unmodifiableSortedSet(classes);
			} else
				classes = BetterSortedSet.empty(ExpressionClass::compareTo);
			int priority = type.getInt("priority", 0);
			if (declaredTypes.put(typeName, new ParsedExpressionType<>(id[0]++, priority, typeName, classes)) != null)
				throw new IllegalArgumentException("Duplicate expressions named " + typeName);
		}
		BetterSortedMap<String, ExpressionClass<S>> classes = new BetterTreeMap<>(false, QommonsUtils.DISTINCT_NUMBER_TOLERANT);
		for (PreParsedClass clazz : declaredClasses.values())
			classes.put(clazz.clazz.getName(), clazz.clazz);
		PreGrammar preGrammar = new PreGrammar() {
			@Override
			public int getTypeId(String typeName) {
				ParsedExpressionType<S> type = declaredTypes.get(typeName);
				if (type != null)
					return type.id;
				PreParsedClass clazz = declaredClasses.get(typeName);
				if (clazz != null)
					return clazz.clazz.id;
				throw new IllegalArgumentException("Unrecognized type name: " + typeName);
			}
		};
		// Second run-through to initialize all the types
		for (QommonsConfig type : config.subConfigs()) {
			ParsedExpressionType<S> typeRef = declaredTypes.get(type.get("name"));
			QommonsConfig[] componentConfigs = type.subConfigs();
			List<ExpressionType<S>> components = new ArrayList<>(componentConfigs.length//
				- (typeRef.classes.isEmpty() ? 1 : 2)); // "name" and "class"
			for (QommonsConfig componentConfig : componentConfigs) {
				if (componentConfig.getName().equals("name") || componentConfig.getName().equals("class")
					|| componentConfig.getName().equals("priority"))
					continue;
				else if (componentConfig.getName().equals("field"))
					throw new IllegalArgumentException("field attribute is not allowed on an expression");
				ExpressionType<S> component;
				try {
					component = parseComponent(componentConfig, classes, declaredTypes, id, preGrammar, true);
				} catch (RuntimeException e) {
					throw new IllegalStateException(e.getMessage() + ":\n" + type, e);
				}
				components.add(component);
			}
			typeRef.initialize(Collections.unmodifiableList(components));
			for (ExpressionClass<S> clazz : typeRef.classes)
				declaredClasses.get(clazz.getName()).addType(typeRef.type);
		}
		List<ConfiguredExpressionType<S>> types = new ArrayList<>(declaredTypes.size());
		BetterSortedMap<String, ConfiguredExpressionType<S>> typesByName = new BetterTreeMap<>(false,
			QommonsUtils.DISTINCT_NUMBER_TOLERANT);
		for (ParsedExpressionType<S> typeRef : declaredTypes.values()) {
			types.add(typeRef.type);
			typesByName.put(typeRef.name, typeRef.type);
		}
		return new ExpressoGrammar<>(name, Collections.unmodifiableList(types), BetterCollections.unmodifiableSortedMap(typesByName),
			BetterCollections.unmodifiableSortedMap(classes));
	}

	private ExpressionType<S> parseComponent(QommonsConfig config, Map<String, ExpressionClass<S>> allClasses,
		Map<String, ParsedExpressionType<S>> allTypes, int[] id, PreGrammar grammar, boolean throwIfNotFound) {
		String componentType = config.getName();
		String value = config.getValue();
		if ("".equals(value))
			value = null;
		NavigableSet<String> fields = null;
		QommonsConfig[] subComponentConfigs = config.subConfigs();
		for (QommonsConfig subComponentConfig : subComponentConfigs) {
			if (subComponentConfig.getName().equals("field")) {
				if (subComponentConfig.subConfigs().length > 0 || subComponentConfig.getValue() == null)
					throw new IllegalArgumentException("field cannot be an element");
				if (fields == null)
					fields = new TreeSet<>();
				String[] fieldNames = subComponentConfig.getValue().split(",");
				for (String fieldName : fieldNames) {
					if (fieldName.length() == 0)
						throw new IllegalArgumentException("field names must not be zero-length");
					if (!fields.add(fieldName))
						System.err.println("Warning: duplicate field names declared: " + fieldName);
				}
			}
		}
		GrammarComponent<S> rc;
		ParsedExpressionType<S> type;
		ExpressionClass<S> clazz;
		ExpressionType<S> found;
		if ((rc = theComponents.get(componentType)) != null) {
			Map<String, String> params = new LinkedHashMap<>();
			String untrimmedValue = config.getValueUntrimmed();
			int componentId = id[0]++;
			List<ExpressionType<S>> subComponents = new ArrayList<>(subComponentConfigs.length);
			for (QommonsConfig subComponentConfig : subComponentConfigs) {
				String scName = subComponentConfig.getName();
				if (scName.equals("field"))
					continue;
				ExpressionType<S> subComponent = parseComponent(subComponentConfig, allClasses, allTypes, id, grammar, false);
				if (subComponent != null)
					subComponents.add(subComponent);
				else if (subComponentConfig.subConfigs().length > 0 || subComponentConfig.getValue() == null)
					throw new IllegalArgumentException("Unrecognized component: " + scName);
				else
					params.put(subComponentConfig.getName(), subComponentConfig.getValue());
			}
			if (subComponents.isEmpty())
				subComponents = Collections.emptyList();
			else {
				((ArrayList<?>) subComponents).trimToSize();
				subComponents = Collections.unmodifiableList(subComponents);
			}
			ExpressionType<S> component;
			try {
				component = rc.build(componentId, params, value, untrimmedValue, subComponents, grammar);
			} catch (RuntimeException e) {
				throw new IllegalStateException(e.getMessage() + ":\n" + config, e);
			}
			if (!params.isEmpty())
				throw new IllegalArgumentException("Unsupported configuration point" + (params.size() == 1 ? "" : "s") + ": "
					+ componentType + "." + params.keySet() + " in " + config);
			found = component;
		} else if ((type = allTypes.get(componentType)) != null) {
			for (QommonsConfig subComponentConfig : subComponentConfigs) {
				String scName = subComponentConfig.getName();
				if (scName.equals("field"))
					continue;
				throw new IllegalArgumentException("Cannot parameterize type references: " + componentType);
			}
			found = type;
		} else if ((clazz = allClasses.get(componentType)) != null) {
			for (QommonsConfig subComponentConfig : subComponentConfigs) {
				String scName = subComponentConfig.getName();
				if (scName.equals("field"))
					continue;
				throw new IllegalArgumentException("Cannot parameterize class references: " + componentType);
			}
			found = clazz;
		} else if (throwIfNotFound)
			throw new IllegalArgumentException("Unrecognized component type: " + componentType + " in " + config);
		else
			return null;
		return fields == null ? found : new ExpressionTypeReference<>(found, Collections.unmodifiableNavigableSet(fields));
	}

	/** @return The standard text GrammarParser */
	public static ExpressoGrammarParser<CharSequenceStream> forText() {
		Map<String, GrammarComponent<CharSequenceStream>> components = getCommonComponents();
		components.put("literal", (id, config, value, untrimmedValue, children, grammar) -> {
			if (!children.isEmpty())
				throw new IllegalArgumentException("Literal cannot have children");
			else if (untrimmedValue == null)
				throw new IllegalArgumentException("Literal declared with no content");
			else if (value != null)
				return new TextLiteralExpressionType<>(id, value);
			else
				return new TextLiteralExpressionType<>(id, untrimmedValue);
		});
		components.put("pattern", (id, config, value, untrimmedValue, children, grammar) -> {
			String ciStr = config.remove("case-sensitive");
			boolean ci;
			if (ciStr == null || ciStr.equals("false"))
				ci = false;
			else if (ciStr.equals("true"))
				ci = true;
			else
				throw new IllegalArgumentException("Unrecognized value for configuration case-sensitive: " + ciStr);
			String maxLen = config.remove("max-length");
			if (!children.isEmpty())
				throw new IllegalArgumentException("Pattern cannot have children");
			else if (value == null)
				throw new IllegalArgumentException("Pattern declared with no content");
			Pattern pattern = Pattern.compile(value, ci ? Pattern.CASE_INSENSITIVE : 0);
			return new TextPatternExpressionType<>(id, maxLen == null ? Integer.MAX_VALUE : Integer.parseInt(maxLen), pattern);
		});
		return new DefaultGrammarParser<>(components);
	}

	/** @return The standard binary GrammarParser (not yet implemented) */
	public static ExpressoGrammarParser<BinarySequenceStream> forBinary() {
		// Map<String, RecognizedComponent<CharSequenceStream>> components = getCommonComponents();
		// TODO Create and add a binary literal expression type and possibly some sort of binary pattern matcher
		throw new UnsupportedOperationException("Not implemented yet");
	}

	private static <S extends BranchableStream<?, ?>> Map<String, GrammarComponent<S>> getCommonComponents() {
		Map<String, GrammarComponent<S>> components = new HashMap<>();
		components.put("sequence", (id, config, value, untrimmedValue, children, grammar) -> {
			if (children.isEmpty())
				throw new IllegalArgumentException("Sequence declared with no children");
			else if (value != null)
				throw new IllegalArgumentException("Sequence declared with text content: " + value);
			else
				return new SequenceExpressionType<>(id, children);
		});
		components.put("one-of", (id, config, value, untrimmedValue, children, grammar) -> {
			if (children.isEmpty())
				throw new IllegalArgumentException("One-of declared with no children");
			else if (value != null)
				throw new IllegalArgumentException("One-of declared with text content: " + value);
			else
				return new OneOfExpressionType<>(id, children);
		});
		components.put("repeat", (id, config, value, untrimmedValue, children, grammar) -> {
			String minStr = config.remove("min");
			String maxStr = config.remove("max");
			if (children.isEmpty())
				throw new IllegalArgumentException("Repeat declared with no children");
			else if (value != null)
				throw new IllegalArgumentException("Repeat declared with text content: " + value);
			else
				return new RepeatExpressionType<>(id, //
					minStr == null ? 0 : Integer.parseInt(minStr), //
					maxStr == null ? Integer.MAX_VALUE : Integer.parseInt(maxStr), //
					children);
		});
		components.put("option", (id, config, value, untrimmedValue, children, grammar) -> {
			if (children.isEmpty())
				throw new IllegalArgumentException("Option declared with no children");
			else if (value != null)
				throw new IllegalArgumentException("Option declared with text content: " + value);
			else
				return new OptionalExpressionType<>(id, children);
		});
		components.put("forbid", (id, config, value, untrimmedValue, children, grammar) -> {
			if (children.isEmpty())
				throw new IllegalArgumentException("Forbid declared with no children");
			else if (children.size() > 1)
				throw new IllegalArgumentException("Forbid declared with multiple children");
			else if (value != null)
				throw new IllegalArgumentException("Forbid declared with text content: " + value);
			else
				return new ForbidExpressionType<>(id, children.get(0));
		});
		components.put("up-to", (id, config, value, untrimmedValue, children, grammar) -> {
			if (children.isEmpty())
				throw new IllegalArgumentException("Up-to declared with no children");
			else if (children.size() > 1)
				throw new IllegalArgumentException("Up-to declared with multiple children");
			else if (value != null)
				throw new IllegalArgumentException("Up-to declared with text content: " + value);
			else
				return new LeadUpExpressionType<>(id, children.get(0));
		});
		components.put("without", (id, config, value, untrimmedValue, children, grammar) -> {
			String typeStr = config.remove("types");
			if (typeStr == null)
				throw new IllegalArgumentException("Without declared with no excluded types: " + config);
			else if (children.isEmpty())
				throw new IllegalArgumentException("Without declared with no children: " + children);
			else if (value != null)
				throw new IllegalArgumentException("Without declared with text content: " + value);
			String[] splitTypes = typeStr.split(",");
			IntList excludedIds = new IntList(splitTypes.length);
			excludedIds.setSorted(true);
			excludedIds.setUnique(true);
			for (String type : splitTypes) {
				excludedIds.add(grammar.getTypeId(type));
			}
			return new ExcludeExpressionType<>(id, excludedIds.toArray(), children);
		});
		return components;
	}

	private static class ParsedExpressionType<S extends BranchableStream<?, ?>> implements ExpressionType<S> {
		private final int id;
		private final int priority;
		private final String name;
		final BetterSortedSet<ExpressionClass<S>> classes;
		ConfiguredExpressionType<S> type;

		ParsedExpressionType(int id, int priority, String name, BetterSortedSet<ExpressionClass<S>> classes) {
			this.id = id;
			this.priority = priority;
			this.name = name;
			this.classes = classes;
		}

		void initialize(List<ExpressionType<S>> components) {
			type = new ConfiguredExpressionType<>(id, priority, name, classes, components);
		}

		@Override
		public int getCacheId() {
			return type.getCacheId();
		}

		@Override
		public <S2 extends S> ExpressionPossibility<S2> parse(ExpressoParser<S2> parser) throws IOException {
			return type.parse(parser);
		}

		@Override
		public String toString() {
			return type == null ? name : type.toString();
		}
	}

	private static class ExpressionTypeReference<S extends BranchableStream<?, ?>> implements ExpressionFieldType<S> {
		private final ExpressionType<S> theWrapped;
		private final NavigableSet<String> theFields;

		ExpressionTypeReference(ExpressionType<S> wrapped, NavigableSet<String> fields) {
			theWrapped = wrapped;
			theFields = fields;
		}

		@Override
		public ExpressionType<S> getWrapped() {
			return theWrapped;
		}

		@Override
		public int getCacheId() {
			return -1;
		}

		@Override
		public NavigableSet<String> getFields() {
			return theFields;
		}

		@Override
		public <S2 extends S> ExpressionFieldType.FieldExpressionPossibility<S2> parse(ExpressoParser<S2> parser) throws IOException {
			return ExpressionFieldType.wrap(this, parser.parseWith(theWrapped));
		}

		@Override
		public String toString() {
			return theWrapped.toString() + " field=" + theFields;
		}
	}
}