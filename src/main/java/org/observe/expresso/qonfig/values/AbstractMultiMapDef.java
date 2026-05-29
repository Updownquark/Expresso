package org.observe.expresso.qonfig.values;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.observe.assoc.ObservableMultiMap;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelType;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.ModelValueElement;
import org.observe.util.TypeTokens;
import org.qommons.QommonsUtils;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

import com.google.common.reflect.TypeToken;

/**
 * Abstract ExElement definition for the Expresso &lt;multi-map> element tagged with &lt;int-map>.
 *
 * @param <M> The sub-type of {@link ObservableMultiMap} that this element creates
 */
public abstract class AbstractMultiMapDef<M extends ObservableMultiMap<?, ?>> extends
ModelValueElement.Def.DoubleTyped<M, ModelValueElement<M>> implements ModelValueElement.CompiledSynth<M, ModelValueElement<M>> {
	private final List<MapEntry> theEntries;

	/**
	 * @param parent The parent element of this value element
	 * @param qonfigType The Qonfig type of this value element
	 * @param modelType The multi-map model type for the value
	 */
	protected AbstractMultiMapDef(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType, ModelType.DoubleTyped<M> modelType) {
		super(parent, qonfigType, modelType);
		theEntries = new ArrayList<>();
	}

	/** @return The entries to populate this map initially */
	public List<MapEntry> getEntries() {
		return Collections.unmodifiableList(theEntries);
	}

	@Override
	protected boolean useWrapperType() {
		return true;
	}

	@Override
	protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
		super.doUpdate(session);
		if (session.isInstance("int-map") != null)
			syncChildren(MapEntry.class, theEntries, session.asElement("int-map").forChildren("entry"));
	}

	@Override
	protected void doPrepare(ExpressoQIS session) { // Nothing to do
	}

	@Override
	public AbstractMultiMapDef.Interpreted<?, ?, M> interpretValue(ExElement.Interpreted<?> parent) {
		return (AbstractMultiMapDef.Interpreted<?, ?, M>) interpret2(parent);
	}

	/**
	 * @param parent The parent element for the interpreted multi-map
	 * @return The interpreted multi-map
	 */
	protected abstract AbstractMultiMapDef.Interpreted<?, ?, ?> interpret2(ExElement.Interpreted<?> parent);

	/**
	 * {@link AbstractMultiMapDef} interpretation
	 *
	 * @param <K> The key type for the multi-map
	 * @param <V> The value type for the multi-map
	 * @param <M> The sub-type of {@link ObservableMultiMap} to create
	 */
	public static abstract class Interpreted<K, V, M extends ObservableMultiMap<K, V>>
	extends ModelValueElement.Def.DoubleTyped.Interpreted<M, M, ModelValueElement<M>>
	implements ModelValueElement.InterpretedSynth<M, M, ModelValueElement<M>> {
		private final List<MapEntry.Interpreted<K, V>> theEntries;

		/**
		 * @param definition The definition to interpret
		 * @param parent The parent element of this multi-map element
		 */
		protected Interpreted(AbstractMultiMapDef<?> definition, ExElement.Interpreted<?> parent) {
			super((AbstractMultiMapDef<M>) definition, parent);
			theEntries = new ArrayList<>();
		}

		@Override
		public AbstractMultiMapDef<M> getDefinition() {
			return (AbstractMultiMapDef<M>) super.getDefinition();
		}

		/** @return The entries to populate this map initially */
		public List<MapEntry.Interpreted<K, V>> getEntries() {
			return Collections.unmodifiableList(theEntries);
		}

		@Override
		public List<? extends InterpretedValueSynth<?, ?>> getComponents() {
			return Collections.emptyList(); // Elements are initialization only, this value is independent (fundamental)
		}

		@Override
		protected void doUpdate() throws ExpressoInterpretationException {
			super.doUpdate();
			ModelInstanceType<M, M> type = getType();
			TypeToken<K> keyType = (TypeToken<K>) type.getType(0);
			TypeToken<V> valueType = (TypeToken<V>) type.getType(1);
			syncChildren(getDefinition().getEntries(), theEntries, d -> (MapEntry.Interpreted<K, V>) d.interpret(this),
				entry -> entry.update(keyType, valueType));
		}

		/** @return The key type of the map */
		protected TypeToken<K> getKeyType() {
			return TypeTokens.get().wrap((TypeToken<K>) getType().getType(0));
		}

		/** @return The value type of the map */
		protected TypeToken<V> getValueType() {
			return TypeTokens.get().wrap((TypeToken<V>) getType().getType(1));
		}

		/**
		 * @return The instantiated entries to populate the initial content for the map
		 * @throws ModelInstantiationException If any initial content cannot be instantiated
		 */
		protected List<MapEntry.MapPopulator<K, V>> instantiateEntries() throws ModelInstantiationException {
			return QommonsUtils.filterMapE(theEntries, null, e -> e.create());
		}
	}

	/**
	 * {@link AbstractMultiMapDef} instantiator
	 *
	 * @param <K> The key type for the multi-map
	 * @param <V> The value type for the multi-map
	 * @param <M> The sub-type of {@link ObservableMultiMap} to create
	 */
	public static abstract class Instantiator<K, V, M extends ObservableMultiMap<K, V>> extends ModelValueElement.Abstract<M> {
		private final List<MapEntry.MapPopulator<K, V>> theEntries;
		private final String theAlias;

		/**
		 * @param interpreted The interpretation to instantiate
		 * @param entries The entries to populate the map initially
		 * @throws ModelInstantiationException If this multi-map cannot be instantiated
		 */
		protected Instantiator(AbstractMultiMapDef.Interpreted<K, V, M> interpreted, List<MapEntry.MapPopulator<K, V>> entries)
			throws ModelInstantiationException {
			super(interpreted);
			theEntries = entries;
			theAlias = getModelPath() + ":" + interpreted.toString();
		}

		/** @return The entries to populate this map initially */
		public List<MapEntry.MapPopulator<K, V>> getEntries() {
			return theEntries;
		}

		@Override
		public void instantiate() throws ModelInstantiationException {
			for (MapEntry.MapPopulator<?, ?> entry : theEntries)
				entry.instantiate();
		}

		@Override
		public M evaluate(ModelSetInstance models) throws ModelInstantiationException, IllegalStateException {
			instantiate(models);
			ObservableMultiMap.Builder<K, V, ?> builder = create(models);
			if (getModelPath() != null)
				builder.withDescription(getModelPath());
			M map = (M) builder.build(models.getUntil());
			for (MapEntry.MapPopulator<K, V> entry : theEntries)
				entry.populateMultiMap(map, models);
			return (M) map.alias(theAlias);
		}

		@Override
		public M forModelCopy(M value, ModelSetInstance sourceModels, ModelSetInstance newModels) throws ModelInstantiationException {
			// Configured entries are merely initialized, not slaved, and the map may have been modified
			// since it was created. There's no sense to making a re-initialized copy here.
			return value;
		}

		/**
		 * Creates the multi-map
		 *
		 * @param models The model instance to use to create the map
		 * @return The builder for the new multi-map
		 * @throws ModelInstantiationException If the map cannot be instantiated
		 */
		protected abstract ObservableMultiMap.Builder<K, V, ?> create(ModelSetInstance models) throws ModelInstantiationException;
	}
}