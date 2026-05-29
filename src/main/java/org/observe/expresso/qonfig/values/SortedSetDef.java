package org.observe.expresso.qonfig.values;

import java.util.Comparator;
import java.util.List;

import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableCollectionBuilder;
import org.observe.collect.ObservableSortedSet;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElement.Def;
import org.observe.expresso.qonfig.ModelValueElement;
import org.observe.expresso.qonfig.values.CollectionElement.CollectionPopulator;
import org.qommons.config.QonfigElementOrAddOn;

/** ExElement definition for the Expresso &lt;sorted-set> element. */
public class SortedSetDef extends AbstractSortedCollectionDef<ObservableSortedSet<?>> {
	/**
	 * @param parent The parent element of this value element
	 * @param qonfigType The Qonfig type of this value element
	 */
	public SortedSetDef(Def<?> parent, QonfigElementOrAddOn qonfigType) {
		super(parent, qonfigType, ModelTypes.SortedSet);
	}

	@Override
	protected SortedSetDef.Interpreted<?> interpret2(ExElement.Interpreted<?> parent) {
		return new SortedSetDef.Interpreted<>(this, parent);
	}

	/**
	 * {@link SortedSetDef} interpretation
	 *
	 * @param <T> The type of values in the collection
	 */
	public static class Interpreted<T> extends AbstractSortedCollectionDef.Interpreted<T, ObservableSortedSet<T>> {
		Interpreted(SortedSetDef definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public ModelValueElement<ObservableSortedSet<T>> create() throws ModelInstantiationException {
			return new SortedSetDef.SortedSetInstantiator<>(this, instantiateElements());
		}
	}

	/**
	 * {@link SortedSetDef} instantiator
	 *
	 * @param <T> The type of values in the collection
	 */
	public static class SortedSetInstantiator<T> extends SortedInstantiator<T, ObservableSortedSet<T>> {
		SortedSetInstantiator(SortedSetDef.Interpreted<T> interpreted, List<CollectionPopulator<T>> elements)
			throws ModelInstantiationException {
			super(interpreted, elements);
		}

		@Override
		protected ObservableCollectionBuilder.DistinctSortedBuilder<T, ?> create(Comparator<? super T> sort, ModelSetInstance models) {
			return ObservableCollection.<T> build().distinctSorted(sort);
		}
	}
}