package org.observe.expresso.qonfig.values;

import java.util.List;

import org.observe.assoc.ObservableMap;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElement.Def;
import org.observe.expresso.qonfig.ModelValueElement;
import org.qommons.config.QonfigElementOrAddOn;

/** ExElement definition for the Expresso &lt;map>. */
public class PlainMapDef extends AbstractMapDef<ObservableMap<?, ?>> {
	/**
	 * @param parent The parent element of this value element
	 * @param qonfigType The Qonfig type of this value element
	 */
	public PlainMapDef(Def<?> parent, QonfigElementOrAddOn qonfigType) {
		super(parent, qonfigType, ModelTypes.Map);
	}

	@Override
	protected PlainMapDef.Interpreted<?, ?> interpret2(ExElement.Interpreted<?> parent) {
		return new PlainMapDef.Interpreted<>(this, parent);
	}

	/**
	 * {@link PlainMapDef} interpretation
	 *
	 * @param <K> The key type of the map
	 * @param <V> The value type of the map
	 */
	public static class Interpreted<K, V> extends AbstractMapDef.Interpreted<K, V, ObservableMap<K, V>> {
		Interpreted(PlainMapDef definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public ModelValueElement<ObservableMap<K, V>> create() throws ModelInstantiationException {
			return new PlainMapDef.PlainInstantiator<>(this, instantiateEntries());
		}
	}

	/**
	 * {@link PlainMapDef} instantiator
	 *
	 * @param <K> The key type of the map
	 * @param <V> The value type of the map
	 */
	public static class PlainInstantiator<K, V> extends Instantiator<K, V, ObservableMap<K, V>> {
		PlainInstantiator(PlainMapDef.Interpreted<K, V> interpreted, List<MapEntry.MapPopulator<K, V>> entries)
			throws ModelInstantiationException {
			super(interpreted, entries);
		}

		@Override
		protected ObservableMap.Builder<K, V, ?> create(ModelSetInstance models) throws ModelInstantiationException {
			return ObservableMap.build();
		}
	}
}