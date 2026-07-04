/*
 * Simplified TypeScript ANTLR4 Parser.
 * Covers: import/export, class, interface, type alias, function, enum,
 * decorator, const/let declarations for signature extraction.
 * Based on JavaScriptParser with TypeScript-specific rules added.
 */
parser grammar TypeScriptParser;

options {
    tokenVocab = TypeScriptLexer;
    superClass = JavaScriptParserBase;
}

program
    : HashBangLine? sourceElements? EOF
    ;

sourceElement
    : statement
    ;

statement
    : block
    | variableStatement
    | importStatement
    | exportStatement
    | emptyStatement_
    | classDeclaration
    | interfaceDeclaration
    | typeAliasDeclaration
    | enumDeclaration
    | functionDeclaration
    | expressionStatement
    | ifStatement
    | iterationStatement
    | continueStatement
    | breakStatement
    | returnStatement
    | withStatement
    | labelledStatement
    | switchStatement
    | throwStatement
    | tryStatement
    | debuggerStatement
    | decorator
    ;

block           : '{' statementList? '}';
statementList   : statement+;
importStatement : Import importFromBlock;

importFromBlock
    : importDefault? (importNamespace | importModuleItems) importFrom eos
    | StringLiteral eos
    ;

importModuleItems  : '{' (importAliasName ',')* (importAliasName ','?)? '}';
importAliasName    : moduleExportName (As importedBinding)?;
moduleExportName   : identifierName | StringLiteral;
importedBinding    : Identifier;
importDefault      : aliasName ',';
importNamespace    : ('*' | identifierName) (As identifierName)?;
importFrom         : From StringLiteral;
aliasName          : identifierName (As identifierName)?;

exportStatement
    : Export Default? (exportFromBlock | declaration) eos
    | Export Default singleExpression eos
    ;

exportFromBlock   : importNamespace importFrom eos | exportModuleItems importFrom? eos;
exportModuleItems : '{' (exportAliasName ',')* (exportAliasName ','?)? '}';
exportAliasName   : moduleExportName (As moduleExportName)?;

declaration
    : variableStatement
    | classDeclaration
    | interfaceDeclaration
    | typeAliasDeclaration
    | enumDeclaration
    | functionDeclaration
    ;

variableStatement       : variableDeclarationList eos;
variableDeclarationList : varModifier variableDeclaration (',' variableDeclaration)*;
varModifier             : Var | Const | Let;
variableDeclaration     : identifier typeAnnotation? ('=' singleExpression)?;

typeAnnotation : ':' typeReference;

typeReference
    : identifier typeParameters? ('[' ']')?
    | Any | Number | String | Boolean | Symbol | NullLiteral | Undefined | Never | Object | Unknown | Void
    | typeReference '|' typeReference
    | typeReference '&' typeReference
    | Keyof typeReference
    | '[' typeReference ']'
    | '(' typeReference ')'
    ;

typeParameters
    : '<' typeParameter (',' typeParameter)* '>'
    ;

typeParameter
    : identifier ('extends' typeReference)?
    ;

emptyStatement_   : SemiColon;
expressionStatement : {this.notOpenBraceAndNotFunction()}? expressionSequence eos;

ifStatement    : If '(' expressionSequence ')' statement (Else statement)?;

iterationStatement
    : Do statement While '(' expressionSequence ')' eos
    | While '(' expressionSequence ')' statement
    | For '(' (expressionSequence | variableDeclarationList)? ';' expressionSequence? ';' expressionSequence? ')' statement
    | For '(' (singleExpression | variableDeclaration) In expressionSequence ')' statement
    | For Await? '(' (singleExpression | variableDeclaration) Of expressionSequence ')' statement
    ;

continueStatement : Continue ({this.notLineTerminator()}? identifier)? eos;
breakStatement    : Break ({this.notLineTerminator()}? identifier)? eos;
returnStatement   : Return ({this.notLineTerminator()}? expressionSequence)? eos;
withStatement     : With '(' expressionSequence ')' statement;
switchStatement   : Switch '(' expressionSequence ')' caseBlock;
caseBlock         : '{' caseClauses? (defaultClause caseClauses?)? '}';
caseClauses       : caseClause+;
caseClause        : Case expressionSequence ':' statementList?;
defaultClause     : Default ':' statementList?;
labelledStatement : identifier ':' statement;
throwStatement    : Throw {this.notLineTerminator()}? expressionSequence eos;
tryStatement      : Try block (catchProduction finallyProduction? | finallyProduction);
catchProduction   : Catch ('(' identifier typeAnnotation? ')')? block;
finallyProduction : Finally block;
debuggerStatement : Debugger eos;

// TypeScript-specific declarations
interfaceDeclaration
    : Interface identifier typeParameters? interfaceExtends? interfaceBody
    ;

interfaceExtends
    : Extends typeReference (',' typeReference)*
    ;

interfaceBody
    : '{' interfaceMember* '}'
    ;

interfaceMember
    : callSignature
    | constructSignature
    | propertySignature
    | methodSignature
    | indexSignature
    ;

callSignature       : typeParameters? '(' parameterList? ')' typeAnnotation? eos;
constructSignature  : New typeParameters? '(' parameterList? ')' typeAnnotation? eos;
propertySignature   : identifier '?'? typeAnnotation? eos;
methodSignature     : identifier '?'? typeParameters? '(' parameterList? ')' typeAnnotation? eos;
indexSignature      : '[' identifier ':' String ']' typeAnnotation eos;

typeAliasDeclaration
    : Type identifier typeParameters? '=' typeReference eos
    ;

enumDeclaration
    : Enum identifier '{' enumBody? '}'
    ;

enumBody
    : enumMember (',' enumMember)* ','?
    ;

enumMember
    : identifier ('=' singleExpression)?
    ;

decorator
    : AT qualifiedName '(' decoratorArgumentList? ')'
    | AT qualifiedName
    ;

qualifiedName
    : identifier ('.' identifier)*
    ;

decoratorArgumentList
    : decoratorArgument (',' decoratorArgument)*
    ;

decoratorArgument
    : singleExpression
    ;

// Functions
functionDeclaration
    : Async? Function_ '*'? identifier typeParameters? '(' parameterList? ')' typeAnnotation? functionBody
    ;

parameterList
    : parameter (',' parameter)* (',' restParameter)?
    | restParameter
    ;

parameter
    : identifier '?'? typeAnnotation? ('=' singleExpression)?
    ;

restParameter
    : Ellipsis identifier typeAnnotation?
    ;

functionBody
    : '{' sourceElements? '}'
    ;

sourceElements
    : sourceElement+
    ;

// Classes
classDeclaration
    : Abstract? Class identifier typeParameters? classExtends? implementsClause? classBody
    ;

classExtends
    : Extends typeReference
    ;

implementsClause
    : Implements typeReference (',' typeReference)*
    ;

classBody
    : '{' classElement* '}'
    ;

classElement
    : decorator* modifier* methodDefinition
    | decorator* modifier* propertyDefinition
    | modifier* constructorDeclaration
    | indexSignature
    | emptyStatement_
    ;

modifier
    : Public | Private | Protected | Static | Readonly | Abstract
    ;

methodDefinition
    : identifier '?'? typeParameters? '(' parameterList? ')' typeAnnotation? functionBody
    ;

propertyDefinition
    : identifier '!'? '?'? typeAnnotation? initializer? eos
    ;

constructorDeclaration
    : Constructor '(' parameterList? ')' functionBody
    ;

initializer
    : '=' singleExpression
    ;

// Expressions (simplified subset for signature extraction)
singleExpression
    : anonymousFunction                                 # FunctionExpression
    | Class identifier? classTail                       # ClassExpression
    | singleExpression '?.' singleExpression            # OptionalChainExpression
    | singleExpression '?.'? '[' expressionSequence ']' # MemberIndexExpression
    | singleExpression '?'? '.' identifier              # MemberDotExpression
    | New typeReference arguments                                             # NewExpression
    | singleExpression arguments                                              # ArgumentsExpression
    | singleExpression '++'                                                   # PostIncrementExpression
    | singleExpression '--'                                                   # PostDecreaseExpression
    | '++' singleExpression                                                   # PreIncrementExpression
    | '--' singleExpression                                                   # PreDecreaseExpression
    | '+' singleExpression                                                    # UnaryPlusExpression
    | '-' singleExpression                                                    # UnaryMinusExpression
    | '~' singleExpression                                                    # BitNotExpression
    | '!' singleExpression                                                    # NotExpression
    | Await singleExpression                                                  # AwaitExpression
    | <assoc = right> singleExpression '**' singleExpression                  # PowerExpression
    | singleExpression ('*' | '/' | '%') singleExpression                     # MultiplicativeExpression
    | singleExpression ('+' | '-') singleExpression                           # AdditiveExpression
    | singleExpression ('<<' | '>>' | '>>>') singleExpression                 # BitShiftExpression
    | singleExpression ('<' | '>' | '<=' | '>=') singleExpression             # RelationalExpression
    | singleExpression Instanceof singleExpression                            # InstanceofExpression
    | singleExpression In singleExpression                                    # InExpression
    | singleExpression ('==' | '!=' | '===' | '!==') singleExpression         # EqualityExpression
    | singleExpression '&' singleExpression                                   # BitAndExpression
    | singleExpression '^' singleExpression                                   # BitXOrExpression
    | singleExpression '|' singleExpression                                   # BitOrExpression
    | singleExpression '&&' singleExpression                                  # LogicalAndExpression
    | singleExpression '||' singleExpression                                  # LogicalOrExpression
    | singleExpression '?' singleExpression ':' singleExpression              # TernaryExpression
    | <assoc = right> singleExpression '=' singleExpression                   # AssignmentExpression
    | singleExpression assignmentOperator singleExpression                    # AssignmentOperatorExpression
    | Import '(' singleExpression ')'                                         # ImportExpression
    | singleExpression templateStringLiteral                                  # TemplateStringExpression
    | This                                                                    # ThisExpression
    | identifier                                                              # IdentifierExpression
    | Super                                                                   # SuperExpression
    | literal                                                                 # LiteralExpression
    | arrayLiteral                                                            # ArrayLiteralExpression
    | objectLiteral                                                           # ObjectLiteralExpression
    | '(' expressionSequence ')'                                              # ParenthesizedExpression
    ;

anonymousFunction
    : functionDeclaration                                   # NamedFunction
    | Async? Function_ '*'? '(' parameterList? ')' functionBody # AnonymousFunctionDecl
    | arrowFunctionParameters '=>' arrowFunctionBody        # ArrowFunction
    ;

arrowFunctionParameters : identifier | '(' parameterList? ')';
arrowFunctionBody       : singleExpression | functionBody;

classTail
    : (Extends singleExpression)? implementsClause? '{' classElement* '}'
    ;

arrayLiteral       : ('[' elementList ']');
elementList        : ','* arrayElement? (','+ arrayElement)* ','*;
arrayElement       : Ellipsis? singleExpression;

objectLiteral      : '{' (propertyAssignment (',' propertyAssignment)* ','?)? '}';

propertyAssignment
    : propertyName ':' singleExpression           # PropertyExpressionAssignment
    | '[' singleExpression ']' ':' singleExpression # ComputedPropertyExpressionAssignment
    | Async? '*'? propertyName '(' parameterList? ')' functionBody # FunctionProperty
    | getter '(' ')' functionBody                                    # PropertyGetter
    | setter '(' parameter ')' functionBody                          # PropertySetter
    | Ellipsis? singleExpression                                     # PropertyShorthand
    ;

propertyName    : identifierName | StringLiteral | numericLiteral | '[' singleExpression ']';
arguments       : '(' (argument (',' argument)* ','?)? ')';
argument        : Ellipsis? (singleExpression | identifier);
expressionSequence : singleExpression (',' singleExpression)*;

assignmentOperator
    : '*=' | '/=' | '%=' | '+=' | '-=' | '<<=' | '>>=' | '>>>=' | '&='
    | '^=' | '|=' | '**=' | '??='
    ;

literal           : NullLiteral | BooleanLiteral | StringLiteral | templateStringLiteral | RegularExpressionLiteral | numericLiteral | bigintLiteral;
templateStringLiteral : BackTick templateStringAtom* BackTick;
templateStringAtom    : TemplateStringAtom | TemplateStringStartExpression singleExpression TemplateCloseBrace;
numericLiteral    : DecimalLiteral | HexIntegerLiteral | OctalIntegerLiteral | OctalIntegerLiteral2 | BinaryIntegerLiteral;
bigintLiteral     : BigDecimalIntegerLiteral | BigHexIntegerLiteral | BigOctalIntegerLiteral | BigBinaryIntegerLiteral;
getter            : {this.n("get")}? identifier;
setter            : {this.n("set")}? identifier identifier;

identifierName    : identifier | reservedWord;
identifier        : Identifier | Async | As | From | Of;
reservedWord      : keyword | NullLiteral | BooleanLiteral;

keyword
    : Break | Do | Instanceof | Typeof | Case | Else | New | Var | Catch | Finally
    | Return | Void | Continue | For | Switch | While | Debugger | Function_ | This
    | With | Default | If | Throw | Delete | In | Try | Class | Enum | Extends | Super
    | Const | Export | Import | Implements | Private | Public | Protected | Static
    | Package | Async | Await | From | As | Of | Interface | Type | Readonly | Abstract
    | Module | Namespace | Keyof | NullLiteral
    ;

eos
    : SemiColon
    | EOF
    | {this.lineTerminatorAhead()}?
    | {this.closeBrace()}?
    ;
