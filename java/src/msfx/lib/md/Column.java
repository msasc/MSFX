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

import msfx.lib.util.Properties;

/**
 * Represents a column in a table.
 *
 * @author Miquel Sas
 */
public class Column {
    /**
     * Container for the column properties.
     */
    private final Properties props = new Properties();
    /**
     * The type of the column, kept apart since enums are not accepted as
     * property values.
     */
    private final Types type;

    /**
     * @param name     The name of the column.
     * @param type     The type of the column.
     * @param length   The length of the column, -1 if not applicable.
     * @param decimals The number of decimal places for the column, -1 if not
     *                 applicable.
     */
    public Column(String name, Types type, int length, int decimals) {
        this.type = Objects.requireNonNull(type, "Type can not be null");
        props.put("name", name);
        props.put("length", length);
        props.put("decimals", decimals);
    }

    /**
     * @return The name of the column.
     */
    public String getName() {
        return props.getString("name");
    }

    /**
     * @return The type of the column.
     */
    public Types getType() {
        return props.getObject("type", Types.class);
    }

    /**
     * @return The length of the column, -1 if not applicable.
     */
    public int getLength() {
        return props.getInteger("length");
    }

    /**
     * @return The number of decimal places for the column, -1 if not applicable.
     */
    public int getDecimals() {
        return props.getInteger("decimals");
    }

    /**
     * @return The user properties of the column.
     */
    public Properties getProperties() {
        Properties userProps = props.getProperties("user-props");
        if (userProps == null) {
            userProps = new Properties();
            props.put("user-props", userProps);
        }
        return userProps;
    }
}
