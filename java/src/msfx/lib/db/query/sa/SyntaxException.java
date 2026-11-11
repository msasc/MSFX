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

package msfx.lib.db.query.sa;

/**
 * An error in a hand-written expression. The message gives the description,
 * the column where the error is, from 1, and the expression with a mark under
 * that column.
 *
 * @author Miquel Sas
 */
public class SyntaxException extends IllegalArgumentException {

	/**
	 * Serial version UID.
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * Returns the full message.
	 *
	 * @param description The description of the error.
	 * @param expression  The expression.
	 * @param position    The position of the error, from zero.
	 * @return The message.
	 */
	private static String getMessage(String description, String expression, int position) {
		StringBuilder msg = new StringBuilder();
		msg.append(description);
		msg.append(" (column ");
		msg.append(position + 1);
		msg.append(")\n");
		msg.append(expression);
		msg.append("\n");
		msg.append(" ".repeat(position));
		msg.append("^");
		return msg.toString();
	}

	/**
	 * The position of the error in the expression, from zero.
	 */
	private final int position;

	/**
	 * Constructor.
	 *
	 * @param description The description of the error.
	 * @param expression  The expression.
	 * @param position    The position of the error, from zero.
	 */
	public SyntaxException(String description, String expression, int position) {
		super(getMessage(description, expression, position));
		this.position = position;
	}

	/**
	 * Returns the position of the error in the expression.
	 *
	 * @return The position, from zero.
	 */
	public int getPosition() {
		return position;
	}
}
