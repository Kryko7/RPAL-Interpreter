package rpal;
import java.util.Stack;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;


/*
 * This class implements a CSE machine.
 */

public class CSEMachine{

  private Stack<ASTNode> valueStack;
  private Delta rootDelta;

  public CSEMachine(AST ast){

    if(!ast.isStandardized())
      throw new RuntimeException("AST has NOT been standardized!"); //should never happen
    rootDelta = ast.createDeltas();
    rootDelta.setLinkedEnv(new Environment()); //primitive environment
    valueStack = new Stack<ASTNode>();
  }

  public void evaluateProgram(){
    processControlStack(rootDelta, rootDelta.getLinkedEnv());
  }
  

  private void processControlStack(Delta currentDelta, Environment currentEnv){
    //create a new control stack and add all of the delta's body to it so that the delta's body isn't
    //modified whenever the control stack is popped in all the functions below
    Stack<ASTNode> controlStack = new Stack<ASTNode>();
    controlStack.addAll(currentDelta.getBody());
    
    while(!controlStack.isEmpty())
      processCurrentNode(currentDelta, currentEnv, controlStack);
  }

  private void processCurrentNode(Delta currentDelta, Environment currentEnv, Stack<ASTNode> currentControlStack){
    ASTNode node = currentControlStack.pop();
    if(applyBinaryOperation(node))
      return;
    else if(applyUnaryOperation(node))
      return;
    else{
      switch(node.getType()){
        case IDENTIFIER:
          handleIdentifiers(node, currentEnv);
          break;
        case NIL:
        case TAU:
          createTuple(node);
          break;
        case BETA:
          handleBeta((Beta)node, currentControlStack);
          break;
        case GAMMA:
          applyGamma(currentDelta, node, currentEnv, currentControlStack);
          break;
        case DELTA:
          ((Delta)node).setLinkedEnv(currentEnv); //RULE 2
          valueStack.push(node);
          break;
        default:
          // Although we use ASTNodes, a CSEM will only ever see a subset of all possible ASTNodeTypes.
          // These are the types that are NOT standardized away into lambdas and gammas. E.g. types
          // such as LET, WHERE, WITHIN, SIMULTDEF etc will NEVER be encountered by the CSEM
          valueStack.push(node);
          break;
      }
    }
  }



  // RULE 6
  private boolean applyBinaryOperation(ASTNode rator){
    switch(rator.getType()){
      case PLUS:
      case MINUS:
      case MULT:
      case DIV:
      case EXP:
      case LS:
      case LE:
      case GR:
      case GE:
        binaryArithmeticOp(rator.getType());
        return true;
      case EQ:
      case NE:
        binaryLogicalEqNeOp(rator.getType());
        return true;
      case OR:
      case AND:
        binaryLogicalOrAndOp(rator.getType());
        return true;
      case AUG:
        augTuples();
        return true;
      default:
        return false;
    }
  }

  private void binaryArithmeticOp(ASTNodeType type){
    ASTNode rand1 = valueStack.pop();
    ASTNode rand2 = valueStack.pop();
    if(rand1.getType()!=ASTNodeType.INTEGER || rand2.getType()!=ASTNodeType.INTEGER)
      EvaluationError.printError(rand1.getSourceLineNumber(), "Expected two integers; was given \""+rand1.getValue()+"\", \""+rand2.getValue()+"\"");

    ASTNode result = new ASTNode();
    result.setType(ASTNodeType.INTEGER);

    switch(type){
      case PLUS:
        result.setValue(Integer.toString(Integer.parseInt(rand1.getValue())+Integer.parseInt(rand2.getValue())));
        break;
      case MINUS:
        result.setValue(Integer.toString(Integer.parseInt(rand1.getValue())-Integer.parseInt(rand2.getValue())));
        break;
      case MULT:
        result.setValue(Integer.toString(Integer.parseInt(rand1.getValue())*Integer.parseInt(rand2.getValue())));
        break;
      case DIV:
        result.setValue(Integer.toString(Integer.parseInt(rand1.getValue())/Integer.parseInt(rand2.getValue())));
        break;
      case EXP:
        result.setValue(Integer.toString((int)Math.pow(Integer.parseInt(rand1.getValue()), Integer.parseInt(rand2.getValue()))));
        break;
      case LS:
        if(Integer.parseInt(rand1.getValue())<Integer.parseInt(rand2.getValue()))
          pushTrueNode();
        else
          pushFalseNode();
        return;
      case LE:
        if(Integer.parseInt(rand1.getValue())<=Integer.parseInt(rand2.getValue()))
          pushTrueNode();
        else
          pushFalseNode();
        return;
      case GR:
        if(Integer.parseInt(rand1.getValue())>Integer.parseInt(rand2.getValue()))
          pushTrueNode();
        else
          pushFalseNode();
        return;
      case GE:
        if(Integer.parseInt(rand1.getValue())>=Integer.parseInt(rand2.getValue()))
          pushTrueNode();
        else
          pushFalseNode();
        return;
      default:
        break;
    }
    valueStack.push(result);
  }

  private void binaryLogicalEqNeOp(ASTNodeType type){
    ASTNode rand1 = valueStack.pop();
    ASTNode rand2 = valueStack.pop();

    if(rand1.getType()==ASTNodeType.TRUE || rand1.getType()==ASTNodeType.FALSE){
      if(rand2.getType()!=ASTNodeType.TRUE && rand2.getType()!=ASTNodeType.FALSE)
        EvaluationError.printError(rand1.getSourceLineNumber(), "Cannot compare dissimilar types; was given \""+rand1.getValue()+"\", \""+rand2.getValue()+"\"");
      compareTruthValues(rand1, rand2, type);
      return;
    }

    if(rand1.getType()!=rand2.getType())
      EvaluationError.printError(rand1.getSourceLineNumber(), "Cannot compare dissimilar types; was given \""+rand1.getValue()+"\", \""+rand2.getValue()+"\"");

    if(rand1.getType()==ASTNodeType.STRING)
      compareStrings(rand1, rand2, type);
    else if(rand1.getType()==ASTNodeType.INTEGER)
      compareIntegers(rand1, rand2, type);
    else
      EvaluationError.printError(rand1.getSourceLineNumber(), "Don't know how to " + type + " \""+rand1.getValue()+"\", \""+rand2.getValue()+"\"");

  }

  private void compareTruthValues(ASTNode rand1, ASTNode rand2, ASTNodeType type){
    if(rand1.getType()==rand2.getType())
      if(type==ASTNodeType.EQ)
        pushTrueNode();
      else
        pushFalseNode();
    else
      if(type==ASTNodeType.EQ)
        pushFalseNode();
      else
        pushTrueNode();
  }

  private void compareStrings(ASTNode rand1, ASTNode rand2, ASTNodeType type){
    if(rand1.getValue().equals(rand2.getValue()))
      if(type==ASTNodeType.EQ)
        pushTrueNode();
      else
        pushFalseNode();
    else
      if(type==ASTNodeType.EQ)
        pushFalseNode();
      else
        pushTrueNode();
  }

  private void compareIntegers(ASTNode rand1, ASTNode rand2, ASTNodeType type){
    if(Integer.parseInt(rand1.getValue())==Integer.parseInt(rand2.getValue()))
      if(type==ASTNodeType.EQ)
        pushTrueNode();
      else
        pushFalseNode();
    else
      if(type==ASTNodeType.EQ)
        pushFalseNode();
      else
        pushTrueNode();
  }

  private void binaryLogicalOrAndOp(ASTNodeType type){
    ASTNode rand1 = valueStack.pop();
    ASTNode rand2 = valueStack.pop();

    if((rand1.getType()==ASTNodeType.TRUE || rand1.getType()==ASTNodeType.FALSE) &&
        (rand2.getType()==ASTNodeType.TRUE || rand2.getType()==ASTNodeType.FALSE)){
      orAndTruthValues(rand1, rand2, type);
      return;
    }

    EvaluationError.printError(rand1.getSourceLineNumber(), "Don't know how to " + type + " \""+rand1.getValue()+"\", \""+rand2.getValue()+"\"");
  }

  private void orAndTruthValues(ASTNode rand1, ASTNode rand2, ASTNodeType type){
    if(type==ASTNodeType.OR){
      if(rand1.getType()==ASTNodeType.TRUE || rand2.getType()==ASTNodeType.TRUE)
        pushTrueNode();
      else
        pushFalseNode();
    }
    else{
      if(rand1.getType()==ASTNodeType.TRUE && rand2.getType()==ASTNodeType.TRUE)
        pushTrueNode();
      else
        pushFalseNode();
    }
  }

  private void augTuples(){
    ASTNode rand1 = valueStack.pop();
    ASTNode rand2 = valueStack.pop();

    if(rand1.getType()!=ASTNodeType.TUPLE)
      EvaluationError.printError(rand1.getSourceLineNumber(), "Cannot augment a non-tuple \""+rand1.getValue()+"\"");

    ASTNode childNode = rand1.getChild();
    if(childNode==null)
      rand1.setChild(rand2);
    else{
      while(childNode.getSibling()!=null)
        childNode = childNode.getSibling();
      childNode.setSibling(rand2);
    }
    rand2.setSibling(null);

    valueStack.push(rand1);
  }

  // RULE 7
  private boolean applyUnaryOperation(ASTNode rator){
    switch(rator.getType()){
      case NOT:
        not();
        return true;
      case NEG:
        neg();
        return true;
      default:
        return false;
    }
  }

  private void not(){
    ASTNode rand = valueStack.pop();
    if(rand.getType()!=ASTNodeType.TRUE && rand.getType()!=ASTNodeType.FALSE)
      EvaluationError.printError(rand.getSourceLineNumber(), "Expecting a truthvalue; was given \""+rand.getValue()+"\"");

    if(rand.getType()==ASTNodeType.TRUE)
      pushFalseNode();
    else
      pushTrueNode();
  }

  private void neg(){
    ASTNode rand = valueStack.pop();
    if(rand.getType()!=ASTNodeType.INTEGER)
      EvaluationError.printError(rand.getSourceLineNumber(), "Expecting a truthvalue; was given \""+rand.getValue()+"\"");

    ASTNode result = new ASTNode();
    result.setType(ASTNodeType.INTEGER);
    result.setValue(Integer.toString(-1*Integer.parseInt(rand.getValue())));
    valueStack.push(result);
  }

  //RULE 3
  private void applyGamma(Delta currentDelta, ASTNode node, Environment currentEnv, Stack<ASTNode> currentControlStack){
    ASTNode rator = valueStack.pop();
    ASTNode rand = valueStack.pop();

    if(rator.getType()==ASTNodeType.DELTA){
      Delta nextDelta = (Delta) rator;
      
      //Delta has a link to the environment in effect when it is pushed on to the value stack (search
      //for 'RULE 2' in this file to see where it's done)
      //We construct a new environment here that will contain all the bindings (single or multiple)
      //required by this Delta. This new environment will link back to the environment carried by the Delta.
      Environment newEnv = new Environment();
      newEnv.setParent(nextDelta.getLinkedEnv());
      
      //RULE 4
      if(nextDelta.getBoundVars().size()==1){
        newEnv.addMapping(nextDelta.getBoundVars().get(0), rand);
      }
      //RULE 11
      else{
        if(rand.getType()!=ASTNodeType.TUPLE)
          EvaluationError.printError(rand.getSourceLineNumber(), "Expected a tuple; was given \""+rand.getValue()+"\"");
        
        for(int i = 0; i < nextDelta.getBoundVars().size(); i++){
          newEnv.addMapping(nextDelta.getBoundVars().get(i), getNthTupleChild((Tuple)rand, i+1)); //+ 1 coz tuple indexing starts at 1
        }
      }
      
      processControlStack(nextDelta, newEnv);
      return;
    }
    else if(rator.getType()==ASTNodeType.YSTAR){
      //RULE 12
      if(rand.getType()!=ASTNodeType.DELTA)
        EvaluationError.printError(rand.getSourceLineNumber(), "Expected a Delta; was given \""+rand.getValue()+"\"");
      
      Eta etaNode = new Eta();
      etaNode.setDelta((Delta)rand);
      valueStack.push(etaNode);
      return;
    }
    else if(rator.getType()==ASTNodeType.ETA){
      //RULE 13
      //push back the rand, the eta and then the delta it contains
      valueStack.push(rand);
      valueStack.push(rator);
      valueStack.push(((Eta)rator).getDelta());
      //push back two gammas (one for the eta and one for the delta)
      currentControlStack.push(node);
      currentControlStack.push(node);
      return;
    }
    else if(rator.getType()==ASTNodeType.TUPLE){
      tupleSelection((Tuple)rator, rand);
      return;
    }
    else if(evaluateReservedIdentifiers(rator, rand, currentControlStack))
      return;
    else
      EvaluationError.printError(rator.getSourceLineNumber(), "Don't know how to evaluate \""+rator.getValue()+"\"");
  }

  private boolean evaluateReservedIdentifiers(ASTNode rator, ASTNode rand, Stack<ASTNode> currentControlStack){
    switch(rator.getValue()){
      case "Isinteger":
        checkTypeAndPushTrueOrFalse(rand, ASTNodeType.INTEGER);
        return true;
      case "Isstring":
        checkTypeAndPushTrueOrFalse(rand, ASTNodeType.STRING);
        return true;
      case "Isdummy":
        checkTypeAndPushTrueOrFalse(rand, ASTNodeType.DUMMY);
        return true;
      case "Isfunction":
        checkTypeAndPushTrueOrFalse(rand, ASTNodeType.DELTA);
        return true;
      case "Istuple":
        checkTypeAndPushTrueOrFalse(rand, ASTNodeType.TUPLE);
        return true;
      case "Istruthvalue":
        if(rand.getType()==ASTNodeType.TRUE||rand.getType()==ASTNodeType.FALSE)
          pushTrueNode();
        else
          pushFalseNode();
        return true;
      case "Stem":
        stem(rand);
        return true;
      case "Stern":
        stern(rand);
        return true;
      case "Conc":
      case "conc": //typos
        conc(rand, currentControlStack);
        return true;
      case "Print":
      case "print": //typos
        printNodeValue(rand);
        pushDummyNode();
        return true;
      case "ItoS":
        itos(rand);
        return true;
      case "Order":
        order(rand);
        return true;
      case "Null":
        isNullTuple(rand);
        return true;
      default:
        return false;
    }
  }

  private void checkTypeAndPushTrueOrFalse(ASTNode rand, ASTNodeType type){
    if(rand.getType()==type)
      pushTrueNode();
    else
      pushFalseNode();
  }

  private void pushTrueNode(){
    ASTNode trueNode = new ASTNode();
    trueNode.setType(ASTNodeType.TRUE);
    trueNode.setValue("true");
    valueStack.push(trueNode);
  }
  
  private void pushFalseNode(){
    ASTNode falseNode = new ASTNode();
    falseNode.setType(ASTNodeType.FALSE);
    falseNode.setValue("false");
    valueStack.push(falseNode);
  }

  private void pushDummyNode(){
    ASTNode falseNode = new ASTNode();
    falseNode.setType(ASTNodeType.DUMMY);
    valueStack.push(falseNode);
  }

  private void stem(ASTNode rand){
    if(rand.getType()!=ASTNodeType.STRING)
      EvaluationError.printError(rand.getSourceLineNumber(), "Expected a string; was given \""+rand.getValue()+"\"");
    
    if(rand.getValue().isEmpty())
      rand.setValue("");
    else
      rand.setValue(rand.getValue().substring(0,1));
    
    valueStack.push(rand);
  }

  private void stern(ASTNode rand){
    if(rand.getType()!=ASTNodeType.STRING)
      EvaluationError.printError(rand.getSourceLineNumber(), "Expected a string; was given \""+rand.getValue()+"\"");
    
    if(rand.getValue().isEmpty() || rand.getValue().length()==1)
      rand.setValue("");
    else
      rand.setValue(rand.getValue().substring(1));
    
    valueStack.push(rand);
  }

  private void conc(ASTNode rand1, Stack<ASTNode> currentControlStack){
    currentControlStack.pop();
    ASTNode rand2 = valueStack.pop();
    if(rand1.getType()!=ASTNodeType.STRING || rand2.getType()!=ASTNodeType.STRING)
      EvaluationError.printError(rand1.getSourceLineNumber(), "Expected two strings; was given \""+rand1.getValue()+"\", \""+rand2.getValue()+"\"");

    ASTNode result = new ASTNode();
    result.setType(ASTNodeType.STRING);
    result.setValue(rand1.getValue()+rand2.getValue());
    
    valueStack.push(result);
  }

  private void itos(ASTNode rand){
    if(rand.getType()!=ASTNodeType.INTEGER)
      EvaluationError.printError(rand.getSourceLineNumber(), "Expected an integer; was given \""+rand.getValue()+"\"");
    
    rand.setType(ASTNodeType.STRING); //all values are stored internally as strings, so nothing else to do
    valueStack.push(rand);
  }

  private void order(ASTNode rand){
    if(rand.getType()!=ASTNodeType.TUPLE)
      EvaluationError.printError(rand.getSourceLineNumber(), "Expected a tuple; was given \""+rand.getValue()+"\"");

    ASTNode result = new ASTNode();
    result.setType(ASTNodeType.INTEGER);
    result.setValue(Integer.toString(getNumChildren(rand)));
    
    valueStack.push(result);
  }

  private void isNullTuple(ASTNode rand){
    if(rand.getType()!=ASTNodeType.TUPLE)
      EvaluationError.printError(rand.getSourceLineNumber(), "Expected a tuple; was given \""+rand.getValue()+"\"");

    if(getNumChildren(rand)==0)
      pushTrueNode();
    else
      pushFalseNode();
  }

  // RULE 10
  private void tupleSelection(Tuple rator, ASTNode rand){
    if(rand.getType()!=ASTNodeType.INTEGER)
      EvaluationError.printError(rand.getSourceLineNumber(), "Non-integer tuple selection with \""+rand.getValue()+"\"");

    ASTNode result = getNthTupleChild(rator, Integer.parseInt(rand.getValue()));
    if(result==null)
      EvaluationError.printError(rand.getSourceLineNumber(), "Tuple selection index "+rand.getValue()+" out of bounds");

    valueStack.push(result);
  }

  /**
   * Get the nth element of the tuple. Note that n starts from 1 and NOT 0.
   * @param tupleNode
   * @param n n starts from 1 and NOT 0.
   * @return
   */
  private ASTNode getNthTupleChild(Tuple tupleNode, int n){
    ASTNode childNode = tupleNode.getChild();
    for(int i=1;i<n;++i){ //tuple selection index starts at 1
      if(childNode==null)
        break;
      childNode = childNode.getSibling();
    }
    return childNode;
  }

  private void handleIdentifiers(ASTNode node, Environment currentEnv){
    if(currentEnv.lookup(node.getValue())!=null) // RULE 1
      valueStack.push(currentEnv.lookup(node.getValue()));
    else if(isReservedIdentifier(node.getValue()))
      valueStack.push(node);
    else
      EvaluationError.printError(node.getSourceLineNumber(), "Undeclared identifier \""+node.getValue()+"\"");
  }

  //RULE 9
  private void createTuple(ASTNode node){
    int numChildren = getNumChildren(node);
    Tuple tupleNode = new Tuple();
    if(numChildren==0){
      valueStack.push(tupleNode);
      return;
    }

    ASTNode childNode = null, tempNode = null;
    for(int i=0;i<numChildren;++i){
      if(childNode==null)
        childNode = valueStack.pop();
      else if(tempNode==null){
        tempNode = valueStack.pop();
        childNode.setSibling(tempNode);
      }
      else{
        tempNode.setSibling(valueStack.pop());
        tempNode = tempNode.getSibling();
      }
    }
    tempNode.setSibling(null);
    tupleNode.setChild(childNode);
    valueStack.push(tupleNode);
  }

  // RULE 8
  private void handleBeta(Beta node, Stack<ASTNode> currentControlStack){
    ASTNode conditionResultNode = valueStack.pop();

    if(conditionResultNode.getType()!=ASTNodeType.TRUE && conditionResultNode.getType()!=ASTNodeType.FALSE)
      EvaluationError.printError(conditionResultNode.getSourceLineNumber(), "Expecting a truthvalue; found \""+conditionResultNode.getValue()+"\"");

    if(conditionResultNode.getType()==ASTNodeType.TRUE)
      currentControlStack.addAll(node.getThenBody());
    else
      currentControlStack.addAll(node.getElseBody());
  }

  private int getNumChildren(ASTNode node){
    int numChildren = 0;
    ASTNode childNode = node.getChild();
    while(childNode!=null){
      numChildren++;
      childNode = childNode.getSibling();
    }
    return numChildren;
  }
  
  private void printNodeValue(ASTNode rand){
    String evaluationResult = rand.getValue();
    evaluationResult = evaluationResult.replace("\\t", "\t");
    evaluationResult = evaluationResult.replace("\\n", "\n");
    System.out.print(evaluationResult);
  }

  // Note how this list is different from the one defined in Scanner.java
  private boolean isReservedIdentifier(String value){
    switch(value){
      case "Isinteger":
      case "Isstring":
      case "Istuple":
      case "Isdummy":
      case "Istruthvalue":
      case "Isfunction":
      case "ItoS":
      case "Order":
      case "Conc":
      case "conc": //typos
      case "Stern":
      case "Stem":
      case "Null":
      case "Print":
      case "print": //typos
      case "neg":
        return true;
    }
    return false;
  }

}

class Beta extends ASTNode{
  private Stack<ASTNode> thenBody;
  private Stack<ASTNode> elseBody;
  
  public Beta(){
    setType(ASTNodeType.BETA);
    thenBody = new Stack<ASTNode>();
    elseBody = new Stack<ASTNode>();
  }
  
  public Beta accept(NodeCopier nodeCopier){
    return nodeCopier.copy(this);
  }

  public Stack<ASTNode> getThenBody(){
    return thenBody;
  }

  public Stack<ASTNode> getElseBody(){
    return elseBody;
  }

  public void setThenBody(Stack<ASTNode> thenBody){
    this.thenBody = thenBody;
  }

  public void setElseBody(Stack<ASTNode> elseBody){
    this.elseBody = elseBody;
  }
  
}

class Delta extends ASTNode{
  private List<String> boundVars;
  private Environment linkedEnv; //environment in effect when this Delta was pushed on to the value stack
  private Stack<ASTNode> body;
  private int index;
  
  public Delta(){
    setType(ASTNodeType.DELTA);
    boundVars = new ArrayList<String>();
  }
  
  public Delta accept(NodeCopier nodeCopier){
    return nodeCopier.copy(this);
  }
  
  //used if the program evaluation results in a partial application
  @Override
  public String getValue(){
    return "[lambda closure: "+boundVars.get(0)+": "+index+"]";
  }

  public List<String> getBoundVars(){
    return boundVars;
  }
  
  public void addBoundVars(String boundVar){
    boundVars.add(boundVar);
  }
  
  public void setBoundVars(List<String> boundVars){
    this.boundVars = boundVars;
  }
  
  public Stack<ASTNode> getBody(){
    return body;
  }
  
  public void setBody(Stack<ASTNode> body){
    this.body = body;
  }
  
  public int getIndex(){
    return index;
  }

  public void setIndex(int index){
    this.index = index;
  }

  public Environment getLinkedEnv(){
    return linkedEnv;
  }

  public void setLinkedEnv(Environment linkedEnv){
    this.linkedEnv = linkedEnv;
  }
}


class Environment{
  private Environment parent;
  private Map<String, ASTNode> nameValueMap;
  
  public Environment(){
    nameValueMap = new HashMap<String, ASTNode>();
  }

  public Environment getParent(){
    return parent;
  }

  public void setParent(Environment parent){
    this.parent = parent;
  }
  
  /**
   * Tries to find the binding of the given key in the mappings of this Environment's
   * inheritance hierarchy, starting with the Environment this method is invoked on.
   * 
   * @param key key the mapping of which to find
   * @return ASTNode that corresponds to the mapping of the key passed in as an argument
   *         or null if no mapping was found
   */
  public ASTNode lookup(String key){
    ASTNode retValue = null;
    Map<String, ASTNode> map = nameValueMap;
    
    retValue = map.get(key);
    
    if(retValue!=null)
      return retValue.accept(new NodeCopier());
    
    if(parent!=null)
      return parent.lookup(key);
    else
      return null;
  }
  
  public void addMapping(String key, ASTNode value){
    nameValueMap.put(key, value);
  }
}

class Eta extends ASTNode{
  private Delta delta;
  
  public Eta(){
    setType(ASTNodeType.ETA);
  }
  
  //used if the program evaluation results in a partial application
  @Override
  public String getValue(){
    return "[eta closure: "+delta.getBoundVars().get(0)+": "+delta.getIndex()+"]";
  }
  
  public Eta accept(NodeCopier nodeCopier){
    return nodeCopier.copy(this);
  }

  public Delta getDelta(){
    return delta;
  }

  public void setDelta(Delta delta){
    this.delta = delta;
  }
  
}


class EvaluationError{
  
  public static void printError(int sourceLineNumber, String message){
    //System.out.println(P2.fileName+":"+sourceLineNumber+": "+message);
    System.exit(1);
  }

}

/**
 * The CopyNode class is responsible for copying AST nodes and their children.
 * It provides methods to copy specific types of AST nodes.
 */

class NodeCopier{
  
  public ASTNode copy(ASTNode astNode){
    ASTNode copy = new ASTNode();
    if(astNode.getChild()!=null) {
      copy.setChild(astNode.getChild().accept(this));
    }
    if(astNode.getSibling()!=null)
      copy.setSibling(astNode.getSibling().accept(this));
    copy.setType(astNode.getType());
    copy.setValue(astNode.getValue());
    copy.setSourceLineNumber(astNode.getSourceLineNumber());
    return copy;
  }
  
  public Beta copy(Beta beta){
    Beta copy = new Beta();
    if(beta.getChild()!=null) {
      copy.setChild(beta.getChild().accept(this));
    }
    if(beta.getSibling()!=null) {
      copy.setSibling(beta.getSibling().accept(this));
    }
    copy.setType(beta.getType());
    copy.setValue(beta.getValue());
    copy.setSourceLineNumber(beta.getSourceLineNumber());
    
    Stack<ASTNode> thenBodyCopy = new Stack<ASTNode>();
    for(ASTNode thenBodyElement: beta.getThenBody()){
      thenBodyCopy.add(thenBodyElement.accept(this));
    }
    copy.setThenBody(thenBodyCopy);
    
    Stack<ASTNode> elseBodyCopy = new Stack<ASTNode>();
    for(ASTNode elseBodyElement: beta.getElseBody()){
      elseBodyCopy.add(elseBodyElement.accept(this));
    }
    copy.setElseBody(elseBodyCopy);
    
    return copy;
  }
  
  public Eta copy(Eta eta){
    Eta copy = new Eta();
    if(eta.getChild()!=null) {
      copy.setChild(eta.getChild().accept(this));
    }
    if(eta.getSibling()!=null) {
      copy.setSibling(eta.getSibling().accept(this));
    }
    copy.setType(eta.getType());
    copy.setValue(eta.getValue());
    copy.setSourceLineNumber(eta.getSourceLineNumber());
    
    copy.setDelta(eta.getDelta().accept(this));
    
    return copy;
  }
  
  public Delta copy(Delta delta){
    Delta copy = new Delta();
    if(delta.getChild()!=null) {
      copy.setChild(delta.getChild().accept(this));
    }
    if(delta.getSibling()!=null) {
      copy.setSibling(delta.getSibling().accept(this));
    }
    copy.setType(delta.getType());
    copy.setValue(delta.getValue());
    copy.setIndex(delta.getIndex());
    copy.setSourceLineNumber(delta.getSourceLineNumber());
    
    Stack<ASTNode> bodyCopy = new Stack<ASTNode>();
    for(ASTNode bodyElement: delta.getBody()){
      bodyCopy.add(bodyElement.accept(this));
    }
    copy.setBody(bodyCopy);
    
    List<String> boundVarsCopy = new ArrayList<String>();
    boundVarsCopy.addAll(delta.getBoundVars());
    copy.setBoundVars(boundVarsCopy);
    
    copy.setLinkedEnv(delta.getLinkedEnv());
    
    return copy;
  }
  
  public Tuple copy(Tuple tuple){
    Tuple copy = new Tuple();
    if(tuple.getChild()!=null) {
      copy.setChild(tuple.getChild().accept(this));
    }
    if(tuple.getSibling()!=null) {
      copy.setSibling(tuple.getSibling().accept(this));
    }
    copy.setType(tuple.getType());
    copy.setValue(tuple.getValue());
    copy.setSourceLineNumber(tuple.getSourceLineNumber());
    return copy;
  }
}

class Tuple extends ASTNode{
  
  public Tuple(){
    setType(ASTNodeType.TUPLE);
  }
  
  @Override
  public String getValue(){
    ASTNode childNode = getChild();
    if(childNode==null) {
      return "nil";
    }
    
    StringBuilder sb = new StringBuilder("(");
    while(childNode.getSibling()!=null){
      sb.append(childNode.getValue()).append(", ");
      childNode = childNode.getSibling();
    }
    sb.append(childNode.getValue()).append(")");
    return sb.toString();
  }
  
  public Tuple accept(NodeCopier nodeCopier){
    return nodeCopier.copy(this);
  }
  
}
