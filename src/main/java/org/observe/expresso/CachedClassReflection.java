package org.observe.expresso;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;

import org.observe.util.TypeTokens;
import org.qommons.ArrayUtils;
import org.qommons.Named;

import com.google.common.reflect.TypeToken;

/**
 * Enables faster access to fields and constructors
 *
 * @param <T> The type whose constructors and methods are cached in this instance
 */
public class CachedClassReflection<T> {
	private static final Map<Class<?>, CachedClassReflection<?>> ALL_CLASSES = new ConcurrentHashMap<>();

	/**
	 * @param <T> The type to get cached reflection information for
	 * @param clazz The class to get cached reflection information for
	 * @return Cached reflection information for the given class
	 */
	public static <T> CachedClassReflection<T> get(Class<T> clazz) {
		return (CachedClassReflection<T>) ALL_CLASSES.computeIfAbsent(clazz, CachedClassReflection::new);
	}

	/** The type token of this type */
	public final TypeToken<T> type;
	private ClassExecutableInfo<Constructor<?>> theConstructors;
	private final Map<String, ClassMethodInfo> theMethods;

	CachedClassReflection(Class<T> type) {
		this.type = TypeTokens.get().of(type);
		theMethods = new HashMap<>();
		for (Constructor<?> constructor : type.getConstructors()) {
			if (theConstructors == null)
				theConstructors = new ClassExecutableInfo<>();
			theConstructors.add(constructor);
		}
		for (Method method : type.getMethods()) {
			theMethods.computeIfAbsent(method.getName(), ClassMethodInfo::new).add(method);
		}
		if (type.isInterface()) {
			for (Method method : Object.class.getMethods())
				theMethods.computeIfAbsent(method.getName(), ClassMethodInfo::new).add(method);
		}
	}

	/**
	 * @param argCount The number of arguments in the invocation
	 * @return An iterable of all constructors that can be called with the given number of arguments
	 */
	public Iterable<Constructor<?>> getConstructors(int argCount) {
		if (theConstructors == null)
			return Collections.emptyList();
		else
			return theConstructors.get(argCount);
	}

	/**
	 * @param name The name of the method to invoke
	 * @param staticMethods Whether the method being invoked is static or non-static
	 * @param argCount The number of arguments in the invocation
	 * @return An iterable of all methods with the given name that can be called with the given number of arguments
	 */
	public Iterable<Method> getMethods(String name, boolean staticMethods, int argCount) {
		ClassMethodInfo methods = theMethods.get(name);
		if (methods == null)
			return Collections.emptyList();
		return methods.get(staticMethods, argCount);
	}

	/**
	 * A structure holding constructors or methods with the same name
	 *
	 * @param <X> The type of executable (constructor or method) being held
	 */
	public static class ClassExecutableInfo<X extends Executable> {
		private Executable[][] theNonVarArgsItems;
		private Executable[][] theVarArgsItems;

		/**
		 * @param argCount The number of arguments in the invocation
		 * @return An iterable of all executables that can be called with the given number of arguments
		 */
		public Iterable<X> get(int argCount) {
			int size = 0;
			boolean nva = theNonVarArgsItems != null && argCount < theNonVarArgsItems.length;
			if (nva)
				size++;
			if (theVarArgsItems != null)
				size += Math.min(argCount, theVarArgsItems.length);
			if (size == 0)
				return Collections.emptyList();
			Executable[][] array = new Executable[size][];
			int index = 0;
			if (nva)
				array[index++] = theNonVarArgsItems[argCount];
			if (theVarArgsItems != null) {
				for (int i = Math.min(argCount, theVarArgsItems.length - 1); i >= 0; i--)
					array[index++] = theVarArgsItems[i];
			}
			return new MethodCollection<>(array);
		}

		/**
		 * Adds an executable to this reflection info instance
		 *
		 * @param item The executable to add
		 */
		public void add(X item) {
			int paramCount = item.getParameterCount();
			if (item.isVarArgs()) {
				if (theVarArgsItems == null)
					theVarArgsItems = new Executable[paramCount][];
				else if (paramCount >= theVarArgsItems.length) {
					Executable[][] temp = theVarArgsItems;
					theVarArgsItems = new Executable[paramCount][];
					System.arraycopy(temp, 0, theVarArgsItems, 0, temp.length);
				}
				if (theVarArgsItems[paramCount - 1] == null)
					theVarArgsItems[paramCount - 1] = new Executable[] { item };
				else
					theVarArgsItems[paramCount - 1] = ArrayUtils.add(theVarArgsItems[paramCount - 1], item);
			} else {
				if (theNonVarArgsItems == null)
					theNonVarArgsItems = new Executable[paramCount + 1][];
				else if (paramCount >= theNonVarArgsItems.length) {
					Executable[][] temp = theNonVarArgsItems;
					theNonVarArgsItems = new Executable[paramCount + 1][];
					System.arraycopy(temp, 0, theNonVarArgsItems, 0, temp.length);
				}
				if (theNonVarArgsItems[paramCount] == null)
					theNonVarArgsItems[paramCount] = new Executable[] { item };
				else
					theNonVarArgsItems[paramCount] = ArrayUtils.add(theNonVarArgsItems[paramCount], item);
			}
		}
	}

	private static class ClassMethodInfo implements Named {
		private final String theName;
		private ClassExecutableInfo<Method> theStaticMethods;
		private ClassExecutableInfo<Method> theInstanceMethods;

		ClassMethodInfo(String name) {
			theName = name;
		}

		@Override
		public String getName() {
			return theName;
		}

		Iterable<Method> get(boolean staticMethods, int argCount) {
			ClassExecutableInfo<Method> methods = staticMethods ? theStaticMethods : theInstanceMethods;
			if (methods == null)
				return Collections.emptyList();
			return methods.get(argCount);
		}

		void add(Method method) {
			ClassExecutableInfo<Method> methods;
			if ((method.getModifiers() & Modifier.STATIC) != 0) {
				if (theStaticMethods == null)
					theStaticMethods = new ClassExecutableInfo<>();
				methods = theStaticMethods;
			} else {
				if (theInstanceMethods == null)
					theInstanceMethods = new ClassExecutableInfo<>();
				methods = theInstanceMethods;
			}
			methods.add(method);
		}
	}

	private static class MethodCollection<X extends Executable> implements Iterable<X> {
		private final Executable[][] theMethods;

		MethodCollection(Executable[][] methods) {
			theMethods = methods;
		}

		@Override
		public Iterator<X> iterator() {
			return new Iterator<X>() {
				private int theIndex0;
				private int theIndex1;
				@Override
				public boolean hasNext() {
					while (theIndex0 < theMethods.length) {
						Executable[] methods = theMethods[theIndex0];
						if (methods != null && theIndex1 < methods.length)
							return true;
						else {
							theIndex0++;
							theIndex1 = 0;
						}
					}
					return false;
				}

				@Override
				public X next() {
					if (!hasNext())
						throw new NoSuchElementException();
					Executable m = theMethods[theIndex0][theIndex1];
					theIndex1++;
					return (X) m;
				}
			};
		}
	}
}
