package dtrbuilder.infrastructure.ast.antlr;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.Token;

/**
 * Minimal base class for JavaScriptLexer.
 * Provides stub implementations for regex detection, strict mode, and brace tracking
 * so the generated ANTLR lexer compiles and runs.
 */
public abstract class JavaScriptLexerBase extends Lexer {

    private int braceDepth = 0;
    private boolean startOfFile = true;
    private boolean inTemplateString = false;
    private boolean strictMode = false;

    public JavaScriptLexerBase(CharStream input) {
        super(input);
    }

    /** Returns true for the first token only (hashbang detection). */
    public boolean IsStartOfFile() {
        if (startOfFile) {
            startOfFile = false;
            return true;
        }
        return false;
    }

    /** Determines if a '/' is a regex or division based on previous token context. */
    public boolean IsRegexPossible() {
        // Conservative: assume regex is possible after operators, open brackets/parens
        return true;
    }

    /** Tracks open braces. */
    public void ProcessOpenBrace() {
        braceDepth++;
    }

    /** Checks if we're inside a template string. */
    public boolean IsInTemplateString() {
        return inTemplateString;
    }

    /** Called when template close brace is encountered. */
    public void ProcessTemplateCloseBrace() {
        inTemplateString = false;
    }

    /** Tracks close braces. */
    public void ProcessCloseBrace() {
        braceDepth--;
        if (braceDepth < 0) braceDepth = 0;
    }

    /** Checks if strict mode is active (reserved word context). */
    public boolean IsStrictMode() {
        return strictMode;
    }

    /** Processes string literals (no-op stub). */
    public void ProcessStringLiteral() {
        // no-op
    }

    /** Called when template string start expression ${ is encountered. */
    public void ProcessTemplateOpenBrace() {
        inTemplateString = true;
    }
}
