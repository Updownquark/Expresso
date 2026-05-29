package org.observe.expresso.qonfig.values;

import java.util.List;

import org.observe.assoc.ObservableMultiMap;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ModelValueElement;
import org.observe.expresso.qonfig.values.AbstractMultiMapDef.Instantiator;
import org.observe.expresso.qonfig.values.MapEntry.MapPopulator;
import org.qommons.config.QonfigElementOrAddOn;

/** ExElement definition for the Expresso &lt;multi-map>. */
public class PlainMultiMapDef extends AbstractMultiMapDef<ObservableMultiMap<?, ?>> {
	/**
	 * @param parent The parent element of this value element
	 * @param qonfigType The Qonfig type of this value element
	 */
	public PlainMultiMapDef(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
		super(parent, qonfigType, ModelTypes.MultiMap);
	}

	@Override
	protected PlainMultiMapDef.Interpreted<?, ?> interpret2(ExElement.Interpreted<?> parent) {
		return new PlainMultiMapDef.Interpreted<>(this, parent);
	}

	/**
	 * {@link PlainMultiMapDef} interpretation
	 *
	 * @param <K> The key type of the map
	 * @param <V> The value type of the map
	 */
	public static class Interpreted<K, V> extends AbstractMultiMapDef.Interpreted<K, V, ObservableMultiMap<K, V>> {
		Interpreted(PlainMultiMapDef definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public ModelValueElement<ObservableMultiMap<K, V>> create() throws ModelInstantiationException {
			return new PlainMultiMapDef.PlainInstantiator<>(this, instantiateEntries());
		}
	}

	/**
	 * {@link PlainMultiMapDef} instantiator
	 *
	 * @param <K> The key type of the map
	 * @param <V> The value type of the map
	 */
	public static class PlainInstantiator<K, V> extends Instantiator<K, V, ObservableMultiMap<K, V>> {
		PlainInstantiator(PlainMultiMapDef.Interpreted<K, V> interpreted, List<MapEntry.MapPopulator<K, V>> entries)
			throws ModelInstantiationException {
			super(interpreted, entries);
		}

		@Override
		protected ObservableMultiMap.Builder<K, V, ?> create(ModelSetInstance models) throws ModelInstantiationException {
			return ObservableMultiMap.build();
		}
	}
}