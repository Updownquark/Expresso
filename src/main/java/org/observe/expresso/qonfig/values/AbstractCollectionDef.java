package org.observe.expresso.qonfig.values;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableCollectionBuilder;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelType;
import org.observe.expresso.ObservableExpression;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ModelValueInstantiator;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.expresso.qonfig.ExpressoBaseV0_1;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.ModelValueElement;
import org.observe.expresso.qonfig.QonfigChildGetter;
import org.observe.expresso.qonfig.values.CollectionElement.CollectionPopulator;
import org.observe.util.TypeTokens;
import org.qommons.Transaction;
import org.qommons.collect.BetterList;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

import com.google.common.reflect.TypeToken;

/**
 * Abstract ExElement definition for the Expresso &lt;int-list> element.
 *
 * @param <C> The sub-type of {@link ObservableCollection} that this element creates
 */
@ExElementTraceable(toolkit = ExpressoBaseV0_1.BASE,
	qonfigType = "int-list",
	interpretation = AbstractCollectionDef.Interpreted.class,
	instance = AbstractCollectionDef.Instantiator.class)
public abstract class AbstractCollectionDef<C extends ObservableCollection<?>> extends
ModelValueElement.Def.SingleTyped<C, ModelValueElement<C>> implements ModelValueElement.CompiledSynth<C, ModelValueElement<C>> {
	private QonfigElementOrAddOn theIntListType;
	private final List<CollectionElement> theElements;

	/**
	 * @param parent The parent element of this value element
	 * @param qonfigType The Qonfig type of this value element
	 * @param modelType The collection model type for the value
	 */
	protected AbstractCollectionDef(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType, ModelType.SingleTyped<C> modelType) {
		super(parent, qonfigType, modelType);
		theElements = new ArrayList<>();
	}

	/** @return Elements defined to initialize this collection */
	@QonfigChildGetter("element")
	public List<CollectionElement> getElements() {
		return Collections.unmodifiableList(theElements);
	}

	@Override
	protected boolean useWrapperType() {
		return true;
	}

	@Override
	protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
		super.doUpdate(session);
		theIntListType = session.isInstance("int-list");
		if (theIntListType != null) {
			ExpressoQIS intListSession = session.asElement("int-list");
			syncChildren(CollectionElement.class, theElements, intListSession.forChildren("element"));
		}
		if ((getElementValue() != null && getElementValue().getExpression() != ObservableExpression.EMPTY) && !theElements.isEmpty())
			reporting().error("Both a list value and elements specified");
	}

	@Override
	protected void doPrepare(ExpressoQIS session) throws QonfigInterpretationException {
		if (!theElements.isEmpty()) {
			List<ExpressoQIS> elementSessions = session.asElement(theIntListType).forChildren("element");
			int i = 0;
			for (CollectionElement element : theElements)
				element.prepareModelValue(elementSessions.get(i++));
		}
	}

	@Override
	public AbstractCollectionDef.Interpreted<?, C> interpretValue(ExElement.Interpreted<?> parent) {
		return (AbstractCollectionDef.Interpreted<?, C>) interpret2(parent);
	}

	/**
	 * @param parent The parent element for the interpreted collection
	 * @return The interpreted collection
	 */
	protected abstract AbstractCollectionDef.Interpreted<?, ?> interpret2(ExElement.Interpreted<?> parent);

	/**
	 * {@link AbstractCollectionDef} implementation
	 *
	 * @param <T> The type of values in the collection
	 * @param <C> The type of the collection
	 */
	public static abstract class Interpreted<T, C extends ObservableCollection<T>>
	extends ModelValueElement.Def.SingleTyped.Interpreted<C, C, ModelValueElement<C>>
	implements ModelValueElement.InterpretedSynth<C, C, ModelValueElement<C>> {
		private final List<CollectionElement.Interpreted<T>> theElements;

		/**
		 * @param definition The definition to interpret
		 * @param parent The parent element for this collection
		 */
		protected Interpreted(AbstractCollectionDef<?> definition, ExElement.Interpreted<?> parent) {
			super((AbstractCollectionDef<C>) definition, parent);
			theElements = new ArrayList<>();
		}

		@Override
		public AbstractCollectionDef<C> getDefinition() {
			return (AbstractCollectionDef<C>) super.getDefinition();
		}

		/** @return Elements defined to initialize this collection */
		public List<CollectionElement.Interpreted<T>> getElements() {
			return Collections.unmodifiableList(theElements);
		}

		@Override
		public List<? extends InterpretedValueSynth<?, ?>> getComponents() {
			if (getElementValue() != null)
				return Collections.singletonList(getElementValue());
			else
				return Collections.emptyList(); // Elements are initialization only, this value is independent (fundamental)
		}

		@Override
		public void doUpdate() throws ExpressoInterpretationException {
			super.doUpdate();
			try (Transaction t = ModelValueElement.INTERPRETING_PARENTS.installParent(this)) {
				syncChildren(getDefinition().getElements(), theElements,
					d -> (CollectionElement.Interpreted<T>) d.interpret(getExpressoEnv(d.getDocument())),
					CollectionElement.Interpreted::update);
			}
		}

		/** @return The type of elements in this collection */
		protected TypeToken<T> getValueType() {
			return TypeTokens.get().wrap((TypeToken<T>) getTargetType().getType(0));
		}

		/**
		 * @return Instantiators for this collection's initial elements
		 * @throws ModelInstantiationException If any initial elements could not be instantiated
		 */
		protected List<CollectionElement.CollectionPopulator<T>> instantiateElements() throws ModelInstantiationException {
			return BetterList.of2(theElements.stream(), el -> el.create());
		}
	}

	/**
	 * {@link AbstractCollectionDef} instantiator
	 *
	 * @param <T> The type of values in the collection
	 * @param <C> The type of the collection
	 */
	public static abstract class Instantiator<T, C extends ObservableCollection<T>> extends ModelValueElement.Abstract<C> {
		private final List<CollectionElement.CollectionPopulator<T>> theElements;
		private final String theAlias;

		/**
		 * @param interpreted The interpretation to instantiate
		 * @param elements The element populators for the collection
		 * @throws ModelInstantiationException If the collection could not be instantiated
		 */
		protected Instantiator(AbstractCollectionDef.Interpreted<T, C> interpreted, List<CollectionPopulator<T>> elements)
			throws ModelInstantiationException {
			super(interpreted);
			theElements = elements;
			theAlias = getModelPath() + ":" + interpreted.toString();
		}

		/** @return Elements defined to initialize this collection */
		public List<CollectionElement.CollectionPopulator<T>> getElements() {
			return Collections.unmodifiableList(theElements);
		}

		@Override
		public ModelValueInstantiator<C> getElementValue() {
			return (ModelValueInstantiator<C>) super.getElementValue();
		}

		@Override
		public void instantiate() throws ModelInstantiationException {
			super.instantiate();
			for (CollectionElement.CollectionPopulator<T> element : theElements)
				element.instantiate();
		}

		@Override
		public C evaluate(ModelSetInstance models) throws ModelInstantiationException, IllegalStateException {
			instantiate(models);
			if (getElementValue() != null)
				return (C) getElementValue().get(models)//
					.alias(theAlias);
			ObservableCollectionBuilder<T, ?> builder = create(models);
			builder.withDescription(theAlias);
			C collection = (C) builder.build();
			for (CollectionElement.CollectionPopulator<T> element : theElements)
				element.populateCollection(collection, models);
			return (C) collection.alias(theAlias);
		}

		@Override
		public C forModelCopy(C value, ModelSetInstance sourceModels, ModelSetInstance newModels) throws ModelInstantiationException {
			if (getElementValue() != null)
				return (C) getElementValue().forModelCopy(value, sourceModels, newModels)//
					.alias(theAlias);
			// Configured elements are merely initialized, not slaved, and the collection may have been modified
			// since it was created. There's no sense to making a re-initialized copy here.
			return value;
		}

		/**
		 * Creates the collection
		 *
		 * @param models The model instance
		 * @return The collection builder
		 * @throws ModelInstantiationException If the collection could not be instantiated
		 */
		protected abstract ObservableCollectionBuilder<T, ?> create(ModelSetInstance models) throws ModelInstantiationException;
	}
}