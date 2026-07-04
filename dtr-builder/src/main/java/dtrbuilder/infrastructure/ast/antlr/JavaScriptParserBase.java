package dtrbuilder.infrastructure.ast.antlr;

import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.TokenStream;

/**
 * Minimal base class for JavaScriptParser.
 * Provides stub implementations for semantic predicates used in the grammar.
 */
public abstract class JavaScriptParserBase extends Parser {

    public JavaScriptParserBase(TokenStream input) {
        super(input);
    }

    /** Returns true if current token is not an open brace and not 'function'. */
    public boolean notOpenBraceAndNotFunction() {
        return true;
    }

    /** Returns true if there is no line terminator before the next token. */
    public boolean notLineTerminator() {
        return true;
    }

    /** Returns true if the next token is a close brace. */
    public boolean closeBrace() {
        return false;
    }

    /** Checks if the given identifier matches a specific name (for getter/setter detection). */
    public boolean n(String s) {
        return false;
    }

    /** Checks if there is a line terminator before the next token.
     *  Used by ASI (automatic semicolon insertion). Simplified stub. */
    public boolean lineTerminatorAhead() {
        return false;
    }
}
