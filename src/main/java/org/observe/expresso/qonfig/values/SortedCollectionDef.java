package org.observe.expresso.qonfig.values;

import java.util.Comparator;
import java.util.List;

import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableCollectionBuilder;
import org.observe.collect.ObservableSortedCollection;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElement.Def;
import org.observe.expresso.qonfig.ModelValueElement;
import org.observe.expresso.qonfig.values.CollectionElement.CollectionPopulator;
import org.qommons.config.QonfigElementOrAddOn;

/** ExElement definition for the Expresso &lt;sorted-list> element. */
public class SortedCollectionDef extends AbstractSortedCollectionDef<ObservableSortedCollection<?>> {
	/**
	 * @param parent The parent element of this value element
	 * @param qonfigType The Qonfig type of this value element
	 */
	public SortedCollectionDef(Def<?> parent, QonfigElementOrAddOn qonfigType) {
		super(parent, qonfigType, ModelTypes.SortedCollection);
	}

	@Override
	protected SortedCollectionDef.Interpreted<?> interpret2(ExElement.Interpreted<?> parent) {
		return new SortedCollectionDef.Interpreted<>(this, parent);
	}

	/**
	 * {@link SortedCollectionDef} interpretation
	 *
	 * @param <T> The type of values in the collection
	 */
	public static class Interpreted<T> extends AbstractSortedCollectionDef.Interpreted<T, ObservableSortedCollection<T>> {
		Interpreted(AbstractSortedCollectionDef<?> definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public ModelValueElement<ObservableSortedCollection<T>> create() throws ModelInstantiationException {
			return new SortedCollectionDef.SimpleSortedInstantiator<>(this, instantiateElements());
		}
	}

	/**
	 * {@link SortedCollectionDef} instantiator
	 *
	 * @param <T> The type of values in the collection
	 */
	public static class SimpleSortedInstantiator<T> extends SortedInstantiator<T, ObservableSortedCollection<T>> {
		SimpleSortedInstantiator(SortedCollectionDef.Interpreted<T> interpreted, List<CollectionPopulator<T>> elements)
			throws ModelInstantiationException {
			super(interpreted, elements);
		}

		@Override
		protected ObservableCollectionBuilder.SortedBuilder<T, ?> create(Comparator<? super T> sort, ModelSetInstance models) {
			return ObservableCollection.<T> build().sortBy(sort);
		}
	}
}