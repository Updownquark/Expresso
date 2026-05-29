package org.observe.expresso.qonfig.values;

import java.util.Comparator;
import java.util.List;

import org.observe.collect.ObservableCollectionBuilder;
import org.observe.collect.ObservableCollectionBuilder.SortedBuilder;
import org.observe.collect.ObservableSortedCollection;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelType;
import org.observe.expresso.ObservableExpression;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ModelValueInstantiator;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.expresso.qonfig.ExSort;
import org.observe.expresso.qonfig.ExpressoBaseV0_1;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.QonfigChildGetter;
import org.observe.expresso.qonfig.values.CollectionElement.CollectionPopulator;
import org.observe.util.TypeTokens;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

import com.google.common.reflect.TypeToken;

/**
 * Abstract ExElement definition for the Expresso &lt;sorted-list> element.
 *
 * @param <C> The sub-type of {@link ObservableSortedCollection} that this element creates
 */
@ExElementTraceable(toolkit = ExpressoBaseV0_1.BASE,
qonfigType = "sorted-model-value",
interpretation = AbstractSortedCollectionDef.Interpreted.class,
instance = AbstractSortedCollectionDef.SortedInstantiator.class)
public abstract class AbstractSortedCollectionDef<C extends ObservableSortedCollection<?>> extends AbstractCollectionDef<C> {
	private ExSort.ExRootSort theSort;

	/**
	 * @param parent The parent element of this value element
	 * @param qonfigType The Qonfig type of this value element
	 * @param modelType The sorted collection model type for the value
	 */
	protected AbstractSortedCollectionDef(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType,
		ModelType.SingleTyped<C> modelType) {
		super(parent, qonfigType, modelType);
	}

	/** @return The sorting specified for the collection */
	@QonfigChildGetter("sort")
	public ExSort.ExRootSort getSort() {
		return theSort;
	}

	@Override
	protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
		super.doUpdate(session);
		theSort = syncChild(ExSort.ExRootSort.class, theSort, session, "sort");
		if (getElementValue() != null && getElementValue().getExpression() != ObservableExpression.EMPTY && theSort != null)
			reporting().warn("Sorting will not be used if the value of the collection is specified");
	}

	@Override
	protected abstract AbstractSortedCollectionDef.Interpreted<?, ?> interpret2(ExElement.Interpreted<?> parent);

	/**
	 * {@link AbstractSortedCollectionDef} interpretation
	 *
	 * @param <T> The type of values in the collection
	 * @param <C> The type of the collection
	 */
	public static abstract class Interpreted<T, C extends ObservableSortedCollection<T>>
	extends AbstractCollectionDef.Interpreted<T, C> {
		private ExSort.ExRootSort.Interpreted<T> theSort;
		private Comparator<? super T> theDefaultSorting;

		/**
		 * @param definition The definition to interpret
		 * @param parent The parent element for this collection element
		 */
		protected Interpreted(AbstractSortedCollectionDef<?> definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public AbstractSortedCollectionDef<C> getDefinition() {
			return (AbstractSortedCollectionDef<C>) super.getDefinition();
		}

		/** @return The sorting specified for the collection */
		public ExSort.ExRootSort.Interpreted<T> getSort() {
			return theSort;
		}

		@Override
		public void doUpdate() throws ExpressoInterpretationException {
			super.doUpdate();
			if (getElementValue() != null) { // No sorting needed
			} else
				theSort = syncChild(getDefinition().getSort(), theSort, def -> (ExSort.ExRootSort.Interpreted<T>) def.interpret(this),
					s -> s.update((TypeToken<T>) getType().getType(0)));
			if (theSort == null) {
				theDefaultSorting = ExSort.getDefaultSorting(TypeTokens.getRawType(getValueType()));
				if (theDefaultSorting == null)
					throw new ExpressoInterpretationException(
						"No default sorting available for type " + getValueType() + ". Specify sorting", reporting().getPosition(), 0);
			}
		}

		/**
		 * @return The instantiator for this collection's sorting
		 * @throws ModelInstantiationException If the sorting could not be instantiated
		 */
		protected ModelValueInstantiator<Comparator<? super T>> instantiateSort() throws ModelInstantiationException {
			if (theSort != null)
				return theSort.instantiateSort();
			else
				return ModelValueInstantiator.literal(theDefaultSorting, "default");
		}
	}

	/**
	 * {@link AbstractSortedCollectionDef} instantiator
	 *
	 * @param <T> The type of values in the collection
	 * @param <C> The type of the collection
	 */
	public static abstract class SortedInstantiator<T, C extends ObservableSortedCollection<T>> extends Instantiator<T, C> {
		private final ModelValueInstantiator<Comparator<? super T>> theSort;

		/**
		 * @param interpreted The interpretation to instantiate
		 * @param elements Instantiators for the initial elements to populate the collection
		 * @throws ModelInstantiationException If the collection or any of its initial content could not be instantiated
		 */
		protected SortedInstantiator(AbstractSortedCollectionDef.Interpreted<T, C> interpreted, List<CollectionPopulator<T>> elements)
			throws ModelInstantiationException {
			super(interpreted, elements);
			theSort = interpreted.instantiateSort();
		}

		/** @return The sorting specified for the collection */
		public ModelValueInstantiator<Comparator<? super T>> comparator() {
			return theSort;
		}

		@Override
		public void instantiate() throws ModelInstantiationException {
			super.instantiate();
			theSort.instantiate();
		}

		@Override
		protected ObservableCollectionBuilder.SortedBuilder<T, ?> create(ModelSetInstance models) throws ModelInstantiationException {
			Comparator<? super T> sort = theSort.get(models);
			return create(sort, models);
		}

		/**
		 *
		 * Creates the collection
		 *
		 * @param sort The sorting for the collection
		 * @param models The model instance
		 * @return The sorted collection builder
		 * @throws ModelInstantiationException If the collection cannot be instantiated
		 */
		protected abstract SortedBuilder<T, ?> create(Comparator<? super T> sort, ModelSetInstance models)
			throws ModelInstantiationException;
	}
}