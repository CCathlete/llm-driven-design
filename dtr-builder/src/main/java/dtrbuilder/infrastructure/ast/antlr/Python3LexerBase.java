package dtrbuilder.infrastructure.ast.antlr;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.Token;

/**
 * Minimal base class for Python3Lexer.
 * Provides stub implementations for indentation handling and brace tracking
 * so the generated ANTLR lexer compiles and runs.
 */
public abstract class Python3LexerBase extends Lexer {

    private int braceDepth = 0;
    private boolean atStart = true;

    public Python3LexerBase(CharStream input) {
        super(input);
    }

    /** Returns true only for the very first token. */
    public boolean atStartOfInput() {
        boolean result = atStart;
        atStart = false;
        return result;
    }

    /** Called on every NEWLINE token. Resets indentation tracking after first line. */
    public void onNewLine() {
        // Minimal: no indentation-based dedent handling
    }

    /** Tracks opening braces for dedent logic. */
    public void openBrace() {
        braceDepth++;
    }

    /** Tracks closing braces for dedent logic. */
    public void closeBrace() {
        braceDepth--;
        if (braceDepth < 0) braceDepth = 0;
    }
}
