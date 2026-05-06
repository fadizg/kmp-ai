package io.github.fadizg.kmpai.llm

/**
 * Constrains generation to text that matches a GBNF grammar (the format
 * llama.cpp uses, near-identical to BNF). Pass via
 * [SamplingParams.grammar] or [LlmEnvironment.defaultSampling].
 *
 * Use the helpers — [choice], [regex], [json], [jsonOf] — for the common
 * cases, or [raw] when you have a hand-written GBNF grammar.
 *
 * Layering: a grammar set on [LlmEnvironment.defaultSampling] applies to
 * every call; [ChatSession] overrides take precedence; per-call
 * [SamplingParams.grammar] wins over both. Most-specific wins.
 *
 * Note: grammar enforcement happens in the sampler. If [SamplingParams.stop]
 * fires before the grammar reaches an accepting state the output is
 * truncated; size [SamplingParams.maxTokens] generously.
 */
class Grammar internal constructor(
    /** GBNF source. The root rule must be named `root`. */
    val gbnf: String,
) {
    override fun toString(): String = "Grammar(${gbnf.length} chars)"

    companion object {
        /** Use a hand-written GBNF grammar. The root rule must be named `root`. */
        fun raw(gbnf: String): Grammar {
            require(gbnf.contains("root")) {
                "GBNF grammar must define a `root` rule"
            }
            return Grammar(gbnf)
        }

        /**
         * Forces the model to emit one of [options] verbatim. Whitespace and
         * casing are preserved exactly. Useful for yes/no, multiple-choice,
         * or routing decisions where the parser downstream is a simple
         * `equals` check.
         */
        fun choice(vararg options: String): Grammar {
            require(options.isNotEmpty()) { "choice() needs at least one option" }
            val alts = options.joinToString(" | ") { "\"${escapeGbnf(it)}\"" }
            return Grammar("root ::= $alts")
        }

        /**
         * Forces the model to emit text matching a tiny regex-like pattern.
         * Supports literals, character classes `[a-z]`, alternation `|`,
         * grouping `( ... )`, and repetition `*`, `+`, `?`. **Not** full
         * regex — anchors, lookaround, backreferences, etc. are not
         * supported. For anything more, use [raw].
         */
        fun regex(pattern: String): Grammar =
            Grammar("root ::= $pattern")

        /**
         * Forces the model to emit a syntactically valid JSON value
         * (object, array, string, number, boolean, or null). Doesn't
         * constrain shape — the model can still emit `[]`, `null`, etc.
         * For shape constraints, write a [raw] grammar or use [jsonOf] to
         * get a starting point.
         */
        fun json(): Grammar = Grammar(JSON_GRAMMAR)

        /**
         * Convenience for "emit a JSON object with these fields, all
         * required, all string-valued". Each entry in [fields] becomes a
         * required key whose value is any JSON string.
         *
         * For richer shapes (numbers, nested objects, optional fields) use
         * [raw] with a hand-written grammar.
         */
        fun jsonObject(vararg fields: String): Grammar {
            require(fields.isNotEmpty()) { "jsonObject() needs at least one field" }
            val pairs = fields.joinToString(" \",\" ws ") { name ->
                "\"\\\"${escapeGbnf(name)}\\\":\" ws string"
            }
            return Grammar(
                """
                root ::= "{" ws $pairs ws "}"
                $JSON_FRAGMENTS
                """.trimIndent(),
            )
        }

        private fun escapeGbnf(literal: String): String =
            literal.replace("\\", "\\\\").replace("\"", "\\\"")

        // Standard JSON grammar lifted from llama.cpp's grammars/json.gbnf.
        private const val JSON_GRAMMAR = """
root   ::= object | array | string | number | boolean | null
object ::= "{" ws (string ":" ws value ("," ws string ":" ws value)*)? "}"
array  ::= "[" ws (value ("," ws value)*)? "]"
value  ::= object | array | string | number | boolean | null
string ::= "\"" ( [^"\\] | "\\" (["\\/bfnrt] | "u" [0-9a-fA-F] [0-9a-fA-F] [0-9a-fA-F] [0-9a-fA-F]) )* "\""
number ::= "-"? ([0-9] | [1-9] [0-9]+) ("." [0-9]+)? ([eE] [-+]? [0-9]+)?
boolean ::= "true" | "false"
null   ::= "null"
ws     ::= [ \t\n]*
"""

        // Fragment set reused by jsonObject/jsonArray helpers.
        private const val JSON_FRAGMENTS = """
value  ::= object | array | string | number | boolean | null
object ::= "{" ws (string ":" ws value ("," ws string ":" ws value)*)? "}"
array  ::= "[" ws (value ("," ws value)*)? "]"
string ::= "\"" ( [^"\\] | "\\" (["\\/bfnrt] | "u" [0-9a-fA-F] [0-9a-fA-F] [0-9a-fA-F] [0-9a-fA-F]) )* "\""
number ::= "-"? ([0-9] | [1-9] [0-9]+) ("." [0-9]+)? ([eE] [-+]? [0-9]+)?
boolean ::= "true" | "false"
null   ::= "null"
ws     ::= [ \t\n]*
"""
    }
}
