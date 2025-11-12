grammar UDMLang;

// Parser Rules

udmFile
    : udmHeader udmValue EOF
    ;

udmHeader
    : '@udm-version:' VERSION_NUMBER ('\n' | '\r\n')
      (metaEntry)*
    ;

metaEntry
    : '@source:' STRING ('\n' | '\r\n')
    | '@parsed-at:' STRING ('\n' | '\r\n')
    ;

udmValue
    : scalarValue
    | arrayValue
    | objectValue
    | dateTimeValue
    | dateValue
    | localDateTimeValue
    | timeValue
    | binaryValue
    | lambdaValue
    ;

// Scalar Values
scalarValue
    : '@Scalar' '<' typeName '>' '(' value ')'  # ExplicitScalar
    | STRING                                     # StringScalar
    | NUMBER                                     # NumberScalar
    | BOOLEAN                                    # BooleanScalar
    | NULL                                       # NullScalar
    ;

typeName
    : 'String'
    | 'Number'
    | 'Boolean'
    | 'Null'
    ;

value
    : STRING
    | NUMBER
    | BOOLEAN
    | NULL
    ;

// Array
arrayValue
    : '@Array' '[' (udmValue (',' udmValue)*)? ']'   # ExplicitArray
    | '[' (udmValue (',' udmValue)*)? ']'            # ShorthandArray
    ;

// Object
objectValue
    : '@Object' objectMeta? '{' objectBody '}'       # ExplicitObject
    | '{' simpleProperties '}'                       # ShorthandObject
    ;

objectMeta
    : '(' objectMetaEntry (',' objectMetaEntry)* ')'
    ;

objectMetaEntry
    : 'name:' STRING
    | 'metadata:' metadataMap
    ;

metadataMap
    : '{' (metadataEntry (',' metadataEntry)*)? '}'
    ;

metadataEntry
    : IDENTIFIER ':' STRING
    ;

objectBody
    : (attributesSection)? propertiesSection
    ;

attributesSection
    : 'attributes:' '{' (keyValuePair (',' keyValuePair)*)? '}' ','?
    ;

propertiesSection
    : 'properties:' '{' (keyValuePair (',' keyValuePair)*)? '}'
    ;

simpleProperties
    : (keyValuePair (',' keyValuePair)*)?
    ;

keyValuePair
    : IDENTIFIER ':' udmValue
    | STRING ':' udmValue
    ;

// DateTime
dateTimeValue
    : '@DateTime' '(' STRING ')'
    ;

// Date
dateValue
    : '@Date' '(' STRING ')'
    ;

// LocalDateTime
localDateTimeValue
    : '@LocalDateTime' '(' STRING ')'
    ;

// Time
timeValue
    : '@Time' '(' STRING ')'
    ;

// Binary
binaryValue
    : '@Binary' '(' binaryMeta ')'
    | '@Binary' '(' STRING ')'  // Inline base64
    ;

binaryMeta
    : binaryMetaEntry (',' binaryMetaEntry)*
    ;

binaryMetaEntry
    : 'size:' NUMBER
    | 'encoding:' STRING
    | 'ref:' STRING
    ;

// Lambda
lambdaValue
    : '@Lambda' '(' lambdaMeta? ')'
    ;

lambdaMeta
    : lambdaMetaEntry (',' lambdaMetaEntry)*
    ;

lambdaMetaEntry
    : 'id:' STRING
    | 'arity:' NUMBER
    ;

// Lexer Rules

VERSION_NUMBER
    : [0-9]+ '.' [0-9]+
    ;

STRING
    : '"' (~["\\\r\n] | '\\' .)* '"'
    ;

NUMBER
    : '-'? [0-9]+ ('.' [0-9]+)? ([eE] [+-]? [0-9]+)?
    ;

BOOLEAN
    : 'true'
    | 'false'
    ;

NULL
    : 'null'
    ;

IDENTIFIER
    : [a-zA-Z_][a-zA-Z0-9_]*
    ;

// Whitespace and Comments
WS
    : [ \t\r\n]+ -> skip
    ;

COMMENT
    : '#' ~[\r\n]* -> skip
    ;

BLOCK_COMMENT
    : '/*' .*? '*/' -> skip
    ;
