package org.observe.expresso.qonfig.values;

import java.util.Comparator;
import java.util.List;

import org.observe.assoc.ObservableMap;
import org.observe.assoc.ObservableSortedMap;
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
import org.observe.expresso.qonfig.QonfigChildGetter;
import org.observe.util.TypeTokens;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

import com.google.common.reflect.TypeToken;

/**
 * ExElement definition for the Expresso &lt;sorted-map>.
 *
 * @param <M> The sub-type of {@link ObservableSortedMap} to create
 */
@ExElementTraceable(toolkit = ExpressoBaseV0_1.BASE,
	qonfigType = "sorted-map",
	interpretation = SortedMapDef.Interpreted.class,
	instance = SortedMapDef.Instantiator.class)
public class SortedMapDef<M extends ObservableSortedMap<?, ?>> extends AbstractMapDef<M> {
	private ExSort.ExRootSort theSort;

	/**
	 * @param parent The parent element of this value element
	 * @param qonfigType The Qonfig type of this value element
	 */
	public SortedMapDef(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
		super(parent, qonfigType, (ModelType.DoubleTyped<M>) ModelTypes.SortedMap);
	}

	/** @return The sorting specified for the map's keys */
	@QonfigChildGetter("sort")
	public ExSort.ExRootSort getSort() {
		return theSort;
	}

	@Override
	protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
		super.doUpdate(session);
		theSort = syncChild(ExSort.ExRootSort.class, theSort, session, "sort");
	}

	@Override
	protected SortedMapDef.Interpreted<?, ?, ?> interpret2(ExElement.Interpreted<?> parent) {
		return new SortedMapDef.Interpreted<>(this, parent);
	}

	/**
	 * {@link SortedMapDef} interpretation
	 *
	 * @param <K> The key type of the map
	 * @param <V> The value type of the map
	 * @param <M> The sub-type of {@link ObservableSortedMap} to create
	 */
	public static class Interpreted<K, V, M extends ObservableSortedMap<K, V>> extends AbstractMapDef.Interpreted<K, V, M> {
		private ExSort.ExRootSort.Interpreted<K> theSort;
		private Comparator<? super K> theDefaultSorting;

		Interpreted(SortedMapDef<?> definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public SortedMapDef<M> getDefinition() {
			return (SortedMapDef<M>) super.getDefinition();
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
		 * @return The instantiated sorting for the map
		 * @throws ModelInstantiationException If the sorting cannot be instantiated
		 */
		protected ModelValueInstantiator<Comparator<? super K>> instantiateSort() throws ModelInstantiationException {
			if (theSort != null)
				return theSort.instantiateSort();
			else
				return ModelValueInstantiator.literal(theDefaultSorting, "default");
		}

		@Override
		public ModelValueElement<M> create() throws ModelInstantiationException {
			return new SortedMapDef.SortedInstantiator<>(this, instantiateEntries());
		}
	}

	/**
	 * {@link SortedMapDef} instantiator
	 *
	 * @param <K> The key type of the map
	 * @param <V> The value type of the map
	 * @param <M> The sub-type of {@link ObservableSortedMap} to create
	 */
	public static class SortedInstantiator<K, V, M extends ObservableSortedMap<K, V>> extends Instantiator<K, V, M> {
		private final ModelValueInstantiator<Comparator<? super K>> theSort;

		SortedInstantiator(SortedMapDef.Interpreted<K, V, M> interpreted, List<MapEntry.MapPopulator<K, V>> entries)
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
		protected ObservableMap.Builder<K, V, ?> create(ModelSetInstance models) throws ModelInstantiationException {
			Comparator<? super K> sort = theSort.get(models);
			return ObservableSortedMap.build(sort);
		}
	}
}