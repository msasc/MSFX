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
package msfx.lib.md;

import java.util.Objects;

/**
 * Data definition of a column or a domain: its type, length and decimals.
 * <p>
 * Immutable, so it is shared: a column created from a domain holds the same
 * data type, and copies of columns and domains share it.
 *
 * @param type     The type.
 * @param length   The length, -1 if not applicable.
 * @param decimals The number of decimal places, -1 if not applicable.
 *
 * @author Miquel Sas
 */
public record DataType(Types type, int length, int decimals) {

    /**
     * Constructor.
     *
     * @param type     The type.
     * @param length   The length, -1 if not applicable.
     * @param decimals The number of decimal places, -1 if not applicable.
     * @throws NullPointerException If the type is null.
     */
    public DataType {
        Objects.requireNonNull(type, "Type can not be null");
    }
}
