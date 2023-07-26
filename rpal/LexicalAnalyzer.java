package rpal;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/*
 * This class is responsible for reading the input file and breaking it down into tokens.
 */

public class LexicalAnalyzer{
  
  private BufferedReader buffer;
  private String extraCharRead;
  private final List<String> reservedIdentifiers = Arrays.asList(new String[]{"let","in","within","fn","where","aug","or",
                                                                              "not","gr","ge","ls","le","eq","ne","true",
                                                                              "false","nil","dummy","rec","and"});
  private int sourceLineNumber;
  
  public LexicalAnalyzer(String inputFile) throws IOException{
    sourceLineNumber = 1;
    buffer = new BufferedReader(new InputStreamReader(new FileInputStream(new File(inputFile))));
  }
  
  
  public Token readNextToken(){
    Token nextToken = null;
    String nextChar;
    if(extraCharRead!=null){
      nextChar = extraCharRead;
      extraCharRead = null;
    } else {
      nextChar = readNextChar();
    }
    if(nextChar!=null) {
      nextToken = buildToken(nextChar);
    }
    return nextToken;
  }

  private String readNextChar(){
    String nextChar = null;
    try{
      int c = buffer.read();
      if(c!=-1){
        nextChar = Character.toString((char)c);
        if(nextChar.equals("\n")) {
         sourceLineNumber++;
        }
      } else
          buffer.close();
    } catch (IOException e) {
      
    }

    return nextChar;
  }

  
  private Token buildToken(String currentChar){
    Token nextToken = null;
    if(RegExPatterns.letterPattern.matcher(currentChar).matches()){
      nextToken = buildIdentifierToken(currentChar);
    }
    else if(RegExPatterns.digitPattern.matcher(currentChar).matches()){
      nextToken = buildIntegerToken(currentChar);
    }
    else if(RegExPatterns.opSymbolPattern.matcher(currentChar).matches()){ //comment tokens are also entered from here
      nextToken = buildOperatorToken(currentChar);
    }
    else if(currentChar.equals("\'")){
      nextToken = buildStringToken(currentChar);
    }
    else if(RegExPatterns.whiteSpacePattern.matcher(currentChar).matches()){
      nextToken = buildSpaceToken(currentChar);
    }
    else if(RegExPatterns.punctuationPattern.matcher(currentChar).matches()){
      nextToken = buildPunctuationPattern(currentChar);
    }
    return nextToken;
  }

  
  private Token buildIdentifierToken(String currentChar){
    Token identifierToken = new Token();
    identifierToken.setType(TokenType.IDENTIFIER);
    identifierToken.setSourceLineNumber(sourceLineNumber);
    StringBuilder sBuilder = new StringBuilder(currentChar);
    
    String nextChar = readNextChar();
    while(nextChar!=null){ //null indicates the file ended
      if(RegExPatterns.identifierPattern.matcher(nextChar).matches()){
        sBuilder.append(nextChar);
        nextChar = readNextChar();
      }
      else{
        extraCharRead = nextChar;
        break;
      }
    }
    
    String value = sBuilder.toString();
    if(reservedIdentifiers.contains(value)) {
      identifierToken.setType(TokenType.RESERVED);
    }
    
    identifierToken.setValue(value);
    return identifierToken;
  }

  
  private Token buildIntegerToken(String currentChar){
    Token integerToken = new Token();
    integerToken.setType(TokenType.INTEGER);
    integerToken.setSourceLineNumber(sourceLineNumber);
    StringBuilder sBuilder = new StringBuilder(currentChar);
    
    String nextChar = readNextChar();
    while(nextChar!=null){ //null indicates the file ended
      if(RegExPatterns.digitPattern.matcher(nextChar).matches()){
        sBuilder.append(nextChar);
        nextChar = readNextChar();
      }
      else{
        extraCharRead = nextChar;
        break;
      }
    }
    
    integerToken.setValue(sBuilder.toString());
    return integerToken;
  }

  
  private Token buildOperatorToken(String currentChar){
    Token opSymbolToken = new Token();
    opSymbolToken.setType(TokenType.OPERATOR);
    opSymbolToken.setSourceLineNumber(sourceLineNumber);
    StringBuilder sBuilder = new StringBuilder(currentChar);
    
    String nextChar = readNextChar();
    
    if(currentChar.equals("/") && nextChar.equals("/")) {
      return buildCommentToken(currentChar+nextChar);
    }
    
    while(nextChar!=null){ //null indicates the file ended
      if(RegExPatterns.opSymbolPattern.matcher(nextChar).matches()){
        sBuilder.append(nextChar);
        nextChar = readNextChar();
      }
      else{
        extraCharRead = nextChar;
        break;
      }
    }
    
    opSymbolToken.setValue(sBuilder.toString());
    return opSymbolToken;
  }

  
  private Token buildStringToken(String currentChar){
    Token stringToken = new Token();
    stringToken.setType(TokenType.STRING);
    stringToken.setSourceLineNumber(sourceLineNumber);
    StringBuilder sBuilder = new StringBuilder("");
    
    String nextChar = readNextChar();
    while(nextChar!=null){ 
      if(nextChar.equals("\'")){ 
        stringToken.setValue(sBuilder.toString());
        return stringToken;
      }
      else if(RegExPatterns.stringPattern.matcher(nextChar).matches()){ //match Letter | Digit | Operator_symbol
        sBuilder.append(nextChar);
        nextChar = readNextChar();
      }
    }
    
    return null;
  }
  
  private Token buildSpaceToken(String currentChar){
    Token deleteToken = new Token();
    deleteToken.setType(TokenType.DELETE);
    deleteToken.setSourceLineNumber(sourceLineNumber);
    StringBuilder sBuilder = new StringBuilder(currentChar);
    
    String nextChar = readNextChar();
    while(nextChar!=null){ 
      if(RegExPatterns.whiteSpacePattern.matcher(nextChar).matches()){
        sBuilder.append(nextChar);
        nextChar = readNextChar();
      }
      else{
        extraCharRead = nextChar;
        break;
      }
    }
    
    deleteToken.setValue(sBuilder.toString());
    return deleteToken;
  }
  
  private Token buildCommentToken(String currentChar){
    Token commentToken = new Token();
    commentToken.setType(TokenType.DELETE);
    commentToken.setSourceLineNumber(sourceLineNumber);
    StringBuilder sBuilder = new StringBuilder(currentChar);
    
    String nextChar = readNextChar();
    while(nextChar!=null){ 
      if(RegExPatterns.commentPattern.matcher(nextChar).matches()){
        sBuilder.append(nextChar);
        nextChar = readNextChar();
      }
      else if(nextChar.equals("\n"))
        break;
    }
    
    commentToken.setValue(sBuilder.toString());
    return commentToken;
  }

  private Token buildPunctuationPattern(String currentChar){
    Token punctuationToken = new Token();
    punctuationToken.setSourceLineNumber(sourceLineNumber);
    punctuationToken.setValue(currentChar);
    if(currentChar.equals("(")) {
      punctuationToken.setType(TokenType.L_PAREN);
    }
    else if(currentChar.equals(")")) {
      punctuationToken.setType(TokenType.R_PAREN);
    }
    else if(currentChar.equals(";")) {
      punctuationToken.setType(TokenType.SEMICOLON);
    }
    else if(currentChar.equals(",")) {
      punctuationToken.setType(TokenType.COMMA);
    }
    
    return punctuationToken;
  }
}


class Token{
  private TokenType type;
  private String value;
  private int sourceLineNumber;
  
  public TokenType getType(){
    return type;
  }
  
  public void setType(TokenType type){
    this.type = type;
  }
  
  public String getValue(){
    return value;
  }
  
  public void setValue(String value){
    this.value = value;
  }

  public int getSourceLineNumber(){
    return sourceLineNumber;
  }

  public void setSourceLineNumber(int sourceLineNumber){
    this.sourceLineNumber = sourceLineNumber;
  }
}

enum TokenType{
  IDENTIFIER,
  INTEGER,
  STRING,
  OPERATOR,
  DELETE,
  L_PAREN,
  R_PAREN,
  SEMICOLON,
  COMMA,
  RESERVED; 
}

class RegExPatterns{
  private static final String letterRegexString = "a-zA-Z";
  private static final String digitRegexString = "\\d";
  private static final String spaceRegexString = "[\\s\\t\\n]";
  private static final String punctuationRegexString = "();,";
  private static final String opSymbolRegexString = "+-/~:=|!#%_{}\"*<>.&$^\\[\\]?@";
  private static final String opSymbolToEscapeString = "([*<>.&$^?])";
  
  public static final Pattern letterPattern = Pattern.compile("["+letterRegexString+"]");
  
  public static final Pattern identifierPattern = Pattern.compile("["+letterRegexString+digitRegexString+"_]");

  public static final Pattern digitPattern = Pattern.compile(digitRegexString);

  public static final Pattern punctuationPattern = Pattern.compile("["+punctuationRegexString+"]");

  public static final String opSymbolRegex = "[" + escapeMetaChars(opSymbolRegexString, opSymbolToEscapeString) + "]";
  public static final Pattern opSymbolPattern = Pattern.compile(opSymbolRegex);
  
  public static final Pattern stringPattern = Pattern.compile("[ \\t\\n\\\\"+punctuationRegexString+letterRegexString+digitRegexString+escapeMetaChars(opSymbolRegexString, opSymbolToEscapeString) +"]");
  
  public static final Pattern whiteSpacePattern = Pattern.compile(spaceRegexString);
  
  public static final Pattern commentPattern = Pattern.compile("[ \\t\\'\\\\ \\r"+punctuationRegexString+letterRegexString+digitRegexString+escapeMetaChars(opSymbolRegexString, opSymbolToEscapeString)+"]"); //the \\r is for Windows LF; not really required since we're targeting *nix systems
  
  private static String escapeMetaChars(String inputString, String charsToEscape){
    return inputString.replaceAll(charsToEscape,"\\\\\\\\$1");
  }
}