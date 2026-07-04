/*
 * [The MIT License]
 * Python3 ANTLR4 grammar from the ANTLR grammars-v4 repository.
 * Adapted for use with sbt-antlr4 code generation.
 */
parser grammar Python3Parser;

options {
    superClass = Python3ParserBase;
    tokenVocab = Python3Lexer;
}

// Entry points
single_input
    : NEWLINE
    | simple_stmts
    | compound_stmt NEWLINE
    ;

file_input
    : (NEWLINE | stmt)* EOF
    ;

eval_input
    : testlist NEWLINE* EOF
    ;

// Decorators
decorator
    : '@' dotted_name ('(' arglist? ')')? NEWLINE
    ;

decorators
    : decorator+
    ;

decorated
    : decorators (classdef | funcdef | async_funcdef)
    ;

// Functions
async_funcdef
    : ASYNC funcdef
    ;

funcdef
    : 'def' name parameters ('->' test)? ':' block
    ;

parameters
    : '(' typedargslist? ')'
    ;

typedargslist
    : (
        tfpdef ('=' test)? (',' tfpdef ('=' test)?)* (
            ',' (
                '*' tfpdef? (',' tfpdef ('=' test)?)* (',' ('**' tfpdef ','?)?)?
                | '**' tfpdef ','?
            )?
        )?
        | '*' tfpdef? (',' tfpdef ('=' test)?)* (',' ('**' tfpdef ','?)?)?
        | '**' tfpdef ','?
    )
    ;

tfpdef
    : name (':' test)?
    ;

varargslist
    : (
        vfpdef ('=' test)? (',' vfpdef ('=' test)?)* (
            ',' (
                '*' vfpdef? (',' vfpdef ('=' test)?)* (',' ('**' vfpdef ','?)?)?
                | '**' vfpdef (',')?
            )?
        )?
        | '*' vfpdef? (',' vfpdef ('=' test)?)* (',' ('**' vfpdef ','?)?)?
        | '**' vfpdef ','?
    )
    ;

vfpdef
    : name
    ;

// Statements
stmt
    : simple_stmts
    | compound_stmt
    ;

simple_stmts
    : simple_stmt (';' simple_stmt)* ';'? NEWLINE
    ;

simple_stmt
    : (
        expr_stmt
        | del_stmt
        | pass_stmt
        | flow_stmt
        | import_stmt
        | global_stmt
        | nonlocal_stmt
        | assert_stmt
    )
    ;

expr_stmt
    : testlist_star_expr (
        annassign
        | augassign (yield_expr | testlist)
        | ('=' (yield_expr | testlist_star_expr))*
    )
    ;

annassign
    : ':' test ('=' test)?
    ;

testlist_star_expr
    : (test | star_expr) (',' (test | star_expr))* ','?
    ;

augassign
    : (
        '+='
        | '-='
        | '*='
        | '@='
        | '/='
        | '%='
        | '&='
        | '|='
        | '^='
        | '<<='
        | '>>='
        | '**='
        | '//='
    )
    ;

del_stmt
    : 'del' exprlist
    ;

pass_stmt
    : 'pass'
    ;

flow_stmt
    : break_stmt | continue_stmt | return_stmt | raise_stmt | yield_stmt
    ;

break_stmt     : 'break';
continue_stmt  : 'continue';
return_stmt    : 'return' testlist?;
yield_stmt     : yield_expr;
raise_stmt     : 'raise' (test ('from' test)?)?;

// Imports
import_stmt
    : import_name
    | import_from
    ;

import_name
    : 'import' dotted_as_names
    ;

import_from
    : (
        'from' (('.' | '...')* dotted_name | ('.' | '...')+) 'import' (
            '*'
            | '(' import_as_names ')'
            | import_as_names
        )
    )
    ;

import_as_name     : name ('as' name)?;
dotted_as_name     : dotted_name ('as' name)?;
import_as_names    : import_as_name (',' import_as_name)* ','?;
dotted_as_names    : dotted_as_name (',' dotted_as_name)*;
dotted_name        : name ('.' name)*;
global_stmt        : 'global' name (',' name)*;
nonlocal_stmt      : 'nonlocal' name (',' name)*;
assert_stmt        : 'assert' test (',' test)?;

// Compound statements
compound_stmt
    : if_stmt
    | while_stmt
    | for_stmt
    | try_stmt
    | with_stmt
    | funcdef
    | classdef
    | decorated
    | async_stmt
    | match_stmt
    ;

async_stmt      : ASYNC (funcdef | with_stmt | for_stmt);
if_stmt         : 'if' test ':' block ('elif' test ':' block)* ('else' ':' block)?;
while_stmt      : 'while' test ':' block ('else' ':' block)?;
for_stmt        : 'for' exprlist 'in' testlist ':' block ('else' ':' block)?;

try_stmt        : (
    'try' ':' block (
        (except_clause ':' block)+ ('else' ':' block)? ('finally' ':' block)?
        | 'finally' ':' block
    )
);

with_stmt       : 'with' with_item (',' with_item)* ':' block;
with_item       : test ('as' expr)?;
except_clause   : 'except' (test ('as' name)?)?;

block
    : simple_stmts
    | NEWLINE INDENT stmt+ DEDENT
    ;

// Match statement (Python 3.10+)
match_stmt       : 'match' subject_expr ':' NEWLINE INDENT case_block+ DEDENT;
subject_expr     : star_named_expression ',' star_named_expressions? | test;
star_named_expressions : ',' star_named_expression+ ','?;
star_named_expression  : '*' expr | test;
case_block       : 'case' patterns guard? ':' block;
guard            : 'if' test;
patterns         : open_sequence_pattern | pattern;
pattern          : as_pattern | or_pattern;
as_pattern       : or_pattern 'as' pattern_capture_target;
or_pattern       : closed_pattern ('|' closed_pattern)*;

closed_pattern
    : literal_pattern
    | capture_pattern
    | wildcard_pattern
    | value_pattern
    | group_pattern
    | sequence_pattern
    | mapping_pattern
    | class_pattern
    ;

literal_pattern     : signed_number { this.CannotBePlusMinus() }? | complex_number | strings | 'None' | 'True' | 'False';
literal_expr        : signed_number { this.CannotBePlusMinus() }? | complex_number | strings | 'None' | 'True' | 'False';
complex_number      : signed_real_number '+' imaginary_number | signed_real_number '-' imaginary_number;
signed_number       : NUMBER | '-' NUMBER;
signed_real_number  : real_number | '-' real_number;
real_number         : NUMBER;
imaginary_number    : NUMBER;
capture_pattern     : pattern_capture_target;
pattern_capture_target : name { this.CannotBeDotLpEq() }?;
wildcard_pattern    : '_';
value_pattern       : attr { this.CannotBeDotLpEq() }?;
attr                : name ('.' name)+;
name_or_attr        : attr | name;
group_pattern       : '(' pattern ')';
sequence_pattern    : '[' maybe_sequence_pattern? ']' | '(' open_sequence_pattern? ')';
open_sequence_pattern      : maybe_star_pattern ',' maybe_sequence_pattern?;
maybe_sequence_pattern     : maybe_star_pattern (',' maybe_star_pattern)* ','?;
maybe_star_pattern         : star_pattern | pattern;
star_pattern               : '*' pattern_capture_target | '*' wildcard_pattern;
mapping_pattern            : '{' '}' | '{' double_star_pattern ','? '}' | '{' items_pattern ',' double_star_pattern ','? '}' | '{' items_pattern ','? '}';
items_pattern              : key_value_pattern (',' key_value_pattern)*;
key_value_pattern          : (literal_expr | attr) ':' pattern;
double_star_pattern        : '**' pattern_capture_target;

class_pattern
    : name_or_attr '(' ')'
    | name_or_attr '(' positional_patterns ','? ')'
    | name_or_attr '(' keyword_patterns ','? ')'
    | name_or_attr '(' positional_patterns ',' keyword_patterns ','? ')'
    ;

positional_patterns : pattern (',' pattern)*;
keyword_patterns    : keyword_pattern (',' keyword_pattern)*;
keyword_pattern     : name '=' pattern;

// Expressions
test        : or_test ('if' or_test 'else' test)? | lambdef;
test_nocond : or_test | lambdef_nocond;
lambdef     : 'lambda' varargslist? ':' test;
lambdef_nocond : 'lambda' varargslist? ':' test_nocond;
or_test     : and_test ('or' and_test)*;
and_test    : not_test ('and' not_test)*;
not_test    : 'not' not_test | comparison;

comparison  : expr (comp_op expr)*;

comp_op
    : '<' | '>' | '==' | '>=' | '<=' | '<>' | '!='
    | 'in' | 'not' 'in' | 'is' | 'is' 'not'
    ;

star_expr   : '*' expr;
expr        : atom_expr | expr '**' expr | ('+' | '-' | '~')+ expr | expr ('*' | '@' | '/' | '%' | '//') expr | expr ('+' | '-') expr | expr ('<<' | '>>') expr | expr '&' expr | expr '^' expr | expr '|' expr;
atom_expr   : AWAIT? atom trailer*;

atom
    : '(' (yield_expr | testlist_comp)? ')'
    | '[' testlist_comp? ']'
    | '{' dictorsetmaker? '}'
    | name
    | NUMBER
    | STRING+
    | '...'
    | 'None'
    | 'True'
    | 'False'
    ;

name        : NAME | '_' | 'match';

testlist_comp : (test | star_expr) (comp_for | (',' (test | star_expr))* ','?);
trailer     : '(' arglist? ')' | '[' subscriptlist ']' | '.' name;
subscriptlist      : subscript_ (',' subscript_)* ','?;
subscript_         : test | test? ':' test? sliceop?;
sliceop            : ':' test?;
exprlist           : (expr | star_expr) (',' (expr | star_expr))* ','?;
testlist           : test (',' test)* ','?;

dictorsetmaker
    : (
        ((test ':' test | '**' expr) (comp_for | (',' (test ':' test | '**' expr))* ','?))
        | ((test | star_expr) (comp_for | (',' (test | star_expr))* ','?))
    )
    ;

classdef     : 'class' name ('(' arglist? ')')? ':' block;
arglist      : argument (',' argument)* ','?;

argument     : (test comp_for? | test '=' test | '**' test | '*' test);
comp_iter    : comp_for | comp_if;
comp_for     : ASYNC? 'for' exprlist 'in' or_test comp_iter?;
comp_if      : 'if' test_nocond comp_iter?;
encoding_decl : name;
yield_expr   : 'yield' yield_arg?;
yield_arg    : 'from' test | testlist;
strings      : STRING+;
