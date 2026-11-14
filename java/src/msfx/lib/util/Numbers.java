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
import java.math.RoundingMode;

/**
 * Number utilities, added as they are needed.
 *
 * @author Miquel Sas
 */
public class Numbers {
	private Numbers() { }

	/**
	 * Divides two numbers, returning zero when the denominator is zero instead
	 * of an infinite or NaN result.
	 *
	 * @param numerator   The numerator.
	 * @param denominator The denominator.
	 * @return The quotient, or zero if the denominator is zero.
	 */
	public static double zdiv(double numerator, double denominator) {
		return denominator != 0 ? numerator / denominator : 0;
	}
	/**
	 * Returns the number of characters of the integer part of a number, as
	 * written in plain notation: the digits before the decimal point, plus the
	 * minus sign for a negative number.
	 *
	 * @param number The number.
	 * @return The number of characters before the decimal point, or the length
	 *         of the number if it has no decimal point.
	 */
	public static int getDigits(double number) {
		String str = new BigDecimal(number).toPlainString();
		int index = str.indexOf('.');
		if (index <= 0) { return str.length(); }
		return index;
	}
	/**
	 * Returns a decimal with a number of decimal places, rounded half up when it
	 * has more, as a decimal read from a database is given the decimals of its
	 * definition. A null decimal, which numbers never are, is zero.
	 *
	 * @param value    The decimal, or null.
	 * @param decimals The number of decimal places, or -1 if not applicable.
	 * @return The decimal with the decimal places, the same decimal if it
	 *         already has them or they are not applicable, or zero with the
	 *         decimal places if it is null.
	 */
	public static BigDecimal toScale(BigDecimal value, int decimals) {
		if (value == null) {
			return decimals < 0 ? BigDecimal.ZERO : BigDecimal.ZERO.setScale(decimals);
		}
		if (decimals < 0 || value.scale() == decimals) {
			return value;
		}
		return value.setScale(decimals, RoundingMode.HALF_UP);
	}
}
