package org.expresso3;

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
import java.util.stream.Collectors;

import org.expresso.stream.BinarySequenceStream;
import org.expresso.stream.BranchableStream;
import org.expresso.stream.CharSequenceStream;
import org.expresso3.types.ExcludeExpressionType;
import org.expresso3.types.ForbidExpressionType;
import org.expresso3.types.LeadUpExpressionType;
import org.expresso3.types.OneOfExpressionType;
import org.expresso3.types.OptionalExpressionType;
import org.expresso3.types.RepeatExpressionType;
import org.expresso3.types.SequenceExpressionType;
import org.expresso3.types.TextLiteralExpressionType;
import org.expresso3.types.TextPatternExpressionType;
import org.qommons.IntList;
import org.qommons.QommonsUtils;
import org.qommons.collect.BetterCollections;
import org.qommons.collect.BetterList;
import org.qommons.collect.BetterSortedMap;
import org.qommons.collect.BetterSortedSet;
import org.qommons.config.QommonsConfig;
import org.qommons.tree.BetterTreeList;
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

	/** The name of the ignorable expression class */
	public static final String IGNORABLE = "ignorable";

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

	class PreParsedClass {
		final ExpressionClass<S> clazz;
		final PreParsedClass[] parents;
		final List<String> memberNames;
		private final BetterList<ConfiguredExpressionType<S>> types;
		private final SortedTreeList<ExpressionClass<S>> childClasses;

		PreParsedClass(ExpressoGrammar<S> grammar, int id, String className, PreParsedClass[] parents) {
			types = new SortedTreeList<>(false, (t1, t2) -> -Integer.compare(t1.getPriority(), t2.getPriority()));
			memberNames = new LinkedList<>();
			childClasses = new SortedTreeList<>(false, ExpressionClass::compareTo);
			clazz = new ExpressionClass<>(grammar, id, className, //
				Collections.unmodifiableList(Arrays.asList(parents).stream().map(ppc -> ppc.clazz)
					.collect(Collectors.toCollection(() -> new ArrayList<>(parents.length)))), //
				BetterCollections.unmodifiableList(childClasses), BetterCollections.unmodifiableList(types));
			this.parents = parents;
			for (PreParsedClass parent : parents)
				parent.childClasses.add(clazz);
		}

		ExpressionClass<S> addTypeName(String typeName) {
			memberNames.add(typeName);
			return clazz;
		}

		ExpressionClass<S> addType(ConfiguredExpressionType<S> type) {
			if (!types.contains(type))
				types.add(type);
			for (PreParsedClass parent : parents)
				parent.addType(type);
			return clazz;
		}
	}

	@Override
	public ExpressoGrammar<S> parseGrammar(String name, InputStream stream) throws IOException {
		QommonsConfig config = QommonsConfig.fromXml(QommonsConfig.getRootElement(stream));
		BetterSortedMap<String, ExpressionClass<S>> classes = new BetterTreeMap<>(false, QommonsUtils.DISTINCT_NUMBER_TOLERANT);
		List<ConfiguredExpressionType<S>> types = new ArrayList<>(config.subConfigs().length);
		BetterSortedMap<String, ConfiguredExpressionType<S>> typesByName = new BetterTreeMap<>(false,
			QommonsUtils.DISTINCT_NUMBER_TOLERANT);
		ExpressoGrammar<S> grammar = new ExpressoGrammar<>(name, Collections.unmodifiableList(types),
			BetterCollections.unmodifiableSortedMap(typesByName), BetterCollections.unmodifiableSortedMap(classes));

		if (!config.getName().equals("expresso"))
			throw new IllegalArgumentException("expresso expected as root, not " + config.getName());
		int[] id = new int[1];
		Map<String, PreParsedClass> declaredClasses = new LinkedHashMap<>();
		declaredClasses.put(IGNORABLE, new PreParsedClass(grammar, id[0]++, IGNORABLE, new DefaultGrammarParser.PreParsedClass[0]));
		QommonsConfig[] classesConfig = config.subConfigs("classes");
		if (classesConfig.length > 1)
			throw new IllegalArgumentException("Only a single classes element is allowed");
		if (classesConfig.length == 1) {
			for (QommonsConfig classConfig : classesConfig[0].subConfigs()) {
				if (!"class".equals(classConfig.getName()))
					throw new IllegalArgumentException("Only class elements (and no attributes) may exist under the classes element");
				String className = classConfig.get("name");
				if (className == null)
					throw new IllegalArgumentException("class element has no name: " + classConfig);
				if (RESERVED_EXPRESSION_NAMES.contains(className))
					throw new IllegalArgumentException(className + " cannot be used as a class name in expresso: " + classConfig);
				else if (theComponents.containsKey(className))
					throw new IllegalArgumentException(className + " is a reserved component type name: " + classConfig);
				else if (declaredClasses.containsKey(className))
					throw new IllegalArgumentException("Class " + className + " is already declared: " + classConfig);

				String extendsStr = classConfig.get("extends");
				PreParsedClass[] parents;
				if (extendsStr == null)
					parents = new DefaultGrammarParser.PreParsedClass[0];
				else {
					String[] extendsSplit = extendsStr.split(",");
					parents = new DefaultGrammarParser.PreParsedClass[extendsSplit.length];
					for (int i = 0; i < extendsSplit.length; i++) {
						extendsSplit[i] = extendsSplit[i].trim();
						parents[i] = declaredClasses.get(extendsSplit[i]);
						if (parents[i] == null)
							throw new IllegalArgumentException(
								"Parent class " + extendsSplit[i] + " of class " + className + " has not been declared");
					}
				}
				declaredClasses.put(className, new PreParsedClass(grammar, id[0]++, className, parents));
			}
		}

		Map<String, ConfiguredReferenceExpressionType<S>> declaredTypes = new LinkedHashMap<>();
		// One run-though to populate the type references
		for (QommonsConfig type : config.subConfigs()) {
			if (type.getName().equals("classes"))
				continue;
			if (!type.getName().equals("expression"))
				throw new IllegalArgumentException("expression elements expected, not " + type.getName());
			String typeName = type.get("name");
			if (typeName == null)
				throw new IllegalArgumentException("expression has no name: " + type);
			else if (RESERVED_EXPRESSION_NAMES.contains(typeName) || IGNORABLE.equals(typeName))
				throw new IllegalArgumentException(typeName + " cannot be used as an expression name in expresso");
			else if (theComponents.containsKey(typeName))
				throw new IllegalArgumentException(typeName + " is a reserved component type name: " + type);
			else if (declaredTypes.containsKey(typeName))
				throw new IllegalArgumentException(typeName + " has already been declared as an expression: " + type);
			else if (declaredClasses.containsKey(typeName))
				throw new IllegalArgumentException(typeName + " has already been declared as a class: " + type);
			String classesStr = type.get("class");
			BetterSortedSet<ExpressionClass<S>> typeClasses;
			if (classesStr != null) {
				String[] classesSplit = classesStr.split(",");
				typeClasses = new BetterTreeSet<>(false, ExpressionClass::compareTo);
				for (int i = 0; i < classesSplit.length; i++) {
					classesSplit[i] = classesSplit[i].trim();
					PreParsedClass clazz = declaredClasses.get(classesSplit[i]);
					if (clazz == null)
						throw new IllegalArgumentException("Class " + classesSplit[i] + " does not exist: " + type);
					typeClasses.add(clazz.addTypeName(typeName));
				}
				typeClasses = BetterCollections.unmodifiableSortedSet(typeClasses);
			} else
				typeClasses = BetterSortedSet.empty(ExpressionClass::compareTo);
			int priority = type.getInt("priority", 0);
			if (declaredTypes.put(typeName, new ConfiguredReferenceExpressionType<>(id[0]++, priority, typeName, typeClasses)) != null)
				throw new IllegalArgumentException("Duplicate expressions named " + typeName);
		}
		for (PreParsedClass clazz : declaredClasses.values())
			classes.put(clazz.clazz.getName(), clazz.clazz);
		ExpressionType<S> ignorable;
		ExpressionClass<S> ignorableClass = classes.get(IGNORABLE);
		if (ignorableClass != null)
			ignorable = new RepeatExpressionType<>(id[0]++, 0, Integer.MAX_VALUE, Arrays.asList(ignorableClass));
		else
			ignorable = null;
		PreGrammar preGrammar = new PreGrammar() {
			@Override
			public int getTypeId(String typeName) {
				ConfiguredReferenceExpressionType<S> type = declaredTypes.get(typeName);
				if (type != null)
					return type.id;
				PreParsedClass clazz = declaredClasses.get(typeName);
				if (clazz != null)
					return clazz.clazz.getId();
				throw new IllegalArgumentException("Unrecognized type name: " + typeName);
			}
		};
		// Second run-through to initialize all the types
		Map<ExpressionType<S>, ExpressionType<S>> cachedExpressions = new HashMap<>();
		for (QommonsConfig type : config.subConfigs()) {
			if (type.getName().equals("classes"))
				continue;
			ConfiguredReferenceExpressionType<S> typeRef = declaredTypes.get(type.get("name"));
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
					if (typeRef.doesExtend(ignorableClass))
						component = parseComponent(componentConfig, classes, declaredTypes, cachedExpressions, id, preGrammar, true, null);
					else
						component = parseComponent(componentConfig, classes, declaredTypes, cachedExpressions, id, preGrammar, true,
							ignorable);
				} catch (RuntimeException e) {
					throw new IllegalStateException(e.getMessage() + ":\n" + type, e);
				}
				components.add(component);
			}
			typeRef.initialize(grammar, Collections.unmodifiableList(components));
			for (ExpressionClass<S> clazz : typeRef.classes)
				declaredClasses.get(clazz.getName()).addType(typeRef.type);
		}
		for (ConfiguredReferenceExpressionType<S> typeRef : declaredTypes.values()) {
			types.add(typeRef.type);
			typesByName.put(typeRef.name, typeRef.type);
		}
		return grammar;
	}

	private ExpressionType<S> parseComponent(QommonsConfig config, Map<String, ExpressionClass<S>> allClasses,
		Map<String, ConfiguredReferenceExpressionType<S>> allTypes, Map<ExpressionType<S>, ExpressionType<S>> cachedExpressions, int[] id,
		PreGrammar grammar, boolean throwIfNotFound, ExpressionType<S> ignorable) {
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
		ConfiguredReferenceExpressionType<S> type;
		ExpressionClass<S> clazz;
		ExpressionType<S> found;
		if ((rc = theComponents.get(componentType)) != null) {
			Map<String, String> params = new LinkedHashMap<>();
			String untrimmedValue = config.getValueUntrimmed();
			List<ExpressionType<S>> subComponents = new ArrayList<>(subComponentConfigs.length);
			int componentId = id[0]++;
			for (QommonsConfig subComponentConfig : subComponentConfigs) {
				String scName = subComponentConfig.getName();
				if (scName.equals("field"))
					continue;
				ExpressionType<S> subComponent = parseComponent(subComponentConfig, allClasses, allTypes, cachedExpressions, id, grammar,
					false, ignorable);
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
			if (component.isCacheable())
				component = cachedExpressions.computeIfAbsent(component, c -> c);
			if (!params.isEmpty())
				throw new IllegalArgumentException("Unsupported configuration point" + (params.size() == 1 ? "" : "s") + ": "
					+ componentType + "." + params.keySet() + " in " + config);
			found = component;
		} else if ((type = allTypes.get(componentType)) != null) {
			for (QommonsConfig subComponentConfig : subComponentConfigs) {
				String scName = subComponentConfig.getName();
				if (scName.equals("field"))
					continue;
				throw new IllegalArgumentException("Cannot parameterize type references except with field: " + componentType);
			}
			found = type;
		} else if ((clazz = allClasses.get(componentType)) != null) {
			for (QommonsConfig subComponentConfig : subComponentConfigs) {
				String scName = subComponentConfig.getName();
				if (scName.equals("field"))
					continue;
				throw new IllegalArgumentException("Cannot parameterize class references except with field: " + componentType);
			}
			found = clazz;
		} else if (throwIfNotFound)
			throw new IllegalArgumentException("Unrecognized component type: " + componentType + " in " + config);
		else
			return null;
		ExpressionType<S> exType = fields == null ? found
			: new FieldMarkedExpressionType<>(found, Collections.unmodifiableNavigableSet(fields));
		if (ignorable != null && found instanceof BareContentExpressionType)
			exType = new SequenceExpressionType<>(id[0]++, Arrays.asList(ignorable, exType));
		return exType;
	}

	/** @return The standard text GrammarParser */
	public static ExpressoGrammarParser<CharSequenceStream> forText() {
		Map<String, GrammarComponent<CharSequenceStream>> components = getCommonComponents();
		components.put("literal", (id, config, value, untrimmedValue, children, grammar) -> {
			if (!children.isEmpty())
				throw new IllegalArgumentException("Literal cannot have children");
			else if (untrimmedValue == null)
				throw new IllegalArgumentException("Literal declared with no content");

			if (value != null)
				return new TextLiteralExpressionType<>(id, value);
			else
				return new TextLiteralExpressionType<>(id, untrimmedValue);
		});
		components.put("pattern", (int id, Map<String, String> config, String value, String untrimmedValue,
			List<ExpressionType<CharSequenceStream>> children, PreGrammar grammar) -> {
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
			BetterList<? extends ExpressionType<? super S>> betterChildren = new BetterTreeList<>(false);
			((BetterList<Object>) betterChildren).withAll(children);
			return new OneOfExpressionType<>(id, BetterCollections.unmodifiableList(betterChildren));
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

	public static class ConfiguredReferenceExpressionType<S extends BranchableStream<?, ?>> implements ExpressionType<S> {
		private final int id;
		private final int priority;
		private final String name;
		final BetterSortedSet<ExpressionClass<S>> classes;
		ConfiguredExpressionType<S> type;

		ConfiguredReferenceExpressionType(int id, int priority, String name, BetterSortedSet<ExpressionClass<S>> classes) {
			this.id = id;
			this.priority = priority;
			this.name = name;
			this.classes = classes;
		}

		boolean doesExtend(ExpressionClass<S> clazz) {
			for (ExpressionClass<S> c : classes)
				if (c.doesExtend(clazz))
					return true;
			return false;
		}

		void initialize(ExpressoGrammar<S> grammar, List<ExpressionType<S>> components) {
			type = new ConfiguredExpressionType<>(grammar, id, priority, name, classes, components);
		}

		@Override
		public int getId() {
			return type.getId();
		}

		@Override
		public boolean isCacheable() {
			return false;
		}

		@Override
		public int getEmptyQuality(int minQuality) {
			return type.getEmptyQuality(minQuality);
		}

		@Override
		public <S2 extends S> Expression<S2> parse(ExpressoParser<S2> parser) throws IOException {
			Expression<S2> ex = parser.parseWith(type);
			return ex == null ? null : new WrappedExpression<>(this, ex);
		}

		@Override
		public Iterable<? extends ExpressionType<? super S>> getComponents() {
			return Collections.unmodifiableList(Arrays.asList(type));
		}

		@Override
		public String toString() {
			return type == null ? name : type.toString();
		}
	}

	public static class WrappedExpression<S extends BranchableStream<?, ?>> implements Expression<S> {
		private final ExpressionType<? super S> theType;
		private final Expression<S> theWrapped;

		WrappedExpression(ExpressionType<? super S> type, Expression<S> wrapped) {
			theType = type;
			theWrapped = wrapped;
		}

		@Override
		public ExpressionType<? super S> getType() {
			return theType;
		}

		@Override
		public S getStream() {
			return theWrapped.getStream();
		}

		@Override
		public int length() {
			return theWrapped.length();
		}

		@Override
		public List<? extends Expression<S>> getChildren() {
			return Collections.unmodifiableList(Arrays.asList(theWrapped));
		}

		@Override
		public Expression<S> nextMatch(ExpressoParser<S> parser) throws IOException {
			Expression<S> ex = parser.nextMatch(theWrapped);
			return ex == null ? null : new WrappedExpression<>(theType, ex);
		}

		@Override
		public int getErrorCount() {
			return theWrapped.getErrorCount();
		}

		@Override
		public Expression<S> getFirstError() {
			return theWrapped.getFirstError();
		}

		@Override
		public int getLocalErrorRelativePosition() {
			return theWrapped.getLocalErrorRelativePosition();
		}

		@Override
		public String getLocalErrorMessage() {
			return theWrapped.getLocalErrorMessage();
		}

		@Override
		public Expression<S> unwrap() {
			return theWrapped.unwrap();
		}

		@Override
		public int getMatchQuality() {
			return theWrapped.getMatchQuality();
		}

		@Override
		public boolean isInvariant() {
			return theWrapped.isInvariant();
		}

		@Override
		public StringBuilder print(StringBuilder str, int indent, String metadata) {
			return theWrapped.print(str, indent, metadata);
		}

		@Override
		public int hashCode() {
			return theWrapped.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			else if (!(obj instanceof WrappedExpression))
				return false;
			return theWrapped.equals(((WrappedExpression<?>) obj).theWrapped);
		}

		@Override
		public String toString() {
			return theWrapped.toString();
		}
	}

	public static class FieldMarkedExpressionType<S extends BranchableStream<?, ?>> implements ExpressionFieldType<S> {
		private final ExpressionType<S> theWrapped;
		private final NavigableSet<String> theFields;

		FieldMarkedExpressionType(ExpressionType<S> wrapped, NavigableSet<String> fields) {
			theWrapped = wrapped;
			theFields = fields;
		}

		@Override
		public ExpressionType<S> getWrapped() {
			return theWrapped;
		}

		@Override
		public int getId() {
			return -1;
		}

		@Override
		public boolean isCacheable() {
			return theWrapped.isCacheable();
		}

		@Override
		public int getEmptyQuality(int minQuality) {
			return theWrapped.getEmptyQuality(minQuality);
		}

		@Override
		public NavigableSet<String> getFields() {
			return theFields;
		}

		@Override
		public <S2 extends S> ExpressionField<S2> parse(ExpressoParser<S2> parser) throws IOException {
			return ExpressionFieldType.wrap(this, parser.parseWith(theWrapped));
		}

		@Override
		public Iterable<? extends ExpressionType<? super S>> getComponents() {
			return Collections.unmodifiableList(Arrays.asList(theWrapped));
		}

		@Override
		public String toString() {
			return theWrapped.toString() + " field=" + theFields;
		}
	}
}
