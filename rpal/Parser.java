package rpal;
import java.util.Stack;



/**
 * Recursive descent parser for RPAL.
 */

public class Parser{

  private LexicalAnalyzer s;
  private Token currentToken;
  Stack<ASTNode> stack;

  public Parser(LexicalAnalyzer s){
    this.s = s;
    stack = new Stack<ASTNode>();
  }
  
  public AST buildAST(){
    beginParse();
    return new AST(stack.pop());
  }

  public void beginParse(){
    eat();
    E(); 
    if(currentToken!=null)
      throw new RuntimeException("Expected EOF.");
  }

  private void eat(){

    do{
      // get the next token
      currentToken = s.readNextToken();
    }while(isCurrentTokenType(TokenType.DELETE));
    if(null != currentToken){
      if(currentToken.getType()==TokenType.IDENTIFIER){
        createTerminalASTNode(ASTNodeType.IDENTIFIER, currentToken.getValue());
      }
      else if(currentToken.getType()==TokenType.INTEGER){
        createTerminalASTNode(ASTNodeType.INTEGER, currentToken.getValue());
      } 
      else if(currentToken.getType()==TokenType.STRING){
        createTerminalASTNode(ASTNodeType.STRING, currentToken.getValue());
      }
    }
  }
  
  private boolean isCurrentToken(TokenType type, String value){

    if(currentToken==null)
      return false;
    if(currentToken.getType()!=type || !currentToken.getValue().equals(value))
      return false;
    return true;
  }


  
  private boolean isCurrentTokenType(TokenType type){
    if(currentToken==null)
      return false;
    if(currentToken.getType()==type)
      return true;
    return false;
  }
  
/**
 * Creates an N-ary Abstract Syntax Tree (AST) node of the specified type.
 *
 * @param type The type of the node to be constructed.
 * @param ariness The number of children to be created for the new node.
 *
 * This method is responsible for building an N-ary AST node with the given type. An N-ary AST node is a node that
 * can have multiple children, represented as a tree-like data structure. The 'type' parameter specifies the type of
 * the node, indicating its role or purpose in the abstract syntax of the language being processed.
 *
 * For example, consider the current state of a stack at a particular point in the parsing process:
 *
 * a <- top of stack
 * b
 * c
 * d
 * ...
 *
 * After invoking buildNAryASTNode(Z, 3), the stack will be modified as follows:
 *
 * X <- top of stack
 * d
 * ...
 *
 * Here, X is an AST node of type Z, and it has three children: a, b, and c. The construction follows an N-ary
 * representation, meaning that the node X is linked to its children in a chain, where each child points to the next
 * sibling.
 *
 * The resulting AST structure will look like this:
 *
 * X
 * |
 * a -> b -> c
 *
 * The buildNAryASTNode method efficiently constructs and organizes the nodes in the AST, allowing for proper parsing
 * and interpretation of the input language's syntax.
 */

  private void buildNAryASTNode(ASTNodeType type, int numOfChildren){
    ASTNode node = new ASTNode();
    node.setType(type);
    while(numOfChildren>0){
      ASTNode child = stack.pop();
      if(node.getChild()!=null)
        child.setSibling(node.getChild());
      node.setChild(child);
      node.setSourceLineNumber(child.getSourceLineNumber());
      numOfChildren--;
    }
    stack.push(node);
  }

  private void createTerminalASTNode(ASTNodeType type, String value){
    ASTNode node = new ASTNode();
    node.setType(type);
    node.setValue(value);
    node.setSourceLineNumber(currentToken.getSourceLineNumber());
    stack.push(node);
  }
  

  /*
   * The following methods implement the grammar rules for RPAL.
   */

  /**
   ####### Expressions ########
   */
  
  /**
   * E-> 'let' D 'in' E => 'let'
   *  -> 'fn' Vb+ '.' E => 'lambda'
   *  ->  Ew;
   */
  private void E(){
    //E -> 'let' D 'in' E => 'let'
    if(isCurrentToken(TokenType.RESERVED, "let")){ 
      eat();
      D();
      if(!isCurrentToken(TokenType.RESERVED, "in"))
        throw new RuntimeException("E:  'in' expected");
      eat();
      E(); 
      buildNAryASTNode(ASTNodeType.LET, 2);
    }
    //E -> 'fn' Vb+ '.' E => 'lambda'
    else if(isCurrentToken(TokenType.RESERVED, "fn")){ 
      int treesToPop = 0;
      eat();
      while(isCurrentTokenType(TokenType.IDENTIFIER) || isCurrentTokenType(TokenType.L_PAREN)){
        VB(); 
        treesToPop++;
      }
      
      if(treesToPop==0)
        throw new RuntimeException("E: at least one 'Vb' expected");
      
      if(!isCurrentToken(TokenType.OPERATOR, "."))
        throw new RuntimeException("E: '.' expected");
      
      eat();
      E(); 
      
      buildNAryASTNode(ASTNodeType.LAMBDA, treesToPop+1); //+1 for the last E 
    }
    else {
      //E -> Ew
      EW();
    }
  }

  /**
   * Ew -> T 'where' Dr => 'where'
   *    -> T;
   */
  private void EW () {
    //Ew -> T
    T(); 
    //Ew -> T 'where' Dr => 'where'
    if(isCurrentToken(TokenType.RESERVED, "where")){ 
      eat();
      DR();
      buildNAryASTNode(ASTNodeType.WHERE, 2);
    }
  }
  
  /*
   # Tuple Expressions ########################################
   */
  
  /*
   * T -> Ta ( ',' Ta )+ => 'tau'
   *   -> Ta;
   */
  private void T(){
    //T -> Ta
    TA();
    int treesToPop = 0;
    //T -> Ta (',' Ta )+ => 'tau'
    while(isCurrentToken(TokenType.OPERATOR, ",")){ 
      eat();
      TA();
      treesToPop++;
    }
    if(treesToPop > 0) buildNAryASTNode(ASTNodeType.TAU, treesToPop+1);
  }

  /**
   * Ta -> Ta 'aug' Tc => 'aug'
   *    -> Tc;
   */
  private void TA () {
    //Ta -> Tc
    TC();
    //Ta -> Ta 'aug' Tc => 'aug'
    while(isCurrentToken(TokenType.RESERVED, "aug")){
      eat();
      TC();
      buildNAryASTNode(ASTNodeType.AUG, 2);
    }
  }

  /**
   * Tc -> B '->' Tc '|' Tc => '->'
   *    -> B;
   */
  private void TC(){
    //Tc -> B
    B(); 
    //Tc -> B '->' Tc '|' Tc => '->'
    if(isCurrentToken(TokenType.OPERATOR, "->")){ 
      eat();
      TC(); 
      if(!isCurrentToken(TokenType.OPERATOR, "|"))
        throw new RuntimeException("TC: '|' expected");
      eat();
      TC();  
      buildNAryASTNode(ASTNodeType.CONDITIONAL, 3);
    }
  }
  
  /**
   * Boolean Expressions
   *******************************/
  
  /**
   
   * B -> B 'or' Bt => 'or'
   *   -> Bt;
   
   */
  private void B(){
    BT(); //B -> Bt
    while(isCurrentToken(TokenType.RESERVED, "or")){ //B -> B 'or' Bt => 'or'
      eat();
      BT();
      buildNAryASTNode(ASTNodeType.OR, 2);
    }
  }
  
  /**
   
   * Bt -> Bs '&' Bt => '&'
   *    -> Bs;
   
   */
  private void BT(){
    BS(); //Bt -> Bs;
    while(isCurrentToken(TokenType.OPERATOR, "&")){ //Bt -> Bt '&' Bs => '&'
      eat();
      BS(); // procBS()
      buildNAryASTNode(ASTNodeType.AND, 2);
    }
  }
  
  /**
   
   * Bs -> 'not Bp => 'not'
   *    -> Bp;
   
   */
  private void BS(){
    if(isCurrentToken(TokenType.RESERVED, "not")){ //Bs -> 'not' Bp => 'not'
      eat();
      BP(); 
      buildNAryASTNode(ASTNodeType.NOT, 1);
    }
    else
      BP(); 
      //Bs -> Bp
      
  }
  
  /**
  
   * Bp -> A ('gr' | '>' ) A => 'gr'
   *    -> A ('ge' | '>=' ) A => 'ge'
   *    -> A ('ls' | '<' ) A => 'ge'
   *    -> A ('le' | '<=' ) A => 'ge'
   *    -> A 'eq' A => 'eq'
   *    -> A 'ne' A => 'ne'
   *    -> A;
   
   */
  private void BP(){
    A(); //Bp -> A
    if(isCurrentToken(TokenType.RESERVED,"gr")||isCurrentToken(TokenType.OPERATOR,">")){ //Bp -> A('gr' | '>' ) A => 'gr'
      eat();
      A(); 
      buildNAryASTNode(ASTNodeType.GR, 2);
    }
    else if(isCurrentToken(TokenType.RESERVED,"ge")||isCurrentToken(TokenType.OPERATOR,">=")){ //Bp -> A ('ge' | '>=') A => 'ge'
      eat();
      A(); 
      buildNAryASTNode(ASTNodeType.GE, 2);
    }
    else if(isCurrentToken(TokenType.RESERVED,"ls")||isCurrentToken(TokenType.OPERATOR,"<")){ //Bp -> A ('ls' | '<' ) A => 'ls'
      eat();
      A(); 
      buildNAryASTNode(ASTNodeType.LS, 2);
    }
    else if(isCurrentToken(TokenType.RESERVED,"le")||isCurrentToken(TokenType.OPERATOR,"<=")){ //Bp -> A ('le' | '<=') A => 'le'
      eat();
      A(); 
      buildNAryASTNode(ASTNodeType.LE, 2);
    }
    else if(isCurrentToken(TokenType.RESERVED,"eq")){ //Bp -> A 'eq' A => 'eq'
      eat();
      A(); 
      buildNAryASTNode(ASTNodeType.EQ, 2);
    }
    else if(isCurrentToken(TokenType.RESERVED,"ne")){ //Bp -> A 'ne' A => 'ne'
      eat();
      A(); 
      buildNAryASTNode(ASTNodeType.NE, 2);
    }
  }
  
  
  /******************************
   * Arithmetic Expressions
   *******************************/
  
  /**
   
   * A -> A '+' At => '+'
   *   -> A '-' At => '-'
   *   ->   '+' At
   *   ->   '-' At => 'neg'
   *   -> At;
   
   */
  private void A(){
    if(isCurrentToken(TokenType.OPERATOR, "+")){ //A -> '+' At
      eat();
      AT(); 
    }
    else if(isCurrentToken(TokenType.OPERATOR, "-")){ //A -> '-' At => 'neg'
      eat();
      AT(); 
      buildNAryASTNode(ASTNodeType.NEG, 1);
    }
    else
      AT(); 
    
    boolean plus = true;
    while(isCurrentToken(TokenType.OPERATOR, "+")||isCurrentToken(TokenType.OPERATOR, "-")){
      if(currentToken.getValue().equals("+"))
        plus = true;
      else if(currentToken.getValue().equals("-"))
        plus = false;
      eat();
      AT(); 
      if(plus) //A -> A '+' At => '+'
        buildNAryASTNode(ASTNodeType.PLUS, 2);
      else //A -> A '-' At => '-'
        buildNAryASTNode(ASTNodeType.MINUS, 2);
    }
  }
  
  /**
   
   * At -> At '*' Af => '*'
   *    -> At '/' Af => '/'
   *    -> Af;
   
   */
  private void AT(){
    AF(); //At -> Af;
    
    boolean mult = true;
    while(isCurrentToken(TokenType.OPERATOR, "*")||isCurrentToken(TokenType.OPERATOR, "/")){
      if(currentToken.getValue().equals("*"))
        mult = true;
      else if(currentToken.getValue().equals("/"))
        mult = false;
      eat();
      AF(); 
      if(mult) //At -> At '*' Af => '*'
        buildNAryASTNode(ASTNodeType.MULT, 2);
      else //At -> At '/' Af => '/'
        buildNAryASTNode(ASTNodeType.DIV, 2);
    }
  }
  
  /**
  
   * Af -> Ap '**' Af => '**'
   *    -> Ap;
  
   */
  private void AF(){
    AP(); // Af -> Ap;
    
    if(isCurrentToken(TokenType.OPERATOR, "**")){ //Af -> Ap '**' Af => '**'
      eat();
      AF();
      buildNAryASTNode(ASTNodeType.EXP, 2);
    }
  }
  
  
  /**
   *
   * Ap -> Ap '@' '&lt;IDENTIFIER&gt;' R => '@'
   *    -> R; 
   
   */
  private void AP(){
    R(); //Ap -> R;
    
    while(isCurrentToken(TokenType.OPERATOR, "@")){ //Ap -> Ap '@' '<IDENTIFIER>' R => '@'
      eat();
      if(!isCurrentTokenType(TokenType.IDENTIFIER))
        throw new RuntimeException("AP: expected Identifier");
      eat();
      R();
      buildNAryASTNode(ASTNodeType.AT, 3);
    }
  }
  
  /******************************
   * Rators and Rands
   *******************************/
  
  /**
  
   * R -> R Rn => 'gamma'
   *   -> Rn;
   
   */
  private void R(){
    RN(); //R -> Rn; NO extra readNT in procRN(). See while loop below for reason.
    eat();
    while(isCurrentTokenType(TokenType.INTEGER)||
        isCurrentTokenType(TokenType.STRING)|| 
        isCurrentTokenType(TokenType.IDENTIFIER)||
        isCurrentToken(TokenType.RESERVED, "true")||
        isCurrentToken(TokenType.RESERVED, "false")||
        isCurrentToken(TokenType.RESERVED, "nil")||
        isCurrentToken(TokenType.RESERVED, "dummy")||
        isCurrentTokenType(TokenType.L_PAREN)){ //R -> R Rn => 'gamma'
      RN(); 
      buildNAryASTNode(ASTNodeType.GAMMA, 2);
      eat();
    }
  }

  /**
   * NOTE: NO extra readNT in procRN. See comments in {@link #R()} for explanation.
  
   * Rn -> '&lt;IDENTIFIER&gt;'
   *    -> '&lt;INTEGER&gt;'
   *    -> '&lt;STRING&gt;'
   *    -> 'true' => 'true'
   *    -> 'false' => 'false'
   *    -> 'nil' => 'nil'
   *    -> '(' E ')'
   *    -> 'dummy' => 'dummy'
  
   */
  private void RN(){
    if(isCurrentTokenType(TokenType.IDENTIFIER)|| //R -> '<IDENTIFIER>'
       isCurrentTokenType(TokenType.INTEGER)|| //R -> '<INTEGER>' 
       isCurrentTokenType(TokenType.STRING)){ //R-> '<STRING>'
    }
    else if(isCurrentToken(TokenType.RESERVED, "true")){ //R -> 'true' => 'true'
      createTerminalASTNode(ASTNodeType.TRUE, "true");
    }
    else if(isCurrentToken(TokenType.RESERVED, "false")){ //R -> 'false' => 'false'
      createTerminalASTNode(ASTNodeType.FALSE, "false");
    } 
    else if(isCurrentToken(TokenType.RESERVED, "nil")){ //R -> 'nil' => 'nil'
      createTerminalASTNode(ASTNodeType.NIL, "nil");
    }
    else if(isCurrentTokenType(TokenType.L_PAREN)){
      eat();
      E(); 
      if(!isCurrentTokenType(TokenType.R_PAREN))
        throw new RuntimeException("RN: ')' expected");
    }
    else if(isCurrentToken(TokenType.RESERVED, "dummy")){ //R -> 'dummy' => 'dummy'
      createTerminalASTNode(ASTNodeType.DUMMY, "dummy");
    }
  }

  /******************************
   * Definitions
   *******************************/
  
  /**
   
   * D -> Da 'within' D => 'within'
   *   -> Da;
  
   */
  private void D(){
    DA(); //D -> Da
    
    if(isCurrentToken(TokenType.RESERVED, "within")){ //D -> Da 'within' D => 'within'
      eat();
      D();
      buildNAryASTNode(ASTNodeType.WITHIN, 2);
    }
  }
  
  /**
   
   * Da -> Dr ('and' Dr)+ => 'and'
   *    -> Dr;
  
   */
  private void DA(){
    DR(); //Da -> Dr
    
    int treesToPop = 0;
    while(isCurrentToken(TokenType.RESERVED, "and")){ //Da -> Dr ( 'and' Dr )+ => 'and'
      eat();
      DR(); 
      treesToPop++;
    }
    if(treesToPop > 0) buildNAryASTNode(ASTNodeType.SIMULTDEF, treesToPop+1);
  }
  
  /**
   * Dr -> 'rec' Db => 'rec'
   *    -> Db;
   */
  private void DR(){
    if(isCurrentToken(TokenType.RESERVED, "rec")){ //Dr -> 'rec' Db => 'rec'
      eat();
      DB(); 
      buildNAryASTNode(ASTNodeType.REC, 1);
    }
    else{ //Dr -> Db
      DB(); 
    }
  }
  
  /**
   
   * Db -> Vl '=' E => '='
   *    -> '&lt;IDENTIFIER&gt;' Vb+ '=' E => 'fcn_form'
   *    -> '(' D ')';
   
   */
  private void DB(){
    if(isCurrentTokenType(TokenType.L_PAREN)){ //Db -> '(' D ')'
      D();
      eat();
      if(!isCurrentTokenType(TokenType.R_PAREN))
        throw new RuntimeException("DB: ')' expected");
      eat();
    }
    else if(isCurrentTokenType(TokenType.IDENTIFIER)){
      eat();
      if(isCurrentToken(TokenType.OPERATOR, ",")){ //Db -> Vl '=' E => '='
        eat();
        VL(); 
        
        if(!isCurrentToken(TokenType.OPERATOR, "="))
          throw new RuntimeException("DB: = expected.");
        buildNAryASTNode(ASTNodeType.COMMA, 2);
        eat();
        E(); 
        buildNAryASTNode(ASTNodeType.EQUAL, 2);
      }
      else{ //Db -> '<IDENTIFIER>' Vb+ '=' E => 'fcn_form'
        if(isCurrentToken(TokenType.OPERATOR, "=")){ //Db -> Vl '=' E => '='; if Vl had only one IDENTIFIER (no commas)
          eat();
          E(); 
          buildNAryASTNode(ASTNodeType.EQUAL, 2);
        }
        else{ //Db -> '<IDENTIFIER>' Vb+ '=' E => 'fcn_form'
          int treesToPop = 0;

          while(isCurrentTokenType(TokenType.IDENTIFIER) || isCurrentTokenType(TokenType.L_PAREN)){
            VB(); 
            treesToPop++;
          }

          if(treesToPop==0)
            throw new RuntimeException("E: at least one 'Vb' expected");

          if(!isCurrentToken(TokenType.OPERATOR, "="))
            throw new RuntimeException("DB: = expected.");

          eat();
          E();

          buildNAryASTNode(ASTNodeType.FCNFORM, treesToPop+2); //+1 for the last E and +1 for the first identifier
        }
      }
    }
  }
  
  /******************************
   * Variables
   *******************************/
  
  /**
   
   * Vb -> '&lt;IDENTIFIER&gt;'
   *    -> '(' Vl ')'
   *    -> '(' ')' => '()'
   
   */
  private void VB(){
    if(isCurrentTokenType(TokenType.IDENTIFIER)){ //Vb -> '<IDENTIFIER>'
      eat();
    }
    else if(isCurrentTokenType(TokenType.L_PAREN)){
      eat();
      if(isCurrentTokenType(TokenType.R_PAREN)){ //Vb -> '(' ')' => '()'
        createTerminalASTNode(ASTNodeType.PAREN, "");
        eat();
      }
      else{ //Vb -> '(' Vl ')'
        VL(); 
        if(!isCurrentTokenType(TokenType.R_PAREN))
          throw new RuntimeException("VB: ')' expected");
        eat();
      }
    }
  }

  /**
   
   * Vl -> '&lt;IDENTIFIER&gt;' list ',' => ','?;
   
   */
  private void VL(){
    if(!isCurrentTokenType(TokenType.IDENTIFIER))
      throw new RuntimeException("VL: Identifier expected");
    else{
      eat();
      int treesToPop = 0;
      while(isCurrentToken(TokenType.OPERATOR, ",")){ //Vl -> '<IDENTIFIER>' list ',' => ','?;
        eat();
        if(!isCurrentTokenType(TokenType.IDENTIFIER))
          throw new RuntimeException("VL: Identifier expected");
        eat();
        treesToPop++;
      }
      if(treesToPop > 0) buildNAryASTNode(ASTNodeType.COMMA, treesToPop+1); //+1 for the first identifier
    }
  }

}

