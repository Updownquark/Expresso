package org.expresso.runtime.typed;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.expresso.runtime.ExpressoEnvironment;
import org.qommons.collect.SimpleMapEntry;

import com.google.common.reflect.TypeToken;

public abstract class TypedExpressoEnvironment<E extends TypedExpressoEnvironment<E>> implements ExpressoEnvironment<E> {
	private final TypedExpressoEnvironment<E> theParent;
	private final Map<String, TypedVariable<?>> theVariables;
	private final Map<String, List<TypedMethod<E, ?>>> theMethods;

	public TypedExpressoEnvironment(TypedExpressoEnvironment<E> parent) {
		theParent = parent;
		theVariables = new LinkedHashMap<>();
		theMethods = new LinkedHashMap<>();
	}

	@Override
	public Map<String, ? extends TypedVariable<?>> getVariables() {
		return Collections.unmodifiableMap(theVariables);
	}

	@Override
	public Map<String, ? extends List<? extends TypedMethod<E, ?>>> getMethods() {
		return new UnmodifiableListMap<>(theMethods);
	}

	public abstract <V> TypedVariable<V> declareVariable(String variableName, TypeToken<V> type, boolean isFinal);

	public abstract <R> TypedMethod<E, R> declareMethod(String methodName, TypeToken<R> returnType, List<TypeToken<?>> paramTypes,
		boolean varArgs);

	public static class UnmodifiableListMap<K, V, L extends List<? extends V>> implements Map<K, L> {
		private final Map<K, L> theWrapped;

		public UnmodifiableListMap(Map<K, L> wrapped) {
			theWrapped = wrapped;
		}

		@Override
		public int size() {
			return theWrapped.size();
		}

		@Override
		public boolean isEmpty() {
			return theWrapped.isEmpty();
		}

		@Override
		public boolean containsKey(Object key) {
			return theWrapped.containsKey(key);
		}

		@Override
		public boolean containsValue(Object value) {
			return theWrapped.containsValue(value);
		}

		@Override
		public L get(Object key) {
			L values = theWrapped.get(key);
			return values == null ? null : (L) Collections.unmodifiableList(values);
		}

		@Override
		public L put(K key, L value) {
			throw new UnsupportedOperationException();
		}

		@Override
		public L remove(Object key) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void putAll(Map<? extends K, ? extends L> m) {
			if (!m.isEmpty())
				throw new UnsupportedOperationException();
		}

		@Override
		public void clear() {
			if (!theWrapped.isEmpty())
				throw new UnsupportedOperationException();
		}

		@Override
		public Set<K> keySet() {
			return theWrapped.keySet();
		}

		@Override
		public Collection<L> values() {
			return theWrapped.values().stream().map(v -> (L) Collections.unmodifiableList(v))
				.collect(Collectors.toCollection(() -> new ArrayList<>(size())));
		}

		@Override
		public Set<Entry<K, L>> entrySet() {
			return theWrapped.entrySet().stream().map(e -> new SimpleMapEntry<>(e.getKey(), (L) Collections.unmodifiableList(e.getValue())))
				.collect(Collectors.toCollection(() -> new LinkedHashSet<>()));
		}

		@Override
		public int hashCode() {
			return theWrapped.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			return theWrapped.equals(obj);
		}

		@Override
		public String toString() {
			return theWrapped.toString();
		}
	}
}
