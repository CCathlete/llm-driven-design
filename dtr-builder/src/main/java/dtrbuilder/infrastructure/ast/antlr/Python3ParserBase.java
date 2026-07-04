package dtrbuilder.infrastructure.ast.antlr;

import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.TokenStream;

/**
 * Minimal base class for Python3Parser.
 * Provides stub implementations for semantic predicates used in the grammar.
 */
public abstract class Python3ParserBase extends Parser {

    public Python3ParserBase(TokenStream input) {
        super(input);
    }

    /** Predicate: returns false to prevent matching signed_number in literal_pattern context
     *  where +/- would be parsed as part of the number literal rather than as unary operators. */
    public boolean CannotBePlusMinus() {
        return false;
    }

    /** Predicate: returns false to prevent matching dotted names as capture patterns. */
    public boolean CannotBeDotLpEq() {
        return false;
    }
}
