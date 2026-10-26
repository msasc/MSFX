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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
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
 * A collection of utility methods for working with maps.
 *
 * @author Miquel Sas
 */
public class Maps {
	/**
	 * The set of classes accepted as values. Matching is by exact class, so
	 * subclasses must be listed explicitly to be accepted.
	 */
	public static final Set<Class<?>> VALUE_CLASSES = Set.of(
			// Scalars.
			Boolean.class,
			Byte.class,
			Short.class,
			Integer.class,
			Long.class,
			Float.class,
			Double.class,
			BigInteger.class,
			BigDecimal.class,
			Character.class,
			String.class,
			// Date and time.
			LocalDate.class,
			LocalTime.class,
			LocalDateTime.class,
			// Maps.
			HashMap.class,
			LinkedHashMap.class,
			TreeMap.class,
			// Lists.
			ArrayList.class,
			LinkedList.class,
			// Sets.
			HashSet.class,
			LinkedHashSet.class,
			TreeSet.class);

	/**
	 * Checks that the value belongs to the list of accepted classes.
	 *
	 * @param value The value to validate.
	 * @throws IllegalArgumentException If the value is null or its class is not
	 *                                  accepted.
	 */
	public static void validateValue(Object value, Set<Class<?>> acceptedClasses) {
		if (value == null) {
			throw new IllegalArgumentException("Value can not be null");
		}
		if (!acceptedClasses.contains(value.getClass())) {
			throw new IllegalArgumentException("Invalid value class: " + value.getClass().getName());
		}
	}

	/**
	 * Returns a deep copy of the map, accepting only {@link #VALUE_CLASSES} as
	 * values.
	 *
	 * @param <K> The key type.
	 * @param <V> The value type.
	 * @param map The map to copy.
	 * @return The deep copy.
	 * @throws IllegalArgumentException If any value, at any depth, is null or its
	 *                                  class is not accepted.
	 * @see #deepCopy(Map, Set)
	 */
	public static <K, V> Map<K, V> deepCopy(Map<K, V> map) {
		return deepCopy(map, VALUE_CLASSES);
	}

	/**
	 * Returns a deep copy of the map, accepting only the given classes as values.
	 * <p>
	 * How each value is copied:
	 * <ul>
	 * <li>Maps ({@link HashMap}, {@link LinkedHashMap}, {@link TreeMap}) and
	 * collections ({@link ArrayList}, {@link LinkedList}, {@link HashSet},
	 * {@link LinkedHashSet}, {@link TreeSet}) are copied into a new instance of the
	 * same class, recursively. Tree maps and sets keep their comparator.</li>
	 * <li>Any other accepted value is assumed immutable (scalars, strings, date and
	 * time) and is shared, not copied.</li>
	 * </ul>
	 * Map keys are shared, not copied, since keys should be immutable.
	 * <p>
	 * A container referenced more than once is copied once, and the copy is
	 * referenced in the same places, so shared and circular references are
	 * preserved.
	 * <p>
	 * The top level map does not need to be in the accepted classes, but it must
	 * be one of the map classes listed above.
	 *
	 * @param <K>             The key type.
	 * @param <V>             The value type.
	 * @param map             The map to copy.
	 * @param acceptedClasses The classes accepted as values, matched by exact
	 *                        class.
	 * @return The deep copy.
	 * @throws IllegalArgumentException If any value, at any depth, is null or its
	 *                                  class is not accepted, or if an accepted
	 *                                  map or collection class can not be copied.
	 */
	@SuppressWarnings("unchecked")
	public static <K, V> Map<K, V> deepCopy(Map<K, V> map, Set<Class<?>> acceptedClasses) {
		Objects.requireNonNull(map, "Map can not be null");
		Objects.requireNonNull(acceptedClasses, "Accepted classes can not be null");
		return (Map<K, V>) copyMap(map, acceptedClasses, new IdentityHashMap<>());
	}

	/**
	 * Validates and copies a value.
	 *
	 * @param value           The value.
	 * @param acceptedClasses The accepted classes.
	 * @param copies          The containers already copied, original to copy.
	 * @return The copy, or the value itself if it is immutable.
	 */
	private static Object copyValue(Object value, Set<Class<?>> acceptedClasses, Map<Object, Object> copies) {
		validateValue(value, acceptedClasses);
		if (value instanceof Map<?, ?> map) {
			return copyMap(map, acceptedClasses, copies);
		}
		if (value instanceof Collection<?> collection) {
			return copyCollection(collection, acceptedClasses, copies);
		}
		return value;
	}

	/**
	 * Copies a map, sharing keys and copying values.
	 *
	 * @param map             The map.
	 * @param acceptedClasses The accepted classes.
	 * @param copies          The containers already copied, original to copy.
	 * @return The copy.
	 */
	private static Map<Object, Object> copyMap(Map<?, ?> map, Set<Class<?>> acceptedClasses, Map<Object, Object> copies) {
		@SuppressWarnings("unchecked")
		Map<Object, Object> copy = (Map<Object, Object>) copies.get(map);
		if (copy != null) {
			return copy;
		}
		copy = newMap(map);
		// Register before filling, so circular references resolve to this copy.
		copies.put(map, copy);
		for (Map.Entry<?, ?> entry : map.entrySet()) {
			copy.put(entry.getKey(), copyValue(entry.getValue(), acceptedClasses, copies));
		}
		return copy;
	}

	/**
	 * Copies a collection, copying its elements.
	 *
	 * @param collection      The collection.
	 * @param acceptedClasses The accepted classes.
	 * @param copies          The containers already copied, original to copy.
	 * @return The copy.
	 */
	private static Collection<Object> copyCollection(Collection<?> collection, Set<Class<?>> acceptedClasses, Map<Object, Object> copies) {
		@SuppressWarnings("unchecked")
		Collection<Object> copy = (Collection<Object>) copies.get(collection);
		if (copy != null) {
			return copy;
		}
		copy = newCollection(collection);
		// Register before filling, so circular references resolve to this copy.
		copies.put(collection, copy);
		for (Object element : collection) {
			copy.add(copyValue(element, acceptedClasses, copies));
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
