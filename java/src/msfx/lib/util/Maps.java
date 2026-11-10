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
import java.util.HashMap;
import java.util.HashSet;
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
			TreeSet.class,
			// Properties.
			Properties.class);

	/**
	 * Checks that the value belongs to the list of accepted classes, or is
	 * {@link Copyable}.
	 *
	 * @param value           The value to validate.
	 * @param acceptedClasses The accepted classes, matched by exact class.
	 * @throws IllegalArgumentException If the value is null or its class is not
	 *                                  accepted.
	 */
	public static void validateValue(Object value, Set<Class<?>> acceptedClasses) {
		if (value == null) {
			throw new IllegalArgumentException("Value can not be null");
		}
		if (!acceptedClasses.contains(value.getClass()) && !(value instanceof Copyable<?>)) {
			throw new IllegalArgumentException("Invalid value class: " + value.getClass().getName());
		}
	}

	/**
	 * Returns a deep copy of the map, copying the {@link #VALUE_CLASSES} as
	 * described in {@link Copier}.
	 *
	 * @param <K> The key type.
	 * @param <V> The value type.
	 * @param map The map to copy.
	 * @return The deep copy.
	 * @throws IllegalArgumentException If any value, at any depth, is null.
	 * @see #deepCopy(Map, Set)
	 */
	public static <K, V> Map<K, V> deepCopy(Map<K, V> map) {
		return deepCopy(map, VALUE_CLASSES);
	}

	/**
	 * Returns a deep copy of the map, copying the values of the given classes,
	 * and the {@link Copyable} values, as described in {@link Copier}.
	 * <p>
	 * The top level map does not need to be in the accepted classes, but it must
	 * be one of the map classes the copier supports.
	 *
	 * @param <K>             The key type.
	 * @param <V>             The value type.
	 * @param map             The map to copy.
	 * @param acceptedClasses The classes whose values are copied, matched by
	 *                        exact class.
	 * @return The deep copy.
	 * @throws IllegalArgumentException If any value, at any depth, is null, or if
	 *                                  an accepted map or collection class can
	 *                                  not be copied.
	 */
	@SuppressWarnings("unchecked")
	public static <K, V> Map<K, V> deepCopy(Map<K, V> map, Set<Class<?>> acceptedClasses) {
		Objects.requireNonNull(map, "Map can not be null");
		Objects.requireNonNull(acceptedClasses, "Accepted classes can not be null");
		return (Map<K, V>) new Copier(acceptedClasses).copyMap(map);
	}
}

