package org.observe.expresso.qonfig.values;

import java.util.Comparator;
import java.util.List;

import org.observe.assoc.ObservableMultiMap;
import org.observe.assoc.ObservableSortedMultiMap;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelType;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ModelValueInstantiator;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.expresso.qonfig.ExSort;
import org.observe.expresso.qonfig.ExpressoBaseV0_1;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.ModelValueElement;
import org.observe.util.TypeTokens;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

import com.google.common.reflect.TypeToken;

/**
 * ExElement definition for the Expresso &lt;sorted-multi-map>.
 *
 * @param <M> The sub-type of {@link ObservableSortedMultiMap} to create
 */
@ExElementTraceable(toolkit = ExpressoBaseV0_1.BASE,
	qonfigType = "sorted-multi-map",
	interpretation = SortedMultiMapDef.Interpreted.class,
	instance = SortedMultiMapDef.Instantiator.class)
public class SortedMultiMapDef<M extends ObservableSortedMultiMap<?, ?>> extends AbstractMultiMapDef<M> {
	private ExSort.ExRootSort theSort;

	/**
	 * @param parent The parent element of this value element
	 * @param qonfigType The Qonfig type of this value element
	 */
	public SortedMultiMapDef(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
		super(parent, qonfigType, (ModelType.DoubleTyped<M>) ModelTypes.SortedMultiMap);
	}

	/** @return The sorting specified for the map's keys */
	public ExSort.ExRootSort getSort() {
		return theSort;
	}

	@Override
	protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
		super.doUpdate(session);
		theSort = syncChild(ExSort.ExRootSort.class, theSort, session, "sort");
	}

	@Override
	protected SortedMultiMapDef.Interpreted<?, ?, ?> interpret2(ExElement.Interpreted<?> parent) {
		return new SortedMultiMapDef.Interpreted<>(this, parent);
	}

	/**
	 * {@link SortedMultiMapDef} interpretation
	 *
	 * @param <K> The key type of the map
	 * @param <V> The value type of the map
	 * @param <M> The sub-type of {@link ObservableSortedMultiMap} to create
	 */
	public static class Interpreted<K, V, M extends ObservableSortedMultiMap<K, V>> extends AbstractMultiMapDef.Interpreted<K, V, M> {
		private ExSort.ExRootSort.Interpreted<K> theSort;
		private Comparator<? super K> theDefaultSorting;

		Interpreted(SortedMultiMapDef<?> definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public SortedMultiMapDef<M> getDefinition() {
			return (SortedMultiMapDef<M>) super.getDefinition();
		}

		/** @return The sorting specified for the map's keys */
		public ExSort.ExRootSort.Interpreted<K> getSort() {
			return theSort;
		}

		@Override
		protected void doUpdate() throws ExpressoInterpretationException {
			super.doUpdate();
			theSort = syncChild(getDefinition().getSort(), theSort, def -> (ExSort.ExRootSort.Interpreted<K>) def.interpret(this),
				s -> s.update((TypeToken<K>) getType().getType(0)));
			if (theSort == null) {
				theDefaultSorting = ExSort.getDefaultSorting(TypeTokens.getRawType(getKeyType()));
				if (theDefaultSorting == null)
					throw new ExpressoInterpretationException(
						"No default sorting available for type " + getKeyType() + ". Specify sorting", reporting().getPosition(), 0);
			}
		}

		/**
		 * @return The instantiator for the sorting for the multi-map's keys
		 * @throws ModelInstantiationException If the sorting could not be instantiated
		 */
		protected ModelValueInstantiator<Comparator<? super K>> instantiateSort() throws ModelInstantiationException {
			if (theSort != null)
				return theSort.instantiateSort();
			else
				return ModelValueInstantiator.literal(theDefaultSorting, "default");
		}

		@Override
		public ModelValueElement<M> create() throws ModelInstantiationException, RuntimeException {
			return new SortedMultiMapDef.SortedInstantiator<>(this, instantiateEntries());
		}
	}

	/**
	 * {@link SortedMultiMapDef} instantiator
	 *
	 * @param <K> The key type of the map
	 * @param <V> The value type of the map
	 * @param <M> The sub-type of {@link ObservableSortedMultiMap} to create
	 */
	public static class SortedInstantiator<K, V, M extends ObservableSortedMultiMap<K, V>> extends Instantiator<K, V, M> {
		private final ModelValueInstantiator<Comparator<? super K>> theSort;

		SortedInstantiator(SortedMultiMapDef.Interpreted<K, V, M> interpreted, List<MapEntry.MapPopulator<K, V>> entries)
			throws ModelInstantiationException {
			super(interpreted, entries);
			theSort = interpreted.instantiateSort();
		}

		/** @return The sorting specified for the map's keys */
		public ModelValueInstantiator<Comparator<? super K>> getSort() {
			return theSort;
		}

		@Override
		public void instantiate() throws ModelInstantiationException {
			super.instantiate();
			theSort.instantiate();
		}

		@Override
		protected ObservableMultiMap.Builder<K, V, ?> create(ModelSetInstance models) throws ModelInstantiationException {
			Comparator<? super K> sort = theSort.get(models);
			return ObservableMultiMap.<K, V> build().sortedBy(sort);
		}
	}
}