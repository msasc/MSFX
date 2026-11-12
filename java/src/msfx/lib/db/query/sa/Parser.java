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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import msfx.lib.md.Value;

/**
 * Builds the tree of {@link Node}s of a hand-written expression, checking only
 * its syntax. Columns, tables and the use of aggregates are checked later by the
 * validator.
 * <p>
 * Grammar, from the lowest to the highest precedence:
 *
 * <pre>
 * condition  := and { OR and }
 * and        := not { AND not }
 * not        := NOT not | predicate
 * predicate  := expression [ comparison expression
 *                          | IS [NOT] NULL
 *                          | [NOT] IN ( value {, value} )
 *                          | [NOT] BETWEEN expression AND expression
 *                          | [NOT] LIKE expression ]
 * expression := term { (+ | - | ||) term }
 * term       := unary { (* | /) unary }
 * unary      := - unary | + unary | primary
 * primary    := number | string | ? | [alias .] name
 *             | function ( [DISTINCT] [value {, value}] ) | COUNT(*)
 *             | CASE WHEN condition THEN value {...} ELSE value END
 *             | ( condition )
 * </pre>
 *
 * Where the grammar says value, a full condition is read and then required not
 * to be one, so that the error says "Value expected" at its start rather than
 * pointing at an unexpected token in the middle.
 *
 * @author Miquel Sas
 */
public class Parser {

	/**
	 * The expression.
	 */
	private final String expression;
	/**
	 * The tokens of the expression.
	 */
	private final List<Token> tokens;
	/**
	 * The index of the current token.
	 */
	private int current;
	/**
	 * The number of placeholders read so far, that gives the index of the next.
	 */
	private int parameters;

	/**
	 * Constructor.
	 *
	 * @param expression The expression.
	 * @throws NullPointerException If the expression is null.
	 * @throws SyntaxException      If the lexer rejects the expression.
	 */
	public Parser(String expression) {
		this.expression = Objects.requireNonNull(expression, "Expression can not be null");
		this.tokens = new Lexer(expression).tokenize();
	}

	/**
	 * Parses the expression as a condition, for ON, WHERE and HAVING.
	 *
	 * @return The root node, a condition.
	 * @throws SyntaxException If the syntax is wrong or the result is not a
	 *                         condition.
	 */
	public Node parseCondition() {
		Node node = parseAll();
		checkCondition(node);
		return node;
	}

	/**
	 * Parses the expression as a value, for the select list.
	 *
	 * @return The root node, a value.
	 * @throws SyntaxException If the syntax is wrong or the result is a
	 *                         condition.
	 */
	public Node parseExpression() {
		Node node = parseAll();
		checkValue(node);
		return node;
	}

	/**
	 * Parses the whole expression, from the first token, and checks that no
	 * token is left.
	 *
	 * @return The root node.
	 */
	private Node parseAll() {
		current = 0;
		parameters = 0;
		if (tokens.isEmpty()) {
			throw error("Expression expected", 0);
		}
		Node node = parseOr();
		if (current < tokens.size()) {
			throw unexpected();
		}
		return node;
	}

	/**
	 * Parses OR: <code>and { OR and }</code>.
	 *
	 * @return The node.
	 */
	private Node parseOr() {
		Node first = parseAnd();
		if (!isKeyword(Keyword.OR)) {
			return first;
		}
		checkCondition(first);
		List<Node> conditions = new ArrayList<>();
		conditions.add(first);
		while (isKeyword(Keyword.OR)) {
			current++;
			Node next = parseAnd();
			checkCondition(next);
			conditions.add(next);
		}
		return Node.or(first.getPosition(), conditions);
	}

	/**
	 * Parses AND: <code>not { AND not }</code>.
	 *
	 * @return The node.
	 */
	private Node parseAnd() {
		Node first = parseNot();
		if (!isKeyword(Keyword.AND)) {
			return first;
		}
		checkCondition(first);
		List<Node> conditions = new ArrayList<>();
		conditions.add(first);
		while (isKeyword(Keyword.AND)) {
			current++;
			Node next = parseNot();
			checkCondition(next);
			conditions.add(next);
		}
		return Node.and(first.getPosition(), conditions);
	}

	/**
	 * Parses NOT: <code>NOT not | predicate</code>.
	 *
	 * @return The node.
	 */
	private Node parseNot() {
		if (!isKeyword(Keyword.NOT)) {
			return parsePredicate();
		}
		int position = tokens.get(current).getPosition();
		current++;
		Node condition = parseNot();
		checkCondition(condition);
		return Node.not(position, condition);
	}

	/**
	 * Parses an expression optionally followed by a comparison, IS NULL, IN,
	 * BETWEEN or LIKE.
	 *
	 * @return The node.
	 */
	private Node parsePredicate() {
		Node left = parseAdditive();
		int position = left.getPosition();

		/* Comparison. */
		Token token = peek();
		if (token != null && token.getKind() == Token.Kind.OPERATOR && token.getOperator().isComparison()) {
			checkValue(left);
			current++;
			Node right = parseAdditive();
			checkValue(right);
			return Node.binary(position, token.getOperator(), left, right);
		}

		/* IS [NOT] NULL. */
		if (isKeyword(Keyword.IS)) {
			checkValue(left);
			current++;
			boolean negated = acceptKeyword(Keyword.NOT);
			expectKeyword(Keyword.NULL);
			return Node.isNull(position, negated, left);
		}

		/* [NOT] IN, BETWEEN, LIKE. */
		boolean negated = false;
		if (isKeyword(Keyword.NOT)) {
			current++;
			if (!isKeyword(Keyword.IN) && !isKeyword(Keyword.BETWEEN) && !isKeyword(Keyword.LIKE)) {
				throw error("IN, BETWEEN or LIKE expected", currentPosition());
			}
			negated = true;
		}
		if (isKeyword(Keyword.IN)) {
			checkValue(left);
			current++;
			expectPunctuation("(");
			if (isPunctuation(")")) {
				throw error("Value expected", currentPosition());
			}
			List<Node> values = new ArrayList<>();
			values.add(parseValue());
			while (acceptPunctuation(",")) {
				values.add(parseValue());
			}
			expectPunctuation(")");
			return Node.in(position, negated, left, values);
		}
		if (isKeyword(Keyword.BETWEEN)) {
			checkValue(left);
			current++;
			Node low = parseAdditive();
			checkValue(low);
			expectKeyword(Keyword.AND);
			Node high = parseAdditive();
			checkValue(high);
			return Node.between(position, negated, left, low, high);
		}
		if (isKeyword(Keyword.LIKE)) {
			checkValue(left);
			current++;
			Node pattern = parseAdditive();
			checkValue(pattern);
			return Node.like(position, negated, left, pattern);
		}
		return left;
	}

	/**
	 * Parses addition, subtraction and concatenation:
	 * <code>term { (+ | - | ||) term }</code>.
	 *
	 * @return The node.
	 */
	private Node parseAdditive() {
		Node left = parseTerm();
		while (isOperator(Operator.PLUS) || isOperator(Operator.MINUS) || isOperator(Operator.CONCAT)) {
			Operator operator = tokens.get(current).getOperator();
			checkValue(left);
			current++;
			Node right = parseTerm();
			checkValue(right);
			left = Node.binary(left.getPosition(), operator, left, right);
		}
		return left;
	}

	/**
	 * Parses multiplication and division: <code>unary { (* | /) unary }</code>.
	 *
	 * @return The node.
	 */
	private Node parseTerm() {
		Node left = parseUnary();
		while (isOperator(Operator.MULTIPLY) || isOperator(Operator.DIVIDE)) {
			Operator operator = tokens.get(current).getOperator();
			checkValue(left);
			current++;
			Node right = parseUnary();
			checkValue(right);
			left = Node.binary(left.getPosition(), operator, left, right);
		}
		return left;
	}

	/**
	 * Parses a sign: <code>- unary | + unary | primary</code>.
	 *
	 * @return The node.
	 */
	private Node parseUnary() {
		if (!isOperator(Operator.PLUS) && !isOperator(Operator.MINUS)) {
			return parsePrimary();
		}
		Token token = tokens.get(current);
		current++;
		Node operand = parseUnary();
		checkValue(operand);
		return Node.unary(token.getPosition(), token.getOperator(), operand);
	}

	/**
	 * Parses a literal, a placeholder, a column, a function call, a CASE or a
	 * group between parentheses.
	 *
	 * @return The node.
	 */
	private Node parsePrimary() {
		Token token = peek();
		if (token == null) {
			throw error("Value expected", expression.length());
		}
		int position = token.getPosition();
		Node node = switch (token.getKind()) {
			case NAME:
				yield parseName();
			case KEYWORD:
				if (token.getKeyword() == Keyword.NULL) {
					throw error("NULL is only accepted in IS [NOT] NULL", position);
				}
				if (token.getKeyword() != Keyword.CASE) {
					throw unexpected();
				}
				yield parseCase();
			case OPERATOR:
				throw unexpected();
			case NUMBER:
				current++;
				yield Node.literal(position, new Value(new BigDecimal(token.getText())));
			case STRING:
				current++;
				yield Node.literal(position, new Value(token.getText()));
			case PARAMETER:
				current++;
				yield Node.parameter(position, parameters++);
			case PUNCTUATION: {
				if (!isPunctuation("(")) {
					throw unexpected();
				}
				current++;
				Node content = parseOr();
				expectPunctuation(")");
				yield Node.group(position, content);
			}
		};
		return node;
	}

	/**
	 * Parses a name: a function call if followed by an opening parenthesis, a
	 * column with its alias if followed by a dot, or a column without alias.
	 *
	 * @return The node.
	 */
	private Node parseName() {
		Token name = tokens.get(current);
		current++;
		if (isPunctuation("(")) {
			return parseCall(name);
		}
		if (!acceptPunctuation(".")) {
			return Node.column(name.getPosition(), null, name.getText());
		}
		Token column = peek();
		if (column == null || column.getKind() != Token.Kind.NAME) {
			throw error("Column name expected", currentPosition());
		}
		current++;
		return Node.column(name.getPosition(), name.getText(), column.getText());
	}

	/**
	 * Parses a function call, the current token being the opening parenthesis.
	 *
	 * @param name The token of the function name.
	 * @return The node.
	 */
	private Node parseCall(Token name) {
		Function function = Function.of(name.getText());
		if (function == null) {
			StringBuilder msg = new StringBuilder();
			msg.append("Unknown function ");
			msg.append(name.getText());
			throw error(msg.toString(), name.getPosition());
		}
		current++;

		/* DISTINCT. */
		boolean distinct = false;
		if (isKeyword(Keyword.DISTINCT)) {
			if (!function.isAggregate()) {
				throw error("DISTINCT is only accepted in aggregates", currentPosition());
			}
			current++;
			distinct = true;
		}

		/* COUNT(*). */
		List<Node> arguments = new ArrayList<>();
		if (isOperator(Operator.MULTIPLY)) {
			if (function != Function.COUNT || distinct) {
				throw error("* is only accepted in COUNT(*)", currentPosition());
			}
			arguments.add(Node.star(currentPosition()));
			current++;
			expectPunctuation(")");
			return Node.call(name.getPosition(), function, false, arguments);
		}

		/* Arguments. */
		if (!isPunctuation(")") || distinct) {
			arguments.add(parseValue());
			while (acceptPunctuation(",")) {
				arguments.add(parseValue());
			}
		}
		if (!isPunctuation(")")) {
			throw error("',' or ')' expected", currentPosition());
		}
		if (arguments.size() > function.getMaxArguments()) {
			Node extra = arguments.get(function.getMaxArguments());
			throw error(getArgumentsMessage("Too many", function), extra.getPosition());
		}
		if (arguments.size() < function.getMinArguments()) {
			throw error(getArgumentsMessage("Too few", function), currentPosition());
		}
		current++;
		return Node.call(name.getPosition(), function, distinct, arguments);
	}

	/**
	 * Returns the message of a wrong number of arguments.
	 *
	 * @param problem  Too many or too few.
	 * @param function The function.
	 * @return The message.
	 */
	private static String getArgumentsMessage(String problem, Function function) {
		int min = function.getMinArguments();
		int max = function.getMaxArguments();
		StringBuilder msg = new StringBuilder();
		msg.append(problem);
		msg.append(" arguments for ");
		msg.append(function.name());
		msg.append(", expected ");
		if (min == max) {
			msg.append(min);
		} else if (max == Integer.MAX_VALUE) {
			msg.append(min);
			msg.append(" or more");
		} else {
			msg.append(min);
			msg.append(" to ");
			msg.append(max);
		}
		return msg.toString();
	}

	/**
	 * Parses a searched CASE, the current token being CASE.
	 *
	 * @return The node.
	 */
	private Node parseCase() {
		int position = currentPosition();
		current++;
		if (!isKeyword(Keyword.WHEN)) {
			throw error("WHEN expected, only the searched CASE is accepted", currentPosition());
		}
		List<Node> conditions = new ArrayList<>();
		List<Node> results = new ArrayList<>();
		while (acceptKeyword(Keyword.WHEN)) {
			Node condition = parseOr();
			checkCondition(condition);
			conditions.add(condition);
			expectKeyword(Keyword.THEN);
			results.add(parseValue());
		}
		if (!isKeyword(Keyword.ELSE)) {
			throw error("WHEN or ELSE expected, ELSE is required", currentPosition());
		}
		current++;
		Node otherwise = parseValue();
		expectKeyword(Keyword.END);
		return Node.caseOf(position, conditions, results, otherwise);
	}

	/**
	 * Parses a value: reads a full condition and checks that it is not one.
	 *
	 * @return The node.
	 */
	private Node parseValue() {
		Node node = parseOr();
		checkValue(node);
		return node;
	}

	/**
	 * Checks that the node is a condition.
	 *
	 * @param node The node.
	 */
	private void checkCondition(Node node) {
		if (!node.isCondition()) {
			throw error("Condition expected", node.getPosition());
		}
	}

	/**
	 * Checks that the node is a value, not a condition.
	 *
	 * @param node The node.
	 */
	private void checkValue(Node node) {
		if (node.isCondition()) {
			throw error("Value expected", node.getPosition());
		}
	}

	/**
	 * Returns the current token.
	 *
	 * @return The token, or null at the end.
	 */
	private Token peek() {
		return current < tokens.size() ? tokens.get(current) : null;
	}

	/**
	 * Returns the position of the current token, or the length of the
	 * expression at the end.
	 *
	 * @return The position.
	 */
	private int currentPosition() {
		Token token = peek();
		return token != null ? token.getPosition() : expression.length();
	}

	/**
	 * Returns whether the current token is the keyword.
	 *
	 * @param keyword The keyword.
	 * @return A boolean.
	 */
	private boolean isKeyword(Keyword keyword) {
		Token token = peek();
		return token != null && token.getKeyword() == keyword;
	}

	/**
	 * Returns whether the current token is the operator.
	 *
	 * @param operator The operator.
	 * @return A boolean.
	 */
	private boolean isOperator(Operator operator) {
		Token token = peek();
		return token != null && token.getOperator() == operator;
	}

	/**
	 * Returns whether the current token is the punctuation.
	 *
	 * @param punctuation The punctuation character.
	 * @return A boolean.
	 */
	private boolean isPunctuation(String punctuation) {
		Token token = peek();
		return token != null && token.getKind() == Token.Kind.PUNCTUATION && token.getText().equals(punctuation);
	}

	/**
	 * Skips the current token if it is the keyword.
	 *
	 * @param keyword The keyword.
	 * @return Whether it was skipped.
	 */
	private boolean acceptKeyword(Keyword keyword) {
		if (isKeyword(keyword)) {
			current++;
			return true;
		}
		return false;
	}

	/**
	 * Skips the current token if it is the punctuation.
	 *
	 * @param punctuation The punctuation character.
	 * @return Whether it was skipped.
	 */
	private boolean acceptPunctuation(String punctuation) {
		if (isPunctuation(punctuation)) {
			current++;
			return true;
		}
		return false;
	}

	/**
	 * Skips the current token, that must be the keyword.
	 *
	 * @param keyword The keyword.
	 */
	private void expectKeyword(Keyword keyword) {
		if (!acceptKeyword(keyword)) {
			StringBuilder msg = new StringBuilder();
			msg.append(keyword.name());
			msg.append(" expected");
			throw error(msg.toString(), currentPosition());
		}
	}

	/**
	 * Skips the current token, that must be the punctuation.
	 *
	 * @param punctuation The punctuation character.
	 */
	private void expectPunctuation(String punctuation) {
		if (!acceptPunctuation(punctuation)) {
			StringBuilder msg = new StringBuilder();
			msg.append("'");
			msg.append(punctuation);
			msg.append("' expected");
			throw error(msg.toString(), currentPosition());
		}
	}

	/**
	 * Returns the error of an unexpected current token, or of an unexpected end.
	 *
	 * @return The exception.
	 */
	private SyntaxException unexpected() {
		Token token = peek();
		if (token == null) {
			return error("Unexpected end of expression", expression.length());
		}
		StringBuilder msg = new StringBuilder();
		msg.append("Unexpected ");
		if (token.getKind() == Token.Kind.STRING) {
			msg.append("string");
		} else {
			msg.append("'");
			msg.append(token.getText());
			msg.append("'");
		}
		return error(msg.toString(), token.getPosition());
	}

	/**
	 * Returns a syntax error.
	 *
	 * @param description The description.
	 * @param position    The position.
	 * @return The exception.
	 */
	private SyntaxException error(String description, int position) {
		return new SyntaxException(description, expression, position);
	}
}
