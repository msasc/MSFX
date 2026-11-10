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

/**
 * An object that can make a deep copy of itself, also when stored in
 * {@link Properties} or in maps and collections copied by {@link Maps}.
 * <p>
 * Implementations of {@link #copy(Copier)} must follow this rule, so that
 * circular and shared references are preserved:
 * <ol>
 * <li>Create the empty copy.</li>
 * <li>Register it with {@link Copier#register(Object, Object)}, before copying
 * anything.</li>
 * <li>Fill it, copying the values it owns with {@link Copier#copy(Object)},
 * and resolving the objects it only refers to with
 * {@link Copier#resolve(Object)}.</li>
 * </ol>
 *
 * @param <T> The type of the copy.
 * @author Miquel Sas
 */
public interface Copyable<T> {

	/**
	 * @param copier The copier of the copy operation, to copy or resolve the
	 *               values this object refers to.
	 * @return A deep copy of this object.
	 */
	T copy(Copier copier);

	/**
	 * @return A deep copy of this object, made with a new {@link Copier}.
	 */
	@SuppressWarnings("unchecked")
	default T copy() {
		return (T) new Copier().copy(this);
	}
}
