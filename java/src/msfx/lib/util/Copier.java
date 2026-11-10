/*
 * Copyright (c) 2026 Miquel Sas
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package msfx.lib.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * One deep copy operation, shared by all the objects it reaches.
 * <p>
 * The copier remembers each object already copied, so an object reached more
 * than once is copied once, and the copy is referenced in the same places:
 * shared and circular references are preserved.
 * <p>
 * How each value is copied:
 * <ul>
 * <li>{@link Copyable} objects, including {@link Properties}, copy themselves
 * through {@link Copyable#copy(Copier)}.</li>
 * <li>Maps ({@link HashMap}, {@link LinkedHashMap}, {@link TreeMap}) and
 * collections ({@link ArrayList}, {@link LinkedList}, {@link HashSet},
 * {@link LinkedHashSet}, {@link TreeSet}) of the accepted classes are copied
 * into a new instance of the same class, recursively. Tree maps and sets keep
 * their comparator. Map keys are shared, not copied, since keys should be
 * immutable.</li>
 * <li>Any other accepted value is assumed immutable (scalars, strings, date and
 * time) and is shared, not copied.</li>
 * <li>Values of classes not accepted, such as enums stored with
 * {@link Properties#putObject(Object, Object)}, are shared as is.</li>
 * </ul>
 * A copier is meant for a single copy operation, and is not thread safe.
 *
 * @author Miquel Sas
 */
public final class Copier {
	/**
	 * The classes whose values are copied, matched by exact class.
	 */
	private final Set<Class<?>> acceptedClasses;
	/**
	 * The objects already copied, original to copy.
	 */
	private final Map<Object, Object> copies = new IdentityHashMap<>();

	/**
	 * Creates a copier that copies the {@link Maps#VALUE_CLASSES}.
	 */
	public Copier() {
		this(Maps.VALUE_CLASSES);
	}

	/**
	 * @param acceptedClasses The classes whose values are copied, matched by
	 *                        exact class.
	 */
	Copier(Set<Class<?>> acceptedClasses) {
		this.acceptedClasses = Objects.requireNonNull(acceptedClasses, "Accepted classes can not be null");
	}

	/**
	 * Copies a value, or returns its copy if already copied in this operation.
	 *
	 * @param <V>   The value type.
	 * @param value The value.
	 * @return The copy, or the value itself if it is immutable or its class is
	 *         not accepted.
	 * @throws IllegalArgumentException If the value, or any value it contains, is
	 *                                  null, or an accepted map or collection
	 *                                  class can not be copied.
	 */
	@SuppressWarnings("unchecked")
	public <V> V copy(V value) {
		if (value == null) {
			throw new IllegalArgumentException("Value can not be null");
		}
		Object copy = copies.get(value);
		if (copy != null) {
			return (V) copy;
		}
		if (value instanceof Copyable<?> copyable) {
			copy = copyable.copy(this);
			// Also for implementations that do not need to register.
			copies.putIfAbsent(value, copy);
			return (V) copy;
		}
		if (!acceptedClasses.contains(value.getClass())) {
			return value;
		}
		if (value instanceof Map<?, ?> map) {
			return (V) copyMap(map);
		}
		if (value instanceof Collection<?> collection) {
			return (V) copyCollection(collection);
		}
		return value;
	}

	/**
	 * Registers the copy of an object, before filling it, so that references
	 * back to the original resolve to this copy.
	 *
	 * @param original The original object.
	 * @param copy     Its copy, still empty.
	 * @throws NullPointerException If the original or the copy is null.
	 */
	public void register(Object original, Object copy) {
		Objects.requireNonNull(original, "Original can not be null");
		Objects.requireNonNull(copy, "Copy can not be null");
		copies.put(original, copy);
	}

	/**
	 * Resolves an object referred to but not owned: its copy if it has been
	 * copied in this operation, or the object itself otherwise.
	 *
	 * @param <V>      The object type.
	 * @param original The object, may be null.
	 * @return The copy, or the object itself if not copied.
	 */
	@SuppressWarnings("unchecked")
	public <V> V resolve(V original) {
		if (original == null) {
			return null;
		}
		Object copy = copies.get(original);
		return copy != null ? (V) copy : original;
	}

	/**
	 * Copies a map, sharing keys and copying values.
	 *
	 * @param map The map.
	 * @return The copy.
	 * @throws IllegalArgumentException If the map class can not be copied.
	 */
	Map<Object, Object> copyMap(Map<?, ?> map) {
		@SuppressWarnings("unchecked")
		Map<Object, Object> copy = (Map<Object, Object>) copies.get(map);
		if (copy != null) {
			return copy;
		}
		copy = newMap(map);
		// Register before filling, so circular references resolve to this copy.
		copies.put(map, copy);
		for (Map.Entry<?, ?> entry : map.entrySet()) {
			copy.put(entry.getKey(), copy(entry.getValue()));
		}
		return copy;
	}

	/**
	 * Copies a collection, copying its elements.
	 *
	 * @param collection The collection.
	 * @return The copy.
	 */
	private Collection<Object> copyCollection(Collection<?> collection) {
		Collection<Object> copy = newCollection(collection);
		// Register before filling, so circular references resolve to this copy.
		copies.put(collection, copy);
		for (Object element : collection) {
			copy.add(copy(element));
		}
		return copy;
	}

	/**
	 * Creates a new empty map of the same class as the given one.
	 *
	 * @param map The map.
	 * @return The new empty map.
	 * @throws IllegalArgumentException If the map class is not supported.
	 */
	@SuppressWarnings("unchecked")
	private static Map<Object, Object> newMap(Map<?, ?> map) {
		Class<?> cls = map.getClass();
		if (cls == HashMap.class) {
			return HashMap.newHashMap(map.size());
		}
		if (cls == LinkedHashMap.class) {
			return LinkedHashMap.newLinkedHashMap(map.size());
		}
		if (cls == TreeMap.class) {
			return new TreeMap<>(((TreeMap<Object, Object>) map).comparator());
		}
		throw new IllegalArgumentException("Can not deep copy map class: " + cls.getName());
	}

	/**
	 * Creates a new empty collection of the same class as the given one.
	 *
	 * @param collection The collection.
	 * @return The new empty collection.
	 * @throws IllegalArgumentException If the collection class is not supported.
	 */
	@SuppressWarnings("unchecked")
	private static Collection<Object> newCollection(Collection<?> collection) {
		Class<?> cls = collection.getClass();
		if (cls == ArrayList.class) {
			return new ArrayList<>(collection.size());
		}
		if (cls == LinkedList.class) {
			return new LinkedList<>();
		}
		if (cls == HashSet.class) {
			return HashSet.newHashSet(collection.size());
		}
		if (cls == LinkedHashSet.class) {
			return LinkedHashSet.newLinkedHashSet(collection.size());
		}
		if (cls == TreeSet.class) {
			return new TreeSet<>(((TreeSet<Object>) collection).comparator());
		}
		throw new IllegalArgumentException("Can not deep copy collection class: " + cls.getName());
	}
}
