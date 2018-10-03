parser grammar BrontiParser;

options { tokenVocab=BrontiLexer; }

file                : block EOF ;

block               : statement* ;

blockWithBraces     : LBRACE block RBRACE ;

statement           : functionDeclaration | variableDeclaration | expression | whileStatement | ifStatement | assignment | returnStatement ;

functionDeclaration : FUN ID LPAREN parameterNames RPAREN blockWithBraces ;

variableDeclaration : VAR ID (ASSIGN expression)? ;

parameterNames      : (ID (COMMA ID)*)? ;

whileStatement      : WHILE LPAREN expression RPAREN blockWithBraces ;

ifStatement         : IF LPAREN expression RPAREN blockWithBraces (ELSE blockWithBraces)? ;

assignment          : ID ASSIGN expression ;

returnStatement     : RETURN expression ;

functionCall        : ID LPAREN arguments RPAREN ;

arguments           : (expression (COMMA expression)*)? ;

expression
    : functionCall                                                  # functionCallExpression
    | expression op=(ASTERISK | DIVISION | MODULUS)     expression  # multiplicationExpression
    | expression op=(PLUS | MINUS)                      expression  # summExpression
    | expression op=(GEQ | LEQ | EQ | NEQ | GR | LS)    expression  # compareExpression
    | expression op=(AND | OR)                          expression  # logicalExpression
    | LITERAL                                                       # literalExpression
    | ID                                                            # variableExpression
    | LPAREN expression RPAREN                                      # parenthesisExpression
;
