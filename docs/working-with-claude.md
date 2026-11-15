# Working with Claude: notes for a guide

Running notes on the working method of the MSFX library, collected while it is
built, as material for a guide on coding efficiently with Claude. Each entry
states the practice, why it works, and an episode where it showed.

Started 2026-11-15. Claude adds entries as new observations come up; edit
freely.

## 1. Key concepts in steps, each with its reason

**Practice.** Present a design one key concept at a time, with the reason
behind it, and ask for feedback on that step only.

**Why it works.** A single concept with its motive gives Claude a clear frame:
it works out the direct consequences and stops there. Given the whole problem
at once, it explores every branch and the design grows heavy with machinery
nobody asked for.

**Episode.** Table equality, in four steps: equality must be by value because a
front end and a back end lose object references; the structure of a column is
name, type, length and decimals; an engine is identified by a key built from
adapter, host, port and database; a table equals another by key, schema and
name. Each answer was short, and the result is simple and complete. An earlier
attempt to lay out the whole QSelect design at once had to be stopped.

## 2. Analysis first, code on "go on"

**Practice.** On design questions, ask for analysis and feedback first; say
"implement" or "go on" only when the design is agreed.

**Why it works.** It keeps half-agreed designs out of the code, and the
discussion stays cheap: changing a paragraph costs less than changing a class
and its tests.

**Episode.** The union wraps of QSelect (alias in the call, aliases enforced,
types not validated) were settled in text over several messages, then written
in one pass with their tests.

## 3. Corrections are discussed before editing

**Practice.** When a remark points at something wrong in code just written,
discuss the correction first, as any design question, and edit only on the
go-ahead.

**Why it works.** A quick fix on top of a fix piles up changes before the
design is settled ("corrections over corrections tend to be bad").

**Episode.** QSelect.getColumnList first took aliases, then became a member
list, then lost the aliases; and the rule "all columns with a domain or none"
was first fixed on the spot and interrupted. Discussing it showed a simpler
check, written by the user, based on the sizes of two lists.

## 4. Skeleton code first

**Practice.** Start from the outline of a class or method (signatures, the
core lines) before the full code. When the design is clear in mind, the user
writes the skeleton and asks Claude to comment, validate and test it. When it
is not, the user asks Claude for a skeleton, to get ideas, and shapes it from
there.

**Why it works.** Code carries the intent faster and more precisely than
prose: names, call order and usage are all in it. A skeleton is also cheap to
change, so it is a good place to try ideas before the Javadoc, the
validations, the error messages and the tests are written in the project
style.

**Episode.** QInsert (into, columns, values, select), QSelect.readList and the
Numbers utility class all started as user skeletons, completed by Claude.

## 5. Rules recorded the moment they are stated

**Practice.** When a decision is general ("this can be a general rule"), have
it written into CLAUDE.md right away.

**Why it works.** CLAUDE.md is read at the start of every session, so the rule
holds from then on without being repeated, and the code stays consistent.

**Episode.** Locals for step-by-step debugging, utility functions in their util
class, external names after an underscore, and the exceptions rule all went
into CLAUDE.md as they were stated.

## 6. Numbered questions, numbered answers

**Practice.** When Claude lists several open points, answer them by number,
one line each.

**Why it works.** Several decisions travel in one round trip, and nothing is
left unanswered by accident; Claude then lists only what is still open.

**Episode.** The design of QInsert columns, values and select was closed in a
few exchanges of "1. ... 2. ... 3. ...".

## 7. Explicit scope for database work

**Practice.** State exactly what Claude may do against a database: which
database, which tables, which kind of statement.

**Why it works.** Claude does not run DDL or change data without permission.
A clear scope turns that caution into real tests instead of offline ones.

**Episode.** "You can freely create a copy of TPIECES as TPIECES_TMP" allowed
the QInsert test to fill a table row by row and from a select, and compare the
result, on the local SQL Server.

## 8. Short reasons unlock better designs

**Practice.** Give the reason behind a preference, even in one sentence.

**Why it works.** A reason applies beyond the case at hand, so Claude can
settle related questions with it instead of asking each one.

**Episode.** "Metadata nullable flags may not match the database" settled at
once the null checks, the omitted-column checks and the type checks of
QInsert: all left to the database.
