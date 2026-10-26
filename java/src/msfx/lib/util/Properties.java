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
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * A useful and quite generic properties table with typed access for commonly
 * used objects.
 * <p>
 * A filter for accepted keys and values classes can be set to configure the
 * type of data that can
 * be put into these properties.
 *
 * @author Miquel Sas
 */
public class Properties {
	/**
	 * The property map.
	 */
	private final Map<Object, Object> props = new HashMap<>();

	/**
	 * Constructor.
	 */
	public Properties() {
	}

	/**
	 * Clears the properties.
	 */
	public void clear() {
		props.clear();
	}

	/**
	 * @return A deep copy of this properties object.
	 */
	public Properties copy() {
		Properties copy = new Properties();
		copy.props.putAll(Maps.deepCopy(props, Maps.VALUE_CLASSES));
		return copy;
	}

	/**
	 * @param key   The key.
	 * @param value The value to associate with the key.
	 */
	public void put(Object key, Object value) {
		Maps.validateValue(value, Maps.VALUE_CLASSES);
		props.put(key, value);
	}

	/**
	 * @param key The key.
	 * @return The removed value associated with the key, or null if not found.
	 */
	public Object remove(Object key) {
		return props.remove(key);
	}

	/**
	 * @param key The key.
	 * @return The Boolean value associated with the key, or the default value if not found.
	 * @throws IllegalArgumentException If the value is not a Boolean.
	 */
	public Boolean getBoolean(Object key) {
		return getBoolean(key, false);
	}
	/**
	 * @param key The key.
	 * @param defaultValue The default value to return if the key is not found. 
	 * @return The Boolean value associated with the key, or the default value if not found.
	 * @throws IllegalArgumentException If the value is not a Boolean.
	 */
	public Boolean getBoolean(Object key, Boolean defaultValue) {
		Object value = props.get(key);
		if (value == null || value instanceof Boolean) {
			if (value == null) {
				return defaultValue;
			}
			return (Boolean) value;
		}
		throw new IllegalArgumentException("Value is not a Boolean: " + value);
	}

	/**
	 * @param key The key.
	 * @return The Byte value associated with the key, or zero if not found.
	 * @throws IllegalArgumentException If the value is not a Byte.
	 */
	public Byte getByte(Object key) {
		return getByte(key, (byte) 0);
	}

	/**
	 * @param key The key.
	 * @param defaultValue The default value to return if the key is not found.
	 * @return The Byte value associated with the key, or the default value if not found.
	 * @throws IllegalArgumentException If the value is not a Byte.
	 */
	public Byte getByte(Object key, Byte defaultValue) {
		Object value = props.get(key);
		if (value == null || value instanceof Byte) {
			if (value == null) {
				return defaultValue;
			}
			return (Byte) value;
		}
		throw new IllegalArgumentException("Value is not a Byte: " + value);
	}

	/**
	 * @param key The key.
	 * @return The Short value associated with the key, or zero if not found.
	 * @throws IllegalArgumentException If the value is not a Short.
	 */
	public Short getShort(Object key) {
		return getShort(key, (short) 0);
	}

	/**
	 * @param key The key.
	 * @param defaultValue The default value to return if the key is not found.
	 * @return The Short value associated with the key, or the default value if not found.
	 * @throws IllegalArgumentException If the value is not a Short.
	 */
	public Short getShort(Object key, Short defaultValue) {
		Object value = props.get(key);
		if (value == null || value instanceof Short) {
			if (value == null) {
				return defaultValue;
			}
			return (Short) value;
		}
		throw new IllegalArgumentException("Value is not a Short: " + value);
	}

	/**
	 * @param key The key.
	 * @return The Integer value associated with the key, or zero if not found.
	 * @throws IllegalArgumentException If the value is not an Integer.
	 */
	public Integer getInteger(Object key) {
		return getInteger(key, 0);
	}

	/**
	 * @param key The key.
	 * @param defaultValue The default value to return if the key is not found.
	 * @return The Integer value associated with the key, or the default value if not found.
	 * @throws IllegalArgumentException If the value is not an Integer.
	 */
	public Integer getInteger(Object key, Integer defaultValue) {
		Object value = props.get(key);
		if (value == null || value instanceof Integer) {
			if (value == null) {
				return defaultValue;
			}
			return (Integer) value;
		}
		throw new IllegalArgumentException("Value is not an Integer: " + value);
	}

	/**
	 * @param key The key.
	 * @return The Long value associated with the key, or zero if not found.
	 * @throws IllegalArgumentException If the value is not a Long.
	 */
	public Long getLong(Object key) {
		return getLong(key, 0L);
	}

	/**
	 * @param key The key.
	 * @param defaultValue The default value to return if the key is not found.
	 * @return The Long value associated with the key, or the default value if not found.
	 * @throws IllegalArgumentException If the value is not a Long.
	 */
	public Long getLong(Object key, Long defaultValue) {
		Object value = props.get(key);
		if (value == null || value instanceof Long) {
			if (value == null) {
				return defaultValue;
			}
			return (Long) value;
		}
		throw new IllegalArgumentException("Value is not a Long: " + value);
	}

	/**
	 * @param key The key.
	 * @return The Float value associated with the key, or zero if not found.
	 * @throws IllegalArgumentException If the value is not a Float.
	 */
	public Float getFloat(Object key) {
		return getFloat(key, 0f);
	}

	/**
	 * @param key The key.
	 * @param defaultValue The default value to return if the key is not found.
	 * @return The Float value associated with the key, or the default value if not found.
	 * @throws IllegalArgumentException If the value is not a Float.
	 */
	public Float getFloat(Object key, Float defaultValue) {
		Object value = props.get(key);
		if (value == null || value instanceof Float) {
			if (value == null) {
				return defaultValue;
			}
			return (Float) value;
		}
		throw new IllegalArgumentException("Value is not a Float: " + value);
	}

	/**
	 * @param key The key.
	 * @return The Double value associated with the key, or zero if not found.
	 * @throws IllegalArgumentException If the value is not a Double.
	 */
	public Double getDouble(Object key) {
		return getDouble(key, 0d);
	}

	/**
	 * @param key The key.
	 * @param defaultValue The default value to return if the key is not found.
	 * @return The Double value associated with the key, or the default value if not found.
	 * @throws IllegalArgumentException If the value is not a Double.
	 */
	public Double getDouble(Object key, Double defaultValue) {
		Object value = props.get(key);
		if (value == null || value instanceof Double) {
			if (value == null) {
				return defaultValue;
			}
			return (Double) value;
		}
		throw new IllegalArgumentException("Value is not a Double: " + value);
	}

	/**
	 * @param key The key.
	 * @return The BigInteger value associated with the key, or zero if not found.
	 * @throws IllegalArgumentException If the value is not a BigInteger.
	 */
	public BigInteger getBigInteger(Object key) {
		return getBigInteger(key, BigInteger.ZERO);
	}

	/**
	 * @param key The key.
	 * @param defaultValue The default value to return if the key is not found.
	 * @return The BigInteger value associated with the key, or the default value if not found.
	 * @throws IllegalArgumentException If the value is not a BigInteger.
	 */
	public BigInteger getBigInteger(Object key, BigInteger defaultValue) {
		Object value = props.get(key);
		if (value == null || value instanceof BigInteger) {
			if (value == null) {
				return defaultValue;
			}
			return (BigInteger) value;
		}
		throw new IllegalArgumentException("Value is not a BigInteger: " + value);
	}

	/**
	 * @param key The key.
	 * @return The BigDecimal value associated with the key, or zero if not found.
	 * @throws IllegalArgumentException If the value is not a BigDecimal.
	 */
	public BigDecimal getBigDecimal(Object key) {
		return getBigDecimal(key, BigDecimal.ZERO);
	}

	/**
	 * @param key The key.
	 * @param defaultValue The default value to return if the key is not found.
	 * @return The BigDecimal value associated with the key, or the default value if not found.
	 * @throws IllegalArgumentException If the value is not a BigDecimal.
	 */
	public BigDecimal getBigDecimal(Object key, BigDecimal defaultValue) {
		Object value = props.get(key);
		if (value == null || value instanceof BigDecimal) {
			if (value == null) {
				return defaultValue;
			}
			return (BigDecimal) value;
		}
		throw new IllegalArgumentException("Value is not a BigDecimal: " + value);
	}

	/**
	 * @param key The key.
	 * @return The Character value associated with the key, or the null character if not found.
	 * @throws IllegalArgumentException If the value is not a Character.
	 */
	public Character getCharacter(Object key) {
		return getCharacter(key, '\0');
	}

	/**
	 * @param key The key.
	 * @param defaultValue The default value to return if the key is not found.
	 * @return The Character value associated with the key, or the default value if not found.
	 * @throws IllegalArgumentException If the value is not a Character.
	 */
	public Character getCharacter(Object key, Character defaultValue) {
		Object value = props.get(key);
		if (value == null || value instanceof Character) {
			if (value == null) {
				return defaultValue;
			}
			return (Character) value;
		}
		throw new IllegalArgumentException("Value is not a Character: " + value);
	}

	/**
	 * @param key The key.
	 * @return The String value associated with the key, or an empty string if not found.
	 * @throws IllegalArgumentException If the value is not a String.
	 */
	public String getString(Object key) {
		return getString(key, "");
	}

	/**
	 * @param key The key.
	 * @param defaultValue The default value to return if the key is not found.
	 * @return The String value associated with the key, or the default value if not found.
	 * @throws IllegalArgumentException If the value is not a String.
	 */
	public String getString(Object key, String defaultValue) {
		Object value = props.get(key);
		if (value == null || value instanceof String) {
			if (value == null) {
				return defaultValue;
			}
			return (String) value;
		}
		throw new IllegalArgumentException("Value is not a String: " + value);
	}

	/**
	 * @param key The key.
	 * @return The LocalDate value associated with the key, or null if not found.
	 * @throws IllegalArgumentException If the value is not a LocalDate.
	 */
	public LocalDate getLocalDate(Object key) {
		Object value = props.get(key);
		if (value == null || value instanceof LocalDate) {
			return (LocalDate) value;
		}
		throw new IllegalArgumentException("Value is not a LocalDate: " + value);
	}

	/**
	 * @param key The key.
	 * @return The LocalTime value associated with the key, or null if not found.
	 * @throws IllegalArgumentException If the value is not a LocalTime.
	 */
	public LocalTime getLocalTime(Object key) {
		Object value = props.get(key);
		if (value == null || value instanceof LocalTime) {
			return (LocalTime) value;
		}
		throw new IllegalArgumentException("Value is not a LocalTime: " + value);
	}

	/**
	 * @param key The key.
	 * @return The LocalDateTime value associated with the key, or null if not found.
	 * @throws IllegalArgumentException If the value is not a LocalDateTime.
	 */
	public LocalDateTime getLocalDateTime(Object key) {
		Object value = props.get(key);
		if (value == null || value instanceof LocalDateTime) {
			return (LocalDateTime) value;
		}
		throw new IllegalArgumentException("Value is not a LocalDateTime: " + value);
	}

	/**
	 * @param <K> The key type.
	 * @param <V> The value type.
	 * @param key The key.
	 * @return The HashMap value associated with the key, or null if not found.
	 * @throws IllegalArgumentException If the value is not a HashMap.
	 */
	@SuppressWarnings("unchecked")
	public <K, V> HashMap<K, V> getHashMap(Object key) {
		Object value = props.get(key);
		if (value == null || value instanceof HashMap) {
			return (HashMap<K, V>) value;
		}
		throw new IllegalArgumentException("Value is not a HashMap: " + value);
	}

	/**
	 * @param <K> The key type.
	 * @param <V> The value type.
	 * @param key The key.
	 * @return The LinkedHashMap value associated with the key, or null if not found.
	 * @throws IllegalArgumentException If the value is not a LinkedHashMap.
	 */
	@SuppressWarnings("unchecked")
	public <K, V> LinkedHashMap<K, V> getLinkedHashMap(Object key) {
		Object value = props.get(key);
		if (value == null || value instanceof LinkedHashMap) {
			return (LinkedHashMap<K, V>) value;
		}
		throw new IllegalArgumentException("Value is not a LinkedHashMap: " + value);
	}

	/**
	 * @param <K> The key type.
	 * @param <V> The value type.
	 * @param key The key.
	 * @return The TreeMap value associated with the key, or null if not found.
	 * @throws IllegalArgumentException If the value is not a TreeMap.
	 */
	@SuppressWarnings("unchecked")
	public <K, V> TreeMap<K, V> getTreeMap(Object key) {
		Object value = props.get(key);
		if (value == null || value instanceof TreeMap) {
			return (TreeMap<K, V>) value;
		}
		throw new IllegalArgumentException("Value is not a TreeMap: " + value);
	}

	/**
	 * @param <E> The element type.
	 * @param key The key.
	 * @return The ArrayList value associated with the key, or null if not found.
	 * @throws IllegalArgumentException If the value is not an ArrayList.
	 */
	@SuppressWarnings("unchecked")
	public <E> ArrayList<E> getArrayList(Object key) {
		Object value = props.get(key);
		if (value == null || value instanceof ArrayList) {
			return (ArrayList<E>) value;
		}
		throw new IllegalArgumentException("Value is not an ArrayList: " + value);
	}

	/**
	 * @param <E> The element type.
	 * @param key The key.
	 * @return The LinkedList value associated with the key, or null if not found.
	 * @throws IllegalArgumentException If the value is not a LinkedList.
	 */
	@SuppressWarnings("unchecked")
	public <E> LinkedList<E> getLinkedList(Object key) {
		Object value = props.get(key);
		if (value == null || value instanceof LinkedList) {
			return (LinkedList<E>) value;
		}
		throw new IllegalArgumentException("Value is not a LinkedList: " + value);
	}

	/**
	 * @param <E> The element type.
	 * @param key The key.
	 * @return The HashSet value associated with the key, or null if not found.
	 * @throws IllegalArgumentException If the value is not a HashSet.
	 */
	@SuppressWarnings("unchecked")
	public <E> HashSet<E> getHashSet(Object key) {
		Object value = props.get(key);
		if (value == null || value instanceof HashSet) {
			return (HashSet<E>) value;
		}
		throw new IllegalArgumentException("Value is not a HashSet: " + value);
	}

	/**
	 * @param <E> The element type.
	 * @param key The key.
	 * @return The LinkedHashSet value associated with the key, or null if not found.
	 * @throws IllegalArgumentException If the value is not a LinkedHashSet.
	 */
	@SuppressWarnings("unchecked")
	public <E> LinkedHashSet<E> getLinkedHashSet(Object key) {
		Object value = props.get(key);
		if (value == null || value instanceof LinkedHashSet) {
			return (LinkedHashSet<E>) value;
		}
		throw new IllegalArgumentException("Value is not a LinkedHashSet: " + value);
	}

	/**
	 * @param <E> The element type.
	 * @param key The key.
	 * @return The TreeSet value associated with the key, or null if not found.
	 * @throws IllegalArgumentException If the value is not a TreeSet.
	 */
	@SuppressWarnings("unchecked")
	public <E> TreeSet<E> getTreeSet(Object key) {
		Object value = props.get(key);
		if (value == null || value instanceof TreeSet) {
			return (TreeSet<E>) value;
		}
		throw new IllegalArgumentException("Value is not a TreeSet: " + value);
	}
}
