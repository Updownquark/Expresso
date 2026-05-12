package org.observe.expresso.qonfig;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

import org.observe.SettableValue;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableSet;
import org.observe.collect.ObservableSortedCollection;
import org.observe.collect.ObservableSortedSet;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ModelValueInstantiator;
import org.observe.util.TypeTokens;
import org.qommons.StringUtils;
import org.qommons.Version;
import org.qommons.collect.CircularArrayList;
import org.qommons.collect.DequeList;
import org.qommons.collect.QuickSet.QuickMap;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigElementView.ValueMember;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.config.QonfigToolkit.ToolkitDef;
import org.qommons.ex.ExBiConsumer;
import org.qommons.ex.ExBiFunction;
import org.qommons.ex.ExConsumer;
import org.qommons.ex.ExFunction;

import com.google.common.reflect.TypeToken;

/**
 * <p>
 * This is a utility class that handles much of the boilerplate code necessary to properly manage attributes, element values, and children
 * for Qonfig-backed expresso elements, which is most of what is required to create a new element type.
 * </p>
 * <p>
 * To use this class, make a new class for your element or add-on. If possible (i.e. your element or add-on does not need to extend
 * another), the class may extends the {@link ExElementType.AbstractElement AbstractElement} or {@link ExElementType.AbstractAddOn
 * AbstractAddOn} member classes.
 * </p>
 * <p>
 * In the element class, make static constants for each attribute and child and the element value if it has one using the static methods in
 * this class (e.g. {@link #valueExpression(String, Class, Supplier)} and
 * {@link #child(String, Class, ExBiFunction, ExConsumer, ExFunction)}). Then build the {@link ExElementType} for the element or add-on with
 * {@link #build(String, Version, String)}.
 * </p>
 * <p>
 * Define definition and interpretation classes for the element/add-on (whether in the same file or not). If the element/add-on extends one
 * of the abstract member classes, those classes themselves have <code>Def</code> and <code>Interpreted</code> member classes that those
 * definition and interpretation classes should extend.
 * </p>
 * <h2>Traceability:</h2>
 * <p>
 * The definition class should be tagged with {@link ExElementTraceable}. Make a static method in the definition class tagged with
 * {@link TraceabilityConfiguration}. For an element type, the method should take a single parameter of type
 * {@link ElementTypeTraceability.SingleTypeTraceabilityBuilder} and should call
 * {@link #configureElementTraceability(Function, Function, Function, ElementTypeTraceability.SingleTypeTraceabilityBuilder)} on the
 * element's {@link ExElementType type}. For an add-on type, the method should take a
 * {@link ElementTypeTraceability.AddOnTraceabilityBuilder} and call
 * {@link #configureAddOnTraceability(Function, Function, Function, ElementTypeTraceability.AddOnTraceabilityBuilder)}.
 * </p>
 * <p>
 * If the element/add-on class is able to extend one of the abstract member classes, those classes will take care of configuring all the
 * configured attributes, value, and children. Otherwise:
 * <ul>
 * <li>The definition class needs to instantiate a {@link DefTypeData} with the element/add-on {@link ExElementType type} (to support
 * extension of the type, the type should be accepted as a parameter to the constructor). The definition class will need to
 * {@link DefTypeData#update(ExElement.Def, ExpressoQIS) update} the type data in the overridden
 * {@link ExElement.Def.Abstract#doUpdate(ExpressoQIS)} or {@link ExAddOn.Def.Abstract#update(ExpressoQIS, ExElement.Def)} method.</li>
 * <li>The interpreted class needs to use the definition's {@link DefTypeData#interpret()} method to instantiate an
 * {@link InterpretedTypeData} for itself, and call {@link InterpretedTypeData#update(ExElement.Interpreted)} from the overridden
 * {@link ExElement.Interpreted.Abstract#doUpdate()} or {@link ExAddOn.Interpreted.Abstract#update(ExElement.Interpreted)} method.</li>
 * <li>The instance implementation needs to call the interpretation's {@link InterpretedTypeData#instantiate(ExElement)} method to create an
 * {@link InstanceTypeData} for itself from the overridden {@link ExElement.Abstract#doUpdate(ExElement.Interpreted)} or
 * {@link ExAddOn.Abstract#update(ExAddOn.Interpreted, ExElement)} method, and must call:
 * <ul>
 * <li>{@link InstanceTypeData#instantiated()} from the overridden {@link ExElement.Abstract#instantiated()} or
 * {@link ExAddOn.Abstract#instantiated()} method,</li>
 * <li>{@link InstanceTypeData#instantiate(ModelSetInstance, ExElement)} from the overridden
 * {@link ExElement.Abstract#doInstantiate(ModelSetInstance)} or {@link ExAddOn.Abstract#instantiate(ModelSetInstance)} method,</li>
 * <li>{@link InstanceTypeData#copy(ExElement)} to create a new {@link InstanceTypeData} for the copied element/add-on in the overridden
 * {@link ExElement.Abstract#copy(ExElement)} or {@link ExAddOn.Abstract#copy(ExElement)} method,</li>
 * <li>and {@link InstanceTypeData#destroy()} from the overridden {@link ExElement.Abstract#destroy()} or {@link ExAddOn.Abstract#destroy()}
 * method</li>
 * </ul>
 * </li>
 * </ul>
 * </p>
 */
public class ExElementType {
	private final ExElementType theSuper;
	private final ToolkitDef theToolkit;
	private final String theElementName;
	private final QuickMap<String, ExElementValue<?, ?, ?, ?, ?>> theAttributes;
	private final ExElementValue<?, ?, ?, ?, ?> theValue;
	private final QuickMap<String, ExElementChild<?, ?, ?>> theChildren;

	ExElementType(ExElementType superType, ToolkitDef toolkit, String elementName,
		QuickMap<String, ExElementValue<?, ?, ?, ?, ?>> attributes, ExElementValue<?, ?, ?, ?, ?> value,
		QuickMap<String, ExElementChild<?, ?, ?>> children) {
		theSuper = superType;
		theToolkit = toolkit;
		theElementName = elementName;
		theAttributes = attributes;
		theValue = value;
		theChildren = children;

		for (ExElementValue<?, ?, ?, ?, ?> v : theAttributes.allValues())
			((AbstractElementValue<?, ?, ?, ?, ?>) v).setOwner(this);
		if (theValue != null)
			((AbstractElementValue<?, ?, ?, ?, ?>) theValue).setOwner(this);

		for (ExElementChild<?, ?, ?> child : theChildren.allValues())
			child.setOwner(this);
	}

	public ExElementType getSuper() {
		return theSuper;
	}

	public ToolkitDef getToolkit() {
		return theToolkit;
	}

	public String getElementName() {
		return theElementName;
	}

	public List<ExElementValue<?, ?, ?, ?, ?>> getAttributes() {
		return theAttributes.allValues();
	}

	public ExElementValue<?, ?, ?, ?, ?> getValue() {
		return theValue;
	}

	public List<ExElementChild<?, ?, ?>> getChildren() {
		return theChildren.allValues();
	}

	public int indexOf(ExElementValue<?, ?, ?, ?, ?> value) {
		if (value == theValue)
			return -1;
		else if (value.getAttributeName() == null)
			throw new IllegalArgumentException("Value " + value + " is not a member of this element type");
		int index = theAttributes.keyIndex(value.getAttributeName());
		if (index < 0)
			throw new IllegalArgumentException("Attribute " + value + " is not a member of this element type");
		ExElementValue<?, ?, ?, ?, ?> myAttr = theAttributes.get(index);
		if (myAttr == value)
			return index;
		else
			throw new IllegalArgumentException("Attribute " + value + " is not a member of this element type");
	}

	public int indexOf(ExElementChild<?, ?, ?> child) {
		int index = theChildren.keyIndex(child.getRoleName());
		if (index < 0)
			throw new IllegalArgumentException("Child " + child + " is not a member of this element type");
		ExElementChild<?, ?, ?> myChild = theChildren.get(index);
		if (myChild == child)
			return index;
		else
			throw new IllegalArgumentException("Child " + child + " is not a member of this element type");
	}

	public <E extends ExElement, I extends ExElement.Interpreted<E>, D extends ExElement.Def<E>> void configureElementTraceability(//
		Function<? super D, DefTypeData> defData, Function<? super I, InterpretedTypeData> interpretedData,
		Function<? super E, InstanceTypeData> instanceData, ElementTypeTraceability.SingleTypeTraceabilityBuilder<E, I, D> traceability) {
		for (ExElementValue<?, ?, ?, ?, ?> v : theAttributes.allValues()) {
			((AbstractElementValue<?, ?, ?, ?, ?>) v).setOwner(this);
			traceability.withAttribute(v.getAttributeName(), def -> defData.apply(def).getValue(v),
				interp -> interpretedData.apply(interp).getValue(v));
		}
		if (theValue != null) {
			((AbstractElementValue<?, ?, ?, ?, ?>) theValue).setOwner(this);
			traceability.withValue(def -> defData.apply(def).getValue(theValue),
				interp -> interpretedData.apply(interp).getValue(theValue));
		}

		for (ExElementChild<?, ?, ?> child : theChildren.allValues()) {
			child.setOwner(this);
			traceability.withChild(child.getRoleName(), def -> defData.apply(def).getChildren(child),
				interp -> interpretedData.apply(interp).getChildren(child), inst -> instanceData.apply(inst).getChildren(child));
		}
	}

	public <E extends ExElement, AO extends ExAddOn<E>, I extends ExAddOn.Interpreted<E, AO>, D extends ExAddOn.Def<E, AO>> void configureAddOnTraceability(//
		Function<? super D, DefTypeData> defData, Function<? super I, InterpretedTypeData> interpretedData,
		Function<? super AO, InstanceTypeData> instanceData, ElementTypeTraceability.AddOnTraceabilityBuilder<E, AO, I, D> traceability) {
		for (ExElementValue<?, ?, ?, ?, ?> v : theAttributes.allValues()) {
			((AbstractElementValue<?, ?, ?, ?, ?>) v).setOwner(this);
			traceability.withAddOnAttribute(v.getAttributeName(), def -> defData.apply(def).getValue(v),
				interp -> interpretedData.apply(interp).getValue(v));
		}
		if (theValue != null) {
			((AbstractElementValue<?, ?, ?, ?, ?>) theValue).setOwner(this);
			traceability.withAddOnValue(def -> defData.apply(def).getValue(theValue),
				interp -> interpretedData.apply(interp).getValue(theValue));
		}

		for (ExElementChild<?, ?, ?> child : theChildren.allValues()) {
			child.setOwner(this);
			traceability.withAddOnChild(child.getRoleName(), def -> defData.apply(def).getChildren(child),
				interp -> interpretedData.apply(interp).getChildren(child), inst -> instanceData.apply(inst).getChildren(child));
		}
	}

	public interface ExElementValue<V, I, D, W, F> {
		String getAttributeName();

		D compile(ExElement.Def<?> element, ExpressoQIS session) throws QonfigInterpretationException;

		I interpret(D definition, InterpretedTypeData type, ExElement.Interpreted<?> element) throws ExpressoInterpretationException;

		W createInstanceWrapper();

		F flattenWrapper(W wrapper);

		V instantiate(I interpreted, ExElement element) throws ModelInstantiationException;

		void instantiated(V value) throws ModelInstantiationException;

		void wrap(W wrapper, V instance, ExElement element, ModelSetInstance models) throws ModelInstantiationException;
	}

	public static class ExElementChild<V extends ExElement, I extends ExElement.Interpreted<?>, D extends ExElement.Def<?>> {
		private ExElementType theOwner;
		private final String theRoleName;
		private final Class<D> theDefType;
		private final ExBiFunction<? super D, ExElement.Interpreted<?>, ? extends I, ExpressoInterpretationException> theInterpreter;
		private final ExConsumer<? super I, ExpressoInterpretationException> theInterpretationUpdate;
		private final ExFunction<? super I, ? extends V, ModelInstantiationException> theInstantiator;

		public ExElementChild(String roleName, Class<D> defType,
			ExBiFunction<? super D, ExElement.Interpreted<?>, ? extends I, ExpressoInterpretationException> interpreter,
			ExConsumer<? super I, ExpressoInterpretationException> interpretationUpdate,
			ExFunction<? super I, ? extends V, ModelInstantiationException> instantiator) {
			theRoleName = roleName;
			theDefType = defType;
			theInterpreter = interpreter;
			theInterpretationUpdate = interpretationUpdate;
			theInstantiator = instantiator;
		}

		ExElementType getOwner() {
			return theOwner;
		}

		void setOwner(ExElementType owner) {
			if (theOwner != null && theOwner != owner)
				throw new IllegalStateException(
					"An element attribute or value (" + this + ") cannot be owned by multiple elements: " + theOwner + " and " + owner);
			theOwner = owner;
		}

		public String getRoleName() {
			return theRoleName;
		}

		public Class<D> getDefType() {
			return theDefType;
		}

		public void compile(ExElement.Def<?> parent, List<? extends ExElement.Def<?>> currentChildren, ExpressoQIS session)
			throws QonfigInterpretationException {
			parent.syncChildren(theDefType, (List<D>) currentChildren, session.forChildren(theRoleName), ExElement.Def::update);
		}

		public void interpret(ExElement.Interpreted<?> parent, DefTypeData definition,
			List<? extends ExElement.Interpreted<?>> currentChildren) throws ExpressoInterpretationException {
			parent.syncChildren(definition.getChildren(this), (List<I>) currentChildren, def -> theInterpreter.apply(def, parent),
				theInterpretationUpdate);
		}

		public void instantiate(ExElement parent, InterpretedTypeData interpreted, List<? extends ExElement> currentChildren)
			throws ModelInstantiationException {
			parent.syncChildren(interpreted.getChildren(this), (List<V>) currentChildren, theInstantiator, ExElement::update);
		}
	}

	public static Builder build(String toolkitName, Version toolkitVersion, String elementName) {
		return new Builder(new ToolkitDef(toolkitName, toolkitVersion.major, toolkitVersion.minor), elementName);
	}

	public static Builder build(ToolkitDef toolkit, String elementName) {
		return new Builder(toolkit, elementName);
	}

	public static <T> SimpleValue<T> simpleValue(String attributeName, Class<T> type,
		ExFunction<ExElement.Def<?>, T, QonfigInterpretationException> defaultValue) {
		return new SimpleValue<>(attributeName, type, defaultValue);
	}

	public static <T, I extends ExElement.Interpreted<?>> ValueExpression<T, I> valueExpression(String attributeName, Class<T> type,
		Supplier<? extends T> defaultValue) {
		return valueExpression(attributeName, ModelTypes.Value.forType(type), defaultValue);
	}

	public static <T, I extends ExElement.Interpreted<?>> ValueExpression<T, I> valueExpression(String attributeName, TypeToken<T> type,
		Supplier<? extends T> defaultValue) {
		return valueExpression(attributeName, ModelTypes.Value.forType(type), defaultValue);
	}

	public static <T, I extends ExElement.Interpreted<?>> ValueExpression<T, I> valueExpression(String attributeName,
		ModelInstanceType<SettableValue<?>, SettableValue<T>> type, Supplier<? extends T> defaultValue) {
		return new ValueExpression<>(attributeName, __ -> type, defaultValue);
	}

	public static <T, I extends ExElement.Interpreted<?>> ValueExpression<T, I> valueExpression(String attributeName,
		ExFunction<I, ModelInstanceType<SettableValue<?>, SettableValue<T>>, ExpressoInterpretationException> type,
		Supplier<? extends T> defaultValue) {
		return new ValueExpression<>(attributeName, type, defaultValue);
	}

	public static <T, I extends ExElement.Interpreted<?>> CollectionExpression<T, I> collectionExpression(String attributeName,
		Class<T> type) {
		return collectionExpression(attributeName, TypeTokens.get().of(type));
	}

	public static <T, I extends ExElement.Interpreted<?>> CollectionExpression<T, I> collectionExpression(String attributeName,
		TypeToken<T> type) {
		ModelInstanceType<ObservableCollection<?>, ObservableCollection<T>> instanceType = ModelTypes.Collection.forType(type);
		return new CollectionExpression<>(attributeName, el -> instanceType);
	}

	public static <T, I extends ExElement.Interpreted<?>> CollectionExpression<T, I> collectionExpression(String attributeName,
		ExFunction<I, TypeToken<T>, ExpressoInterpretationException> type) {
		return new CollectionExpression<>(attributeName, el -> ModelTypes.Collection.forType(type.apply(el)));
	}

	public static <T, I extends ExElement.Interpreted<?>> SetExpression<T, I> setExpression(String attributeName, Class<T> type) {
		return setExpression(attributeName, TypeTokens.get().of(type));
	}

	public static <T, I extends ExElement.Interpreted<?>> SetExpression<T, I> setExpression(String attributeName, TypeToken<T> type) {
		ModelInstanceType<ObservableSet<?>, ObservableSet<T>> instanceType = ModelTypes.Set.forType(type);
		return new SetExpression<>(attributeName, el -> instanceType);
	}

	public static <T, I extends ExElement.Interpreted<?>> SetExpression<T, I> setExpression(String attributeName,
		ExFunction<I, TypeToken<T>, ExpressoInterpretationException> type) {
		return new SetExpression<>(attributeName, el -> ModelTypes.Set.forType(type.apply(el)));
	}

	public static <T, I extends ExElement.Interpreted<?>> SortedCollectionExpression<T, I> sortedCollectionExpression(String attributeName,
		Class<T> type, Comparator<? super T> sorting) {
		return sortedCollectionExpression(attributeName, TypeTokens.get().of(type), sorting);
	}

	public static <T, I extends ExElement.Interpreted<?>> SortedCollectionExpression<T, I> sortedCollectionExpression(String attributeName,
		TypeToken<T> type, Comparator<? super T> sorting) {
		ModelInstanceType<ObservableSortedCollection<?>, ObservableSortedCollection<T>> instanceType = ModelTypes.SortedCollection
			.forType(type);
		return new SortedCollectionExpression<>(attributeName, el -> instanceType, sorting);
	}

	public static <T, I extends ExElement.Interpreted<?>> SortedCollectionExpression<T, I> sortedCollectionExpression(String attributeName,
		Comparator<? super T> sorting, ExFunction<I, TypeToken<T>, ExpressoInterpretationException> type) {
		return new SortedCollectionExpression<>(attributeName, el -> ModelTypes.SortedCollection.forType(type.apply(el)), sorting);
	}

	public static <T, I extends ExElement.Interpreted<?>> SortedSetExpression<T, I> sortedSetExpression(String attributeName, Class<T> type,
		Comparator<? super T> sorting) {
		return sortedSetExpression(attributeName, TypeTokens.get().of(type), sorting);
	}

	public static <T, I extends ExElement.Interpreted<?>> SortedSetExpression<T, I> sortedSetExpression(String attributeName,
		TypeToken<T> type, Comparator<? super T> sorting) {
		ModelInstanceType<ObservableSortedSet<?>, ObservableSortedSet<T>> instanceType = ModelTypes.SortedSet.forType(type);
		return new SortedSetExpression<>(attributeName, el -> instanceType, sorting);
	}

	public static <T, I extends ExElement.Interpreted<?>> SortedSetExpression<T, I> sortedSetExpression(String attributeName,
		Comparator<? super T> sorting, ExFunction<I, TypeToken<T>, ExpressoInterpretationException> type) {
		return new SortedSetExpression<>(attributeName, el -> ModelTypes.SortedSet.forType(type.apply(el)), sorting);
	}

	public static <E extends ExElement, I extends ExElement.Interpreted<? super E>, D extends ExElement.Def<? super E>> ExElementChild<E, I, D> child(//
		String roleName, Class<? super D> defType, //
		ExBiFunction<? super D, ExElement.Interpreted<?>, I, ExpressoInterpretationException> interpreter,
		ExConsumer<? super I, ExpressoInterpretationException> update, //
		ExFunction<? super I, E, ModelInstantiationException> instantiator) {
		return new ExElementChild<>(roleName, (Class<D>) (Class<?>) defType, interpreter, update, instantiator);
	}

	static abstract class AbstractElementValue<V, I, D, W, F> implements ExElementValue<V, I, D, W, F> {
		private ExElementType theOwner;

		ExElementType getOwner() {
			return theOwner;
		}

		void setOwner(ExElementType owner) {
			if (theOwner != null && theOwner != owner)
				throw new IllegalStateException(
					"An element attribute or value (" + this + ") cannot be owned by multiple elements: " + theOwner + " and " + owner);
			theOwner = owner;
		}
	}

	public static class SimpleValue<V> extends AbstractElementValue<V, V, V, SettableValue<V>, SettableValue<V>> {
		private final String theAttributeName;
		private final Class<V> theType;
		private final ExFunction<? super ExElement.Def<?>, V, QonfigInterpretationException> theDefault;

		public SimpleValue(String attributeName, Class<V> type,
			ExFunction<? super ExElement.Def<?>, V, QonfigInterpretationException> defaultValue) {
			theAttributeName = attributeName;
			theType = type;
			theDefault = defaultValue;
		}

		@Override
		public String getAttributeName() {
			return theAttributeName;
		}

		@Override
		public V compile(ExElement.Def<?> element, ExpressoQIS session) throws QonfigInterpretationException {
			ValueMember<?, ?> view = theAttributeName == null ? session.getValue() : session.attributes().get(theAttributeName);
			if (view != null)
				return view.getValue(theType);
			else if (theDefault != null)
				return theDefault.apply(element);
			else
				return null;
		}

		@Override
		public V interpret(V definition, InterpretedTypeData type, ExElement.Interpreted<?> element)
			throws ExpressoInterpretationException {
			return definition;
		}

		@Override
		public SettableValue<V> createInstanceWrapper() {
			return SettableValue.create();
		}

		@Override
		public SettableValue<V> flattenWrapper(SettableValue<V> wrapper) {
			return wrapper;
		}

		@Override
		public V instantiate(V interpreted, ExElement element) {
			return interpreted;
		}

		@Override
		public void instantiated(V value) throws ModelInstantiationException {
		}

		@Override
		public void wrap(SettableValue<V> wrapper, V instance, ExElement element, ModelSetInstance models) {
			wrapper.set(instance);
		}

		@Override
		public String toString() {
			return (theAttributeName == null ? "(value)" : theAttributeName) + " (" + theType.getName() + ")";
		}
	}

	public static abstract class AbstractValueExpression<M, MV extends M, E extends ExElement.Interpreted<?>> extends AbstractElementValue<//
	ModelValueInstantiator<MV>, InterpretedValueSynth<M, MV>, CompiledExpression, SettableValue<MV>, MV> {
		private final String theAttributeName;
		private final ExFunction<E, ModelInstanceType<M, MV>, ExpressoInterpretationException> theType;

		protected AbstractValueExpression(String attributeName,
			ExFunction<E, ModelInstanceType<M, MV>, ExpressoInterpretationException> type) {
			theAttributeName = attributeName;
			theType = type;
		}

		@Override
		public String getAttributeName() {
			return theAttributeName;
		}

		@Override
		public CompiledExpression compile(ExElement.Def<?> element, ExpressoQIS session) throws QonfigInterpretationException {
			return theAttributeName == null ? element.getValueExpression(session)
				: element.getAttributeExpression(theAttributeName, session);
		}

		@Override
		public InterpretedValueSynth<M, MV> interpret(CompiledExpression definition, InterpretedTypeData interpretedType,
			ExElement.Interpreted<?> element) throws ExpressoInterpretationException {
			ModelInstanceType<M, MV> type = theType.apply((E) element);
			return element.interpret(definition, type);
		}

		@Override
		public SettableValue<MV> createInstanceWrapper() {
			return SettableValue.create();
		}

		@Override
		public ModelValueInstantiator<MV> instantiate(InterpretedValueSynth<M, MV> interpreted, ExElement element)
			throws ModelInstantiationException {
			return interpreted.instantiate();
		}

		@Override
		public void instantiated(ModelValueInstantiator<MV> value) throws ModelInstantiationException {
			value.instantiate();
		}

		@Override
		public void wrap(SettableValue<MV> wrapper, ModelValueInstantiator<MV> instance, ExElement element, ModelSetInstance models)
			throws ModelInstantiationException {
			wrapper.set(instance == null ? null : instance.get(models));
		}
	}

	public static class ValueExpression<T, I extends ExElement.Interpreted<?>>
	extends AbstractValueExpression<SettableValue<?>, SettableValue<T>, I> {
		private final Supplier<? extends T> theDefault;

		public ValueExpression(String attributeName,
			ExFunction<I, ModelInstanceType<SettableValue<?>, SettableValue<T>>, ExpressoInterpretationException> type,
			Supplier<? extends T> defaultValue) {
			super(attributeName, type);
			theDefault = defaultValue;
		}

		@Override
		public SettableValue<T> flattenWrapper(SettableValue<SettableValue<T>> wrapper) {
			return SettableValue.flatten(wrapper, theDefault);
		}

		@Override
		public void instantiated(ModelValueInstantiator<SettableValue<T>> value) throws ModelInstantiationException {
			value.instantiate();
		}

		@Override
		public void wrap(SettableValue<SettableValue<T>> wrapper, ModelValueInstantiator<SettableValue<T>> instance, ExElement element,
			ModelSetInstance models) throws ModelInstantiationException {
			wrapper.set(instance.get(models));
		}
	}

	public static class CollectionExpression<T, E extends ExElement.Interpreted<?>>
	extends AbstractValueExpression<ObservableCollection<?>, ObservableCollection<T>, E> {
		public CollectionExpression(String attributeName,
			ExFunction<E, ModelInstanceType<ObservableCollection<?>, ObservableCollection<T>>, ExpressoInterpretationException> type) {
			super(attributeName, type);
		}

		@Override
		public ObservableCollection<T> flattenWrapper(SettableValue<ObservableCollection<T>> wrapper) {
			return ObservableCollection.flattenValue(wrapper);
		}
	}

	public static class SetExpression<T, E extends ExElement.Interpreted<?>>
	extends AbstractValueExpression<ObservableSet<?>, ObservableSet<T>, E> {
		public SetExpression(String attributeName,
			ExFunction<E, ModelInstanceType<ObservableSet<?>, ObservableSet<T>>, ExpressoInterpretationException> type) {
			super(attributeName, type);
		}

		@Override
		public ObservableSet<T> flattenWrapper(SettableValue<ObservableSet<T>> wrapper) {
			return ObservableSet.flattenValue(wrapper);
		}
	}

	public static class SortedCollectionExpression<T, E extends ExElement.Interpreted<?>>
	extends AbstractValueExpression<ObservableSortedCollection<?>, ObservableSortedCollection<T>, E> {
		private final Comparator<? super T> theSorting;

		public SortedCollectionExpression(String attributeName,
			ExFunction<E, ModelInstanceType<ObservableSortedCollection<?>, ObservableSortedCollection<T>>, ExpressoInterpretationException> type,
			Comparator<? super T> sorting) {
			super(attributeName, type);
			theSorting = sorting;
		}

		@Override
		public ObservableSortedCollection<T> flattenWrapper(SettableValue<ObservableSortedCollection<T>> wrapper) {
			return ObservableSortedCollection.flattenValue(wrapper, theSorting);
		}
	}

	public static class SortedSetExpression<T, E extends ExElement.Interpreted<?>>
	extends AbstractValueExpression<ObservableSortedSet<?>, ObservableSortedSet<T>, E> {
		private final Comparator<? super T> theSorting;

		public SortedSetExpression(String attributeName,
			ExFunction<E, ModelInstanceType<ObservableSortedSet<?>, ObservableSortedSet<T>>, ExpressoInterpretationException> type,
			Comparator<? super T> sorting) {
			super(attributeName, type);
			theSorting = sorting;
		}

		@Override
		public ObservableSortedSet<T> flattenWrapper(SettableValue<ObservableSortedSet<T>> wrapper) {
			return ObservableSortedSet.flattenValue(wrapper, theSorting);
		}
	}

	static class TypeValueData<T> {
		private final TypeValueData<T> theSuperData;
		private final Object[] theAttributes;
		private T theValue;

		TypeValueData(ExElementType type) {
			theSuperData = type.getSuper() == null ? null : new TypeValueData<>(type.getSuper());
			theAttributes = new Object[type.getAttributes().size()];
		}

		TypeValueData<T> getSuperData() {
			return theSuperData;
		}

		T get(ExElementValue<?, ?, ?, ?, ?> value, ExElementType type) {
			if (theSuperData != null && ((AbstractElementValue<?, ?, ?, ?, ?>) value).getOwner() != type)
				return theSuperData.get(value, type.getSuper());
			int index = type.indexOf(value);
			if (index < 0)
				return theValue;
			else
				return (T) theAttributes[index];
		}

		void set(ExElementValue<?, ?, ?, ?, ?> valueType, ExElementType type, T value) {
			if (theSuperData != null && ((AbstractElementValue<?, ?, ?, ?, ?>) valueType).getOwner() != type)
				theSuperData.set(valueType, type.getSuper(), value);
			int index = type.indexOf(valueType);
			if (index < 0)
				theValue = value;
			else
				theAttributes[index] = value;
		}

		void set(int index, T value) {
			if (index < 0)
				theValue = value;
			else
				theAttributes[index] = value;
		}

		void setAll(T value) {
			if (theSuperData != null)
				theSuperData.setAll(value);
			Arrays.fill(theAttributes, value);
			theValue = value;
		}

		<X extends Throwable> T modify(ExElementValue<?, ?, ?, ?, ?> valueType, ExElementType type,
			ExFunction<? super T, ? extends T, X> modify) throws X {
			if (theSuperData != null && ((AbstractElementValue<?, ?, ?, ?, ?>) valueType).getOwner() != type)
				return theSuperData.modify(valueType, type.getSuper(), modify);
			int index = type.indexOf(valueType);
			if (index < 0) {
				theValue = modify.apply(theValue);
				return theValue;
			} else {
				T value = modify.apply((T) theAttributes[index]);
				theAttributes[index] = value;
				return value;
			}
		}

		<X extends Throwable> void forEach(ExElementType type,
			ExBiFunction<? super T, ExElementValue<?, ?, ?, ?, ?>, ? extends T, X> modify) throws X {
			if (theSuperData != null)
				theSuperData.forEach(type.getSuper(), modify);
			for (int a = 0; a < theAttributes.length; a++) {
				T newValue = modify.apply((T) theAttributes[a], type.getAttributes().get(a));
				theAttributes[a] = newValue;
			}
			if (type.getValue() != null)
				theValue = modify.apply(theValue, type.getValue());
		}
	}

	public static class TypeChildData<T> {
		private final TypeChildData<T> theSuperData;
		private final DequeList<T>[] theChildren;

		TypeChildData(ExElementType type) {
			theSuperData = type.getSuper() == null ? null : new TypeChildData<>(type.getSuper());
			theChildren = new DequeList[type.getChildren().size()];
			for (int c = 0; c < theChildren.length; c++)
				theChildren[c] = new CircularArrayList<>();
		}

		TypeChildData<T> getSuperData() {
			return theSuperData;
		}

		DequeList<T> get(int index) {
			return theChildren[index];
		}

		DequeList<T> get(ExElementChild<?, ?, ?> child, ExElementType type) {
			if (theSuperData != null && child.getOwner() != type)
				return theSuperData.get(child, type.getSuper());
			return theChildren[type.indexOf(child)];
		}

		<X extends Throwable> void forEach(ExElementType type,
			ExBiConsumer<? super DequeList<T>, ? super ExElementChild<?, ?, ?>, X> action) throws X {
			if (theSuperData != null)
				theSuperData.forEach(type.getSuper(), action);
			for (int c = 0; c < theChildren.length; c++) {
				action.accept(theChildren[c], type.getChildren().get(c));
			}
		}
	}

	public static class DefTypeData {
		private final ExElementType theType;
		private final TypeValueData<Object> theValues;
		private final TypeChildData<? extends ExElement.Def<?>> theChildren;

		public DefTypeData(ExElementType type) {
			theType = type;
			theValues = new TypeValueData<>(type);
			theChildren = new TypeChildData<>(type);
		}

		public ExElementType getType() {
			return theType;
		}

		public <D> D getValue(ExElementValue<?, ?, D, ?, ?> valueDef) {
			return (D) theValues.get(valueDef, theType);
		}

		public <D extends ExElement.Def<?>> DequeList<D> getChildren(ExElementChild<?, ?, D> childDef) {
			return (DequeList<D>) theChildren.get(childDef, theType);
		}

		public <D extends ExElement.Def<?>> D getChild(ExElementChild<?, ?, D> childDef) {
			DequeList<D> children = getChildren(childDef);
			if (children.isEmpty())
				return null;
			else if (children.size() == 1)
				return children.getFirst();
			else
				throw new IllegalArgumentException("Multiple children for " + childDef);
		}

		public void update(ExElement.Def<?> element, ExpressoQIS session) throws QonfigInterpretationException {
			compileTypeData(theType, theValues, theChildren, element, session);
		}

		private void compileTypeData(ExElementType type, TypeValueData<Object> valueData,
			TypeChildData<? extends ExElement.Def<?>> childData, ExElement.Def<?> element, ExpressoQIS session)
				throws QonfigInterpretationException {
			if (type.getSuper() != null)
				compileTypeData(type.getSuper(), valueData.getSuperData(), childData.getSuperData(), element,
					session.asElement(type.getSuper().getToolkit(), type.getSuper().getElementName()));

			for (int a = 0; a < type.getAttributes().size(); a++)
				valueData.set(a, type.getAttributes().get(a).compile(element, session));
			if (type.getValue() != null)
				valueData.set(-1, type.getValue().compile(element, session));

			for (int c = 0; c < type.getChildren().size(); c++)
				type.getChildren().get(c).compile(element, childData.get(c), session);
		}

		public InterpretedTypeData interpret() {
			return new InterpretedTypeData(this);
		}
	}

	public static class InterpretedTypeData {
		private final DefTypeData theDefinition;
		private final TypeValueData<Object> theValues;
		private final TypeChildData<? extends ExElement.Interpreted<?>> theChildren;

		public InterpretedTypeData(DefTypeData definition) {
			theDefinition = definition;
			theValues = new TypeValueData<>(theDefinition.getType());
			theChildren = new TypeChildData<>(theDefinition.getType());
		}

		public <I> I getValue(ExElementValue<?, I, ?, ?, ?> valueDef) {
			return (I) theValues.get(valueDef, theDefinition.getType());
		}

		public <I, D> I getOrInterpretValue(ExElementValue<?, I, D, ?, ?> valueDef, ExElement.Interpreted<?> element)
			throws ExpressoInterpretationException {
			return (I) theValues.modify(valueDef, theDefinition.getType(), value -> {
				if (value == null)
					value = valueDef.interpret(theDefinition.getValue(valueDef), this, element);
				return value;
			});
		}

		public <I extends ExElement.Interpreted<?>> DequeList<I> getChildren(ExElementChild<?, I, ?> childDef) {
			return (DequeList<I>) theChildren.get(childDef, theDefinition.getType());
		}

		public <I extends ExElement.Interpreted<?>> I getChild(ExElementChild<?, I, ?> childDef) {
			DequeList<I> children = getChildren(childDef);
			if (children.isEmpty())
				return null;
			else if (children.size() == 1)
				return children.getFirst();
			else
				throw new IllegalArgumentException("Multiple children for " + childDef);
		}

		public void clearValues() {
			theValues.setAll(null);
		}

		public void update(ExElement.Interpreted<?> element) throws ExpressoInterpretationException {
			ExElementType type = theDefinition.getType();
			theValues.forEach(type, (value, valueType) -> {
				if (value == null)
					value = ((ExElementValue<?, ?, Object, ?, ?>) valueType).interpret(theDefinition.getValue(valueType), this, element);
				return value;
			});
			theChildren.forEach(type, (old, childType) -> childType.interpret(element, theDefinition, old));
		}

		public InstanceTypeData instantiate(ExElement element) throws ModelInstantiationException {
			InstanceTypeData instanceData = new InstanceTypeData(theDefinition.getType());

			instanceData.update(this, element);

			return instanceData;
		}
	}

	public static class InstanceTypeData {
		static class InstantiatedValueData<V, W, F> {
			V instantiator;
			final W wrapper;
			final F flatWrapper;

			InstantiatedValueData(ExElementValue<V, ?, ?, W, F> value) {
				wrapper = value.createInstanceWrapper();
				flatWrapper = value.flattenWrapper(wrapper);
			}

			<I> InstantiatedValueData<V, W, F> instantiate(ExElementValue<?, I, ?, ?, ?> valueType, InterpretedTypeData interpreted,
				ExElement element) throws ModelInstantiationException {
				I interpretedValue = interpreted.getValue(valueType);
				instantiator = interpretedValue == null ? null : (V) valueType.instantiate(interpretedValue, element);
				return this;
			}

			InstantiatedValueData<V, W, F> instantiated(ExElementValue<?, ?, ?, ?, ?> valueType) throws ModelInstantiationException {
				if (instantiator != null)
					((ExElementValue<V, ?, ?, W, F>) valueType).instantiated(instantiator);
				return this;
			}

			InstantiatedValueData<V, W, F> instantiate(ExElementValue<?, ?, ?, ?, ?> valueType, ExElement element,
				ModelSetInstance myModels) throws ModelInstantiationException {
				((ExElementValue<V, ?, ?, W, F>) valueType).wrap(wrapper, instantiator, element, myModels);
				return this;
			}
		}

		private final ExElementType theType;
		private TypeValueData<InstantiatedValueData<?, ?, ?>> theValues;
		private TypeChildData<? extends ExElement> theChildren;

		InstanceTypeData(ExElementType type) {
			theType = type;
			theValues = new TypeValueData<>(theType);
			theValues.forEach(theType, (old, valueType) -> new InstantiatedValueData<>(valueType));
			theChildren = new TypeChildData<>(theType);
		}

		public ExElementType getType() {
			return theType;
		}

		public <V> V getValue(ExElementValue<?, ?, ?, ?, V> valueDef) {
			return (V) theValues.get(valueDef, theType).flatWrapper;
		}

		public <E extends ExElement> DequeList<E> getChildren(ExElementChild<E, ?, ?> childDef) {
			return (DequeList<E>) theChildren.get(childDef, theType);
		}

		public <E extends ExElement> E getChild(ExElementChild<E, ?, ?> childDef) {
			DequeList<E> children = getChildren(childDef);
			if (children.isEmpty())
				return null;
			else if (children.size() == 1)
				return children.getFirst();
			else
				throw new IllegalArgumentException("Multiple children for " + childDef);
		}

		void update(InterpretedTypeData interpreted, ExElement element) throws ModelInstantiationException {
			theValues.forEach(theType, (old, valueType) -> old.instantiate(valueType, interpreted, element));
			theChildren.forEach(theType, (old, childType) -> childType.instantiate(element, interpreted, old));
		}

		public void instantiated() throws ModelInstantiationException {
			theValues.forEach(theType, InstantiatedValueData::instantiated);
			theChildren.forEach(theType, (old, childType) -> {
				for (ExElement child : old)
					child.instantiated();
			});
		}

		public void instantiate(ModelSetInstance models, ExElement element) throws ModelInstantiationException {
			theValues.forEach(theType, (old, valueType) -> old.instantiate(valueType, element, models));
			theChildren.forEach(theType, (old, childType) -> {
				for (ExElement child : old)
					child.instantiate(models);
			});
		}

		public InstanceTypeData copy(ExElement newOwner) {
			InstanceTypeData copy = new InstanceTypeData(theType);
			copy.theChildren.forEach(theType, (old, childType) -> {
				for (ExElement child : theChildren.get(childType, theType))
					((DequeList<ExElement>) old).add(child.copy(newOwner));
			});
			return copy;
		}

		public void destroy() {
			theChildren.forEach(theType, (old, childType) -> {
				for (ExElement child : old)
					child.destroy();
			});
		}
	}

	public static class Builder {
		private final ToolkitDef theToolkit;
		private final String theElementName;
		private final Map<String, ExElementValue<?, ?, ?, ?, ?>> theAttributes;
		private ExElementValue<?, ?, ?, ?, ?> theValue;
		private final Map<String, ExElementChild<?, ?, ?>> theChildren;

		Builder(ToolkitDef toolkit, String elementName) {
			theToolkit = toolkit;
			theElementName = elementName;
			theAttributes = new HashMap<>();
			theChildren = new HashMap<>();
		}

		public Builder withValue(ExElementValue<?, ?, ?, ?, ?> value) {
			String attr = value.getAttributeName();
			if (attr == null) {
				if (theValue == null)
					theValue = value;
				else
					throw new IllegalArgumentException("An element value is already set: " + theValue);
			} else {
				ExElementValue<?, ?, ?, ?, ?> prev = theAttributes.putIfAbsent(attr, value);
				if (prev != null)
					throw new IllegalArgumentException("Attribute '" + attr + "' is already set: " + prev);
			}
			return this;
		}

		public Builder withChild(ExElementChild<?, ?, ?> child) {
			ExElementChild<?, ?, ?> prev = theChildren.putIfAbsent(child.getRoleName(), child);
			if (prev != null)
				throw new IllegalArgumentException("Child '" + child.getRoleName() + "' is already set: " + prev);
			return this;
		}

		public ExElementType build(ExElementType superType) {
			QuickMap<String, ExElementValue<?, ?, ?, ?, ?>> attrs = QuickMap.of(theAttributes, StringUtils.DISTINCT_NUMBER_TOLERANT);
			QuickMap<String, ExElementChild<?, ?, ?>> children = QuickMap.of(theChildren, StringUtils.DISTINCT_NUMBER_TOLERANT);
			return new ExElementType(superType, theToolkit, theElementName, attrs.unmodifiable(), theValue, children.unmodifiable());
		}
	}

	public static abstract class AbstractElement extends ExElement.Abstract {
		public static <E extends AbstractElement, I extends Interpreted<E>, D extends Def<E>> void configureTraceability(
			ElementTypeTraceability.SingleTypeTraceabilityBuilder<E, ?, ?> traceability, ExElementType type) {
			type.configureElementTraceability(Def::getTypeData, Interpreted::getTypeData, AbstractElement::getTypeData,
				(ElementTypeTraceability.SingleTypeTraceabilityBuilder<E, I, D>) traceability);
		}

		public static abstract class Def<E extends AbstractElement> extends ExElement.Def.Abstract<E> {
			private final DefTypeData theTypeData;

			protected Def(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType, ExElementType type) {
				super(parent, qonfigType);
				theTypeData = new DefTypeData(type);
			}

			protected DefTypeData getTypeData() {
				return theTypeData;
			}

			public <D> D getValue(ExElementValue<?, ?, D, ?, ?> valueDef) {
				return theTypeData.getValue(valueDef);
			}

			public <D extends ExElement.Def<?>> DequeList<D> getChildren(ExElementChild<?, ?, D> childDef) {
				return theTypeData.getChildren(childDef);
			}

			public <D extends ExElement.Def<?>> D getChild(ExElementChild<?, ?, D> childDef) {
				return theTypeData.getChild(childDef);
			}

			@Override
			protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
				super.doUpdate(session);

				theTypeData.update(this, session);
			}

			protected abstract Interpreted<? extends E> interpret(ExElement.Interpreted<?> parent);
		}

		public static abstract class Interpreted<E extends AbstractElement> extends ExElement.Interpreted.Abstract<E> {
			private final InterpretedTypeData theTypeData;

			protected Interpreted(Def<? super E> definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
				theTypeData = definition.getTypeData().interpret();
			}

			@Override
			public Def<? super E> getDefinition() {
				return (Def<? super E>) super.getDefinition();
			}

			protected InterpretedTypeData getTypeData() {
				return theTypeData;
			}

			public <I> I getValue(ExElementValue<?, I, ?, ?, ?> valueDef) {
				return theTypeData.getValue(valueDef);
			}

			public <I> I getOrInterpretValue(ExElementValue<?, I, ?, ?, ?> valueDef) throws ExpressoInterpretationException {
				return theTypeData.getOrInterpretValue(valueDef, this);
			}

			public <I extends ExElement.Interpreted<?>> DequeList<I> getChildren(ExElementChild<?, I, ?> childDef) {
				return theTypeData.getChildren(childDef);
			}

			public <I extends ExElement.Interpreted<?>> I getChild(ExElementChild<?, I, ?> childDef) {
				return theTypeData.getChild(childDef);
			}

			@Override
			protected void doUpdate() throws ExpressoInterpretationException {
				super.doUpdate();

				theTypeData.update(this);
			}

			protected abstract AbstractElement create();
		}

		private InstanceTypeData theTypeData;

		protected AbstractElement(Object id) {
			super(id);
		}

		protected InstanceTypeData getTypeData() {
			return theTypeData;
		}

		public <V> V getValue(ExElementValue<?, ?, ?, ?, V> valueDef) {
			return theTypeData.getValue(valueDef);
		}

		public <E extends ExElement> DequeList<E> getChildren(ExElementChild<E, ?, ?> childDef) {
			return theTypeData.getChildren(childDef);
		}

		public <E extends ExElement> E getChild(ExElementChild<E, ?, ?> childDef) {
			return theTypeData.getChild(childDef);
		}

		@Override
		protected void doUpdate(ExElement.Interpreted<?> interpreted) throws ModelInstantiationException {
			super.doUpdate(interpreted);

			theTypeData = ((Interpreted<?>) interpreted).getTypeData().instantiate(this);
		}

		@Override
		public void instantiated() throws ModelInstantiationException {
			super.instantiated();

			theTypeData.instantiated();
		}

		@Override
		protected ModelSetInstance doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
			myModels = super.doInstantiate(myModels);

			theTypeData.instantiate(myModels, this);

			return myModels;
		}

		@Override
		public AbstractElement copy(ExElement parent) {
			AbstractElement copy = (AbstractElement) super.copy(parent);

			copy.theTypeData = theTypeData.copy(copy);

			return copy;
		}

		@Override
		public void destroy() {
			super.destroy();

			theTypeData.destroy();
		}
	}

	public static abstract class AbstractAddOn<E extends ExElement> extends ExAddOn.Abstract<E> {
		public static <E extends ExElement, AO extends AbstractAddOn<E>, I extends Interpreted<E, AO>, D extends Def<E, AO>> void configureTraceability(
			ElementTypeTraceability.AddOnTraceabilityBuilder<E, AO, ?, ?> traceability, ExElementType type) {
			type.configureAddOnTraceability(Def::getTypeData, Interpreted::getTypeData, AbstractAddOn::getTypeData,
				(ElementTypeTraceability.AddOnTraceabilityBuilder<E, AO, I, D>) traceability);
		}

		public static abstract class Def<E extends ExElement, AO extends AbstractAddOn<E>> extends ExAddOn.Def.Abstract<E, AO> {
			private final DefTypeData theTypeData;

			protected Def(QonfigAddOn type, ExElement.Def<? extends E> element, DefTypeData typeData) {
				super(type, element);
				theTypeData = typeData;
			}

			protected DefTypeData getTypeData() {
				return theTypeData;
			}

			public <D> D getValue(ExElementValue<?, ?, D, ?, ?> valueDef) {
				return theTypeData.getValue(valueDef);
			}

			public <D extends ExElement.Def<?>> DequeList<D> getChildren(ExElementChild<?, ?, D> childDef) {
				return theTypeData.getChildren(childDef);
			}

			public <D extends ExElement.Def<?>> D getChild(ExElementChild<?, ?, D> childDef) {
				return theTypeData.getChild(childDef);
			}

			@Override
			public void update(ExpressoQIS session, ExElement.Def<? extends E> element) throws QonfigInterpretationException {
				super.update(session, element);

				theTypeData.update(element, session);
			}

			@Override
			public abstract <E2 extends E> Interpreted<? super E2, ? extends AO> interpret(ExElement.Interpreted<E2> element);
		}

		public static abstract class Interpreted<E extends ExElement, AO extends AbstractAddOn<E>>
		extends ExAddOn.Interpreted.Abstract<E, AO> {
			private final InterpretedTypeData theTypeData;

			protected Interpreted(Def<? super E, ? super AO> definition, ExElement.Interpreted<? extends E> element) {
				super(definition, element);
				theTypeData = definition.getTypeData().interpret();
			}

			protected InterpretedTypeData getTypeData() {
				return theTypeData;
			}

			public <I> I getValue(ExElementValue<?, I, ?, ?, ?> valueDef) {
				return theTypeData.getValue(valueDef);
			}

			public <I> I getOrInterpretValue(ExElementValue<?, I, ?, ?, ?> valueDef) throws ExpressoInterpretationException {
				return theTypeData.getOrInterpretValue(valueDef, getElement());
			}

			public <I extends ExElement.Interpreted<?>> DequeList<I> getChildren(ExElementChild<?, I, ?> childDef) {
				return theTypeData.getChildren(childDef);
			}

			public <I extends ExElement.Interpreted<?>> I getChild(ExElementChild<?, I, ?> childDef) {
				return theTypeData.getChild(childDef);
			}

			@Override
			public void update(ExElement.Interpreted<? extends E> element) throws ExpressoInterpretationException {
				super.update(element);

				theTypeData.update(element);
			}

			@Override
			public abstract AO create(ExElement element);
		}

		private InstanceTypeData theTypeData;

		protected AbstractAddOn(ExElement element) {
			super(element);
		}

		protected InstanceTypeData getTypeData() {
			return theTypeData;
		}

		public <V> V getValue(ExElementValue<?, ?, ?, ?, V> valueDef) {
			return theTypeData.getValue(valueDef);
		}

		public <E extends ExElement> DequeList<E> getChildren(ExElementChild<E, ?, ?> childDef) {
			return theTypeData.getChildren(childDef);
		}

		public <E extends ExElement> E getChild(ExElementChild<E, ?, ?> childDef) {
			return theTypeData.getChild(childDef);
		}

		@Override
		public void update(ExAddOn.Interpreted<? super E, ?> interpreted, ExElement element) throws ModelInstantiationException {
			super.update(interpreted, element);

			theTypeData = ((Interpreted<?, ?>) interpreted).getTypeData().instantiate(element);
		}

		@Override
		public void instantiated() throws ModelInstantiationException {
			super.instantiated();

			theTypeData.instantiated();
		}

		@Override
		public ModelSetInstance instantiate(ModelSetInstance models) throws ModelInstantiationException {
			models = super.instantiate(models);

			theTypeData.instantiate(models, getElement());

			return models;
		}

		@Override
		public Abstract<E> copy(ExElement element) {
			AbstractAddOn<E> copy = (AbstractAddOn<E>) super.copy(element);

			copy.theTypeData = theTypeData.copy(element);

			return copy;
		}

		@Override
		public void destroy() {
			super.destroy();

			theTypeData.destroy();
		}
	}
}
