package cn.misection.cvac.parser;

import cn.misection.cvac.ast.clas.AbstractCvaClass;
import cn.misection.cvac.ast.clas.CvaClass;
import cn.misection.cvac.ast.decl.AbstractDeclaration;
import cn.misection.cvac.ast.decl.CvaDeclaration;
import cn.misection.cvac.ast.decl.nullptr.CvaNullDecl;
import cn.misection.cvac.ast.entry.AbstractEntryClass;
import cn.misection.cvac.ast.entry.CvaEntryClass;
import cn.misection.cvac.ast.expr.AbstractExpression;
import cn.misection.cvac.ast.expr.EnumCvaExpr;
import cn.misection.cvac.ast.expr.nonterminal.binary.*;
import cn.misection.cvac.ast.expr.nonterminal.unary.CvaCallExpr;
import cn.misection.cvac.ast.expr.nonterminal.unary.CvaIncDecExpr;
import cn.misection.cvac.ast.expr.nonterminal.unary.CvaNegateExpr;
import cn.misection.cvac.ast.expr.nonterminal.unary.CvaNewExpr;
import cn.misection.cvac.ast.expr.terminator.*;
import cn.misection.cvac.ast.method.AbstractMethod;
import cn.misection.cvac.ast.method.CvaMainMethod;
import cn.misection.cvac.ast.method.CvaMethod;
import cn.misection.cvac.ast.program.CvaProgram;
import cn.misection.cvac.ast.statement.*;
import cn.misection.cvac.ast.statement.nullptr.CvaNullStatement;
import cn.misection.cvac.ast.type.ICvaType;
import cn.misection.cvac.ast.type.advance.CvaArrayType;
import cn.misection.cvac.ast.type.advance.CvaStringType;
import cn.misection.cvac.ast.type.basic.EnumCvaType;
import cn.misection.cvac.ast.type.reference.CvaClassType;
import cn.misection.cvac.codegen.bst.instructor.EnumOperandType;
import cn.misection.cvac.codegen.bst.instructor.EnumOperator;
import cn.misection.cvac.constant.EnumIncDirection;
import cn.misection.cvac.constant.EnumLexerCommon;
import cn.misection.cvac.constant.WriteOptionCode;
import cn.misection.cvac.io.IBufferedQueue;
import cn.misection.cvac.lexer.CvaToken;
import cn.misection.cvac.lexer.EnumCvaToken;
import cn.misection.cvac.lexer.Lexer;

import java.util.*;

/**
 * @author MI6 root
 * @TODO 把所有带判断的eatToken改成advance;
 */
public final class Parser
{
    private final Lexer lexer;

    private CvaToken curToken;

    /**
     * for varDecl cn.misection.cvac.parser;
     */
    private boolean varDeclFlag;

    private boolean markingFlag;

    private boolean hasEntry;

    private final Queue<CvaToken> markedTokenQueue;


    public Parser(IBufferedQueue queueStream)
    {
        lexer = new Lexer(queueStream);
        curToken = lexer.nextToken();
        markingFlag = false;
        markedTokenQueue = new LinkedList<>();
    }

    public CvaProgram parse()
    {
        return parseProgram();
    }

    /**
     * utility methods;
     */
    private void advance()
    {
        if (markingFlag)
        {
            curToken = lexer.nextToken();
            markedTokenQueue.offer(curToken);
        }
        else if (!markedTokenQueue.isEmpty())
        {
            curToken = markedTokenQueue.poll();
        }
        else
        {
            curToken = lexer.nextToken();
        }
    }

    private char peekCh()
    {
        return lexer.peekCh();
    }

    /**
     * start recording the tokens;
     */
    private void mark()
    {
        markingFlag = true;
        markedTokenQueue.offer(curToken);
    }

    /**
     * stop recording the tokens and clear recorded
     */
    private void deMark()
    {
        markingFlag = false;
        markedTokenQueue.clear();
    }

    /**
     * reset current token and stop recording
     */
    private void reset()
    {
        markingFlag = false;
        advance();
    }

    private void eatToken(EnumCvaToken kind)
    {
        if (kind == curToken.toEnum())
        {
            advance();
        }
        else
        {
            errorLog(String.valueOf(kind),
                    curToken.toEnum());
        }
    }

    private void eatEof()
    {
        if (curToken.toEnum() != EnumCvaToken.EOF)
        {
            errorLog("end of file",
                    curToken);
        }
    }

    private void errorLog()
    {
        System.err.printf("Syntax error at line %s compilation aborting...\n%n",
                curToken != null ? curToken.getLineNum() : "unknown");
        System.exit(1);
    }

    private void errorLog(String expected, CvaToken got)
    {
        System.err.printf("Line %d: Expects: %s, but got: %s which literal is %s%n",
                curToken.getLineNum(), expected, got.toEnum(), got.getLiteral());
        System.exit(1);
    }

    private void errorLog(String expected, EnumCvaToken got)
    {
        System.err.printf("Line %d: Expects: %s, but got: %s%n",
                curToken.getLineNum(), expected, got);
        System.exit(1);
    }


    private void errorLog(String expected, String got)
    {
        System.err.printf("Line %d: Expects: %s, but got: %s%n",
                curToken.getLineNum(), expected, got);
        System.exit(1);
    }

    /**
     * parse methods
     * ExprList -> Expr ExprRest*
     * ->
     * ExprRest -> , Expr
     *
     * @return Exprlist;
     */
    private List<AbstractExpression> parseExprList()
    {
        List<AbstractExpression> expList = new ArrayList<>();
        if (curToken.toEnum() == EnumCvaToken.CLOSE_PAREN)
        {
            return expList;
        }
        AbstractExpression tem = parseLinkedExpr();
        tem.setLineNum(curToken.getLineNum());
        expList.add(tem);
        while (curToken.toEnum() == EnumCvaToken.COMMA)
        {
            advance();
            tem = parseLinkedExpr();
            tem.setLineNum(curToken.getLineNum());
            expList.add(tem);
        }
        return expList;
    }

    /**
     * 原子操作层解析;
     * AtomExpr -> (exp)
     * -> Integer Literal
     * -> true
     * -> false
     * -> this
     * -> id
     * -> new id()
     *
     * @return atom expr;
     */
    private AbstractExpression parseAtomExpr()
    {
        switch (curToken.toEnum())
        {
            case OPEN_PAREN:
            {
                advance();
                AbstractExpression expr = parseLinkedExpr();
                expr.setLineNum(curToken.getLineNum());
                //advance();
                eatToken(EnumCvaToken.CLOSE_PAREN);
                return expr;
            }
            case CONST_INT:
            {
                AbstractExpression expr = new CvaConstIntExpr(curToken.getLineNum(), Integer.parseInt(curToken.getLiteral()));
                advance();
                return expr;
            }
            case STRING:
            {
                AbstractExpression expr = new CvaConstStringExpr(curToken.getLineNum(), curToken.getLiteral());
                advance();
                return expr;
            }
            case TRUE:
            {
                AbstractExpression expr = new CvaConstTrueExpr(curToken.getLineNum());
                advance();
                return expr;
            }
            case FALSE:
            {
                AbstractExpression expr = new CvaConstFalseExpr(curToken.getLineNum());
                advance();
                return expr;
            }
            case THIS:
            {
                AbstractExpression expr = new CvaThisExpr(curToken.getLineNum());
                advance();
                return expr;
            }
            case IDENTIFIER:
            {
                AbstractExpression expr = new CvaIdentifierExpr(
                        curToken.getLineNum(), curToken.getLiteral());
                advance();
                switch (curToken.toEnum())
                {
                    case INCREMENT:
                    case DECREMENT:
                    {
                        return handleIncDecExpr(expr);
                    }
                    default:
                    {
                        return expr;
                    }
                }
            }
            case INCREMENT:
            {
                advance();
                if (curToken.toEnum() == EnumCvaToken.IDENTIFIER)
                {
                    CvaIdentifierExpr expr = new CvaIdentifierExpr(
                            curToken.getLineNum(), curToken.getLiteral());
                    advance();
                    return new CvaIncDecExpr(
                            curToken.getLineNum(), expr, EnumIncDirection.INCREMENT);
                }
                else
                {
                    errorLog("identifier after ++", curToken);
                }
            }
            case DECREMENT:
            {
                advance();
                if (curToken.toEnum() == EnumCvaToken.IDENTIFIER)
                {
                    CvaIdentifierExpr expr = new CvaIdentifierExpr(
                            curToken.getLineNum(), curToken.getLiteral());
                    advance();
                    return new CvaIncDecExpr(
                            curToken.getLineNum(), expr, EnumIncDirection.DECREMENT);
                }
                else
                {
                    errorLog("identifier after --", curToken);
                }
            }
            case NEW:
            {
                advance();
                AbstractExpression expr = new CvaNewExpr(curToken.getLineNum(), curToken.getLiteral());
                advance();
                eatToken(EnumCvaToken.OPEN_PAREN);
                eatToken(EnumCvaToken.CLOSE_PAREN);
                return expr;
            }
            default:
            {
                errorLog();
                return null;
            }
        }
    }

    /*
     * TODO;
     */
//    private AbstractExpression parseBitNegateExpr()
//    {
//        // FIXME, 这里不要先给终结符!!有问题的是;
//        AbstractExpression expr = parseAtomExpr();
//        while (curToken.toEnum() == EnumCvaToken.DOT)
//        {
//            advance();
//            CvaToken token = curToken;
//            eatToken(EnumCvaToken.IDENTIFIER);
//            eatToken(EnumCvaToken.OPEN_PAREN);
//            expr = new CvaCallExpr(
//                    token.getLineNum(),
//                    token.getLiteral(),
//                    expr,
//                    parseExprList()
//            );
//            eatToken(EnumCvaToken.CLOSE_PAREN);
//        }
//        return expr;
//    }


    /**
     * NegateExpr -> AtomExpr
     * -> AtomExpr.id(exprList)
     *
     * @return negateExpr
     */
    private AbstractExpression parseCallExpr()
    {
        // FIXME, 这里不要先给终结符!!有问题的是;
        AbstractExpression expr = parseAtomExpr();
        while (curToken.toEnum() == EnumCvaToken.DOT)
        {
            advance();
            CvaToken token = curToken;
            eatToken(EnumCvaToken.IDENTIFIER);
            eatToken(EnumCvaToken.OPEN_PAREN);
            expr = new CvaCallExpr(
                    token.getLineNum(),
                    token.getLiteral(),
                    expr,
                    parseExprList()
            );
            eatToken(EnumCvaToken.CLOSE_PAREN);
        }
        return expr;
    }

    private AbstractExpression parseNegateExpr()
    {
        int i = 0;
        while (curToken.toEnum() == EnumCvaToken.NEGATE)
        {
            advance();
            i++;
        }
        AbstractExpression expr = parseCallExpr();
        AbstractExpression tem = new CvaNegateExpr(
                expr.getLineNum(), expr);
        return i % 2 == 0 ? expr : tem;
    }

    /**
     * @return p;
     * @TODO 给几个枚举做反查map, 可以直接查;
     */
    private AbstractExpression parseUnsignedRightShiftExpr()
    {
        AbstractExpression tem = parseNegateExpr();
        AbstractExpression expr = tem;
        while (curToken.toEnum() == EnumCvaToken.UNSIGNED_RIGHT_SHIFT)
        {
            advance();
            tem = parseNegateExpr();
            expr = new CvaOperandOperatorExpr.Builder()
                    .putLineNum(tem.getLineNum())
                    .putEnumExpr(EnumCvaExpr.UNSIGNED_RIGHT_SHIFT)
                    .putLeft(expr)
                    .putRight(tem)
                    .putInstType(EnumOperandType.INT)
                    .putInstOp(EnumOperator.UNSIGNED_RIGHT_SHIFT)
                    .build();
        }
        return expr;
    }

    private AbstractExpression parseRightShiftExpr()
    {
        AbstractExpression tem = parseUnsignedRightShiftExpr();
        AbstractExpression expr = tem;
        while (curToken.toEnum() == EnumCvaToken.RIGHT_SHIFT)
        {
            advance();
            tem = parseUnsignedRightShiftExpr();
            expr = new CvaOperandOperatorExpr.Builder()
                    .putLineNum(tem.getLineNum())
                    .putEnumExpr(EnumCvaExpr.RIGHT_SHIFT)
                    .putLeft(expr)
                    .putRight(tem)
                    .putInstType(EnumOperandType.INT)
                    .putInstOp(EnumOperator.RIGHT_SHIFT)
                    .build();
        }
        return expr;
    }

    private AbstractExpression parseLeftShiftExpr()
    {
        AbstractExpression tem = parseRightShiftExpr();
        AbstractExpression expr = tem;
        while (curToken.toEnum() == EnumCvaToken.LEFT_SHIFT)
        {
            advance();
            tem = parseRightShiftExpr();
            expr = new CvaOperandOperatorExpr.Builder()
                    .putLineNum(tem.getLineNum())
                    .putEnumExpr(EnumCvaExpr.LEFT_SHIFT)
                    .putLeft(expr)
                    .putRight(tem)
                    .putInstType(EnumOperandType.INT)
                    .putInstOp(EnumOperator.LEFT_SHIFT)
                    .build();
        }
        return expr;
    }

    private AbstractExpression parseBitXOrExpr()
    {
        AbstractExpression tem = parseLeftShiftExpr();
        AbstractExpression expr = tem;
        while (curToken.toEnum() == EnumCvaToken.BIT_XOR)
        {
            advance();
            tem = parseLeftShiftExpr();
            expr = new CvaOperandOperatorExpr.Builder()
                    .putLineNum(tem.getLineNum())
                    .putEnumExpr(EnumCvaExpr.BIT_XOR)
                    .putLeft(expr)
                    .putRight(tem)
                    .putInstType(EnumOperandType.INT)
                    .putInstOp(EnumOperator.BIT_XOR)
                    .build();
        }
        return expr;
    }

    private AbstractExpression parseBitOrExpr()
    {
        AbstractExpression tem = parseBitXOrExpr();
        AbstractExpression expr = tem;
        while (curToken.toEnum() == EnumCvaToken.BIT_OR)
        {
            advance();
            tem = parseBitXOrExpr();
            expr = new CvaOperandOperatorExpr.Builder()
                    .putLineNum(tem.getLineNum())
                    .putEnumExpr(EnumCvaExpr.BIT_OR)
                    .putLeft(expr)
                    .putRight(tem)
                    .putInstType(EnumOperandType.INT)
                    .putInstOp(EnumOperator.BIT_OR)
                    .build();
        }
        return expr;
    }


    private AbstractExpression parseBitAndExpr()
    {
        AbstractExpression tem = parseBitOrExpr();
        AbstractExpression expr = tem;
        while (curToken.toEnum() == EnumCvaToken.BIT_AND)
        {
            advance();
            tem = parseBitOrExpr();
            expr = new CvaOperandOperatorExpr.Builder()
                    .putLineNum(tem.getLineNum())
                    .putEnumExpr(EnumCvaExpr.BIT_AND)
                    .putLeft(expr)
                    .putRight(tem)
                    .putInstType(EnumOperandType.INT)
                    .putInstOp(EnumOperator.BIT_AND)
                    .build();
        }
        return expr;
    }


    private AbstractExpression parseRemExpr()
    {
        AbstractExpression tem = parseBitAndExpr();
        AbstractExpression expr = tem;
        while (curToken.toEnum() == EnumCvaToken.REM)
        {
            advance();
            tem = parseBitAndExpr();
            expr = new CvaOperandOperatorExpr.Builder()
                    .putLineNum(tem.getLineNum())
                    .putEnumExpr(EnumCvaExpr.REM)
                    .putLeft(expr)
                    .putRight(tem)
                    // FIXME, 后面改成从表达式获取;
                    .putInstType(EnumOperandType.INT)
                    .putInstOp(EnumOperator.REM)
                    .build();
        }
        return expr;
    }

    /**
     * MulExpr -> ! MulExpr
     * -> NegateExpr
     *
     * @return MulExpr
     */
    private AbstractExpression parseDivExpr()
    {
        AbstractExpression tem = parseRemExpr();
        AbstractExpression expr = tem;
        while (curToken.toEnum() == EnumCvaToken.DIV)
        {
            advance();
            tem = parseRemExpr();
            expr = new CvaOperandOperatorExpr.Builder()
                    .putLineNum(tem.getLineNum())
                    .putEnumExpr(EnumCvaExpr.DIV)
                    .putLeft(expr)
                    .putRight(tem)
                    .putInstType(EnumOperandType.INT)
                    .putInstOp(EnumOperator.DIV)
                    .build();
        }
        return expr;
    }

    /**
     * AddSubExpr -> MulExpr * MulExpr
     * -> MulExpr
     *
     * @return AddSubExpr
     */
    private AbstractExpression parseMulExpr()
    {
        AbstractExpression tem = parseDivExpr();
        AbstractExpression expr = tem;
        while (curToken.toEnum() == EnumCvaToken.STAR)
        {
            advance();
            tem = parseDivExpr();
            expr = new CvaMulExpr(tem.getLineNum(), expr, tem);
        }
        return expr;
    }

    /**
     * LessThanExpr -> AddSubExpr + AddSubExpr
     * -> AddSubExpr - AddSubExpr
     * -> AddSubExpr
     *
     * @return LessThanExpr
     */
    private AbstractExpression parseAddSubExpr()
    {
        AbstractExpression expr = parseMulExpr();
        while (curToken.toEnum() == EnumCvaToken.ADD
                || curToken.toEnum() == EnumCvaToken.SUB)
        {
            boolean addFlag = curToken.toEnum() == EnumCvaToken.ADD;
            advance();
            AbstractExpression tem = parseMulExpr();
            if (addFlag)
            {
                expr = new CvaAddExpr(expr.getLineNum(), expr, tem);
            }
            // 减法;
            else
            {
                // 加的是常数就直接反过来;
                if (tem instanceof CvaConstIntExpr)
                {
                    expr = new CvaAddExpr(
                            tem.getLineNum(),
                            expr,
                            new CvaConstIntExpr(tem.getLineNum(),
                                    -((CvaConstIntExpr) tem).getValue()));
                }
                else
                {
                    // 否则用这个, 不是很统一;
                    expr = new CvaSubExpr(expr.getLineNum(), expr, tem);
                }
            }
        }
        return expr;
    }

    /**
     * AndAndExpr -> LessThanExpr < LessThanExp
     * -> LtExp
     *
     * @return AndAndExpr;
     */
    private AbstractExpression parseLessOrMoreThanExpr()
    {
        AbstractExpression expr = parseAddSubExpr();
        while (true)
        {
            switch (curToken.toEnum())
            {
                case LESS_THAN:
                {
                    advance();
                    AbstractExpression tem = parseAddSubExpr();
                    expr = new CvaLessOrMoreThanExpr(expr.getLineNum(), expr, tem);
                    continue;
                }
                case MORE_THAN:
                {
                    // more than 倒一下即可;
                    advance();
                    AbstractExpression tem = parseAddSubExpr();
                    expr = new CvaLessOrMoreThanExpr(expr.getLineNum(), tem, expr);
                    continue;
                }
                case MORE_OR_EQUALS:
                {
                    // >=;
                    break;
                }
                case LESS_OR_EQUALS:
                {
                    // <=
                    break;
                }
                default:
                {
                    break;
                }
            }
            break;
        }
        return expr;
    }

    private AbstractExpression parseAndAndExpr()
    {
        AbstractExpression expr = parseLessOrMoreThanExpr();
        while (curToken.toEnum() == EnumCvaToken.AND_AND)
        {
            advance();
            AbstractExpression tem = parseLessOrMoreThanExpr();
            expr = new CvaAndAndExpr(expr.getLineNum(), expr, tem);
        }
        return expr;
    }

    /**
     * Expr -> AndExpr && AndExpr
     * -> AndExpr
     *
     * @return Single Expr
     */
    private AbstractExpression parseLinkedExpr()
    {
        // start;
        return parseAndAndExpr();
    }

    /**
     * Statement -> { Statement* }
     * -> if (Expr) Statement else Statement
     * -> while (Expr) Statement
     * -> for )
     * -> write(Expr);
     * -> id = Expr;
     *
     * @return single Statement;
     */
    private AbstractStatement parseStatement()
    {
        switch (curToken.toEnum())
        {
            case OPEN_CURLY_BRACE:
            {
                return handleOpenCurly();
            }
            case IF_STATEMENT:
            {
                return handleIf();
            }
            case WHILE_STATEMENT:
            {
                return handleWhile();
            }
            case FOR_STATEMENT:
            {
                return handleFor();
            }
            case WRITE:
            {
                return handleWriteOp(WriteOptionCode.CONSOLE_WRITE);
            }
            case WRITE_LINE:
            {
                return handleWriteOp(WriteOptionCode.CONSOLE_WRITELN);
            }
            case WRITE_FORMAT:
            {
                return handleWriteOp(WriteOptionCode.CONSOLE_WRITE_FORMAT);
            }
            case IDENTIFIER:
            {
                return handleIdentifier();
            }
            case INCREMENT:
            case DECREMENT:
            case NEW:
            {
                // 有副作用, 小心顺序;
                AbstractStatement statement = new CvaExprStatement(
                        curToken.getLineNum(), parseLinkedExpr());
                eatToken(EnumCvaToken.SEMI);
                return statement;
            }
            case SEMI:
            {
                // 有时候要注释掉这个来看会不会漏检查分号;
                eatToken(EnumCvaToken.SEMI);
                return CvaNullStatement.getInstance();
            }
            default:
            {
                errorLog();
                // 不可达, 做成抛错;
                return null;
            }
        }
    }

    /**
     * // StatementList -> Statement Statements
     * //  ->
     *
     * @return StatementList;
     */
    private List<AbstractStatement> parseStatementList()
    {
        List<AbstractStatement> statementList = new ArrayList<>();
        while (true)
        {
            switch (curToken.toEnum())
            {
                case OPEN_CURLY_BRACE:
                case IF_STATEMENT:
                case WHILE_STATEMENT:
                case IDENTIFIER:
                case WRITE:
                    // TODO 不优雅, 想办法改;
                case WRITE_LINE:
                case WRITE_FORMAT:
                {
                    statementList.add(parseStatement());
                    continue;
                }
                default:
                {
                    break;
                }
            }
            // 走到这里就会break掉;
            break;
        }
        return statementList;
    }

    /**
     * // Type -> int
     * //  -> boolean
     * //  -> id
     *
     * @return Type;
     */
    private ICvaType parseType()
    {
        ICvaType type = null;
        // 放map只能反射, 不放了还是;
        switch (curToken.toEnum())
        {
            case VOID:
            {
                type = EnumCvaType.VOID;
                break;
            }
            case BYTE:
            {
                type = EnumCvaType.BYTE;
                break;
            }
            case CHAR:
            {
                type = EnumCvaType.CHAR;
                break;
            }
            case SHORT:
            {
                type = EnumCvaType.SHORT;
                break;
            }
            case INT:
            {
                type = EnumCvaType.INT;
                break;
            }
            case LONG:
            {
                type = EnumCvaType.LONG;
                break;
            }
            case FLOAT:
            {
                type = EnumCvaType.FLOAT;
                break;
            }
            case DOUBLE:
            {
                type = EnumCvaType.DOUBLE;
                break;
            }
            case BOOLEAN:
            {
                type = EnumCvaType.BOOLEAN;
                break;
            }
            case STRING:
            {
                type = new CvaStringType();
                break;
            }
            case IDENTIFIER:
            {
                // 应该是type;
                type = new CvaClassType(curToken.getLiteral());
                break;
            }
            default:
            {
                errorLog("type",
                        curToken);
                // 不需要break打断虚拟机了已经;
            }
        }
        // 因为有advance所以不能直接return;
        advance();
        if (curToken.toEnum() == EnumCvaToken.OPEN_BRACKETS)
        {
            advance();
            switch (curToken.toEnum())
            {
                case CLOSE_BRACKETS:
                {
                    advance();
                    return new CvaArrayType(type);
                }
                case CONST_INT:
                {
                    int size = Integer.parseInt(curToken.getLiteral());
                    advance();
                    eatToken(EnumCvaToken.CLOSE_BRACKETS);
                    return new CvaArrayType(type, size);
                }
                default:
                {
                    errorLog("] or int array size whith ]", curToken);
                    break;
                }
            }
        }
        return type;
    }

    /**
     * // VarDecl -> Type id;
     *
     * @return VarDecl;
     */
    private AbstractDeclaration parseVarDecl()
    {
        mark();
        ICvaType type = parseType();
        // maybe a assign statement in method;
        switch (curToken.toEnum())
        {
            case ASSIGN:
            {
                this.reset();
                varDeclFlag = false;
                return null;
            }
            case IDENTIFIER:
            {
                String literal = curToken.getLiteral();
                advance();
                switch (curToken.toEnum())
                {
                    case SEMI:
                    {
                        this.deMark();
                        varDeclFlag = true;
                        AbstractDeclaration decl = new CvaDeclaration(
                                curToken.getLineNum(), literal, type);
                        eatToken(EnumCvaToken.SEMI);
                        return decl;
                    }
                    // maybe a method in class;
                    case OPEN_PAREN:
                    {
                        varDeclFlag = false;
                        this.reset();
                        return CvaNullDecl.getInstance();
                    }
                    default:
                    {
                        errorLog();
                        return null;
                    }
                }
            }
            default:
            {
                errorLog();
                return null;
            }
        }
    }

    /**
     * // VarDecls -> VarDecl VarDecls
     * //  ->
     *
     * @return VarDeclList;
     */
    private List<AbstractDeclaration> parseVarDeclList()
    {
        List<AbstractDeclaration> declList = new ArrayList<>();
        varDeclFlag = true;
        while (EnumCvaToken.isType(curToken.toEnum())
                || curToken.toEnum() == EnumCvaToken.IDENTIFIER)
        {
            AbstractDeclaration decl = parseVarDecl();
            declList.add(decl);
            if (!varDeclFlag)
            {
                break;
            }
        }
        return declList;
    }

    /**
     * FormalList -> Type id FormalRest*
     * ->
     * FormalRest -> , Type id
     *
     * @return FormalList;
     */
    private List<AbstractDeclaration> parseFormalList()
    {
        List<AbstractDeclaration> declList = new ArrayList<>();
        if (EnumCvaToken.isType(curToken.toEnum()))
        {
            // 这里非常坑. 必须要先parser;
            // parse的副作用是推一个token, 所以给new decl传参的时候先后顺序换了会导致意想不到的bug;
            // 保存上一个token的type, 拿取下一个token的literal;
            ICvaType type = parseType();
            declList.add(new CvaDeclaration(curToken.getLineNum(), curToken.getLiteral(), type));
            eatToken(EnumCvaToken.IDENTIFIER);
            while (curToken.toEnum() == EnumCvaToken.COMMA)
            {
                advance();
                ICvaType argType = parseType();
                declList.add(new CvaDeclaration(curToken.getLineNum(), curToken.getLiteral(), argType));
                eatToken(EnumCvaToken.IDENTIFIER);
            }
        }
        return declList;
    }

    /**
     * Method -> Type id (FormalList)
     * {VarDec* Statement* return Exp; }
     *
     * @return Method;
     */
    private AbstractMethod parseMethod()
    {
        // 第一个是返回值;
        ICvaType retType = parseType();
        // 解析函数名;
        String methodName = curToken.getLiteral();
        // 吃掉函数名和开小括号;
        eatToken(EnumCvaToken.IDENTIFIER);
        eatToken(EnumCvaToken.OPEN_PAREN);
        // 解析形参List;
        List<AbstractDeclaration> formalList = parseFormalList();
        // 解析完毕吃掉小括号;
        eatToken(EnumCvaToken.CLOSE_PAREN);
        // 吃掉大括号;
        eatToken(EnumCvaToken.OPEN_CURLY_BRACE);
        List<AbstractDeclaration> localVarDecls = new ArrayList<>();
        List<AbstractStatement> statementList = new ArrayList<>();

        while (true)
        {
            EnumCvaToken memTokenEnum = curToken.toEnum();
            switch (memTokenEnum)
            {
                case IDENTIFIER:
                {
                    char pCh = peekCh();
                    if (Character.isAlphabetic(pCh) || pCh == '_' || pCh == '$')
                    {
                        // 2连 identifier, 说明是定义;
                        ICvaType declType = parseType();
                        switch (peekCh())
                        {
                            case ';':
                            {
                                String idLiteral = curToken.getLiteral();
                                localVarDecls.add(handleMethodVarDecl(idLiteral, declType));
                                eatToken(EnumCvaToken.SEMI);
                                continue;
                            }
                            case '=':
                            {
                                String idLiteral = curToken.getLiteral();
                                localVarDecls.add(handleMethodVarDecl(idLiteral, declType));
                                eatToken(EnumCvaToken.ASSIGN);
                                statementList.add(handleMethodAssign(idLiteral));
                                eatToken(EnumCvaToken.SEMI);
                                continue;
                            }
                            default:
                            {
                                errorLog("semi or assign", lexer.nextToken());
                                break;
                            }
                        }
                    }
                    else
                    {
                        // 说明是普通statement;
                        statementList.add(parseStatement());
                        continue;
                    }
                    break;
                }
                case RETURN:
                {
                    break;
                }
                default:
                {
                    if (EnumCvaToken.isType(memTokenEnum))
                    {
                        // 吃掉type, cur是id, 下一个看是分号还是assign;
                        ICvaType declType = parseType();
                        switch (peekCh())
                        {
                            case ';':
                            {
                                String idLiteral = curToken.getLiteral();
                                localVarDecls.add(handleMethodVarDecl(idLiteral, declType));
                                eatToken(EnumCvaToken.SEMI);
                                continue;
                            }
                            case '=':
                            {
                                String idLiteral = curToken.getLiteral();
                                localVarDecls.add(handleMethodVarDecl(idLiteral, declType));
                                eatToken(EnumCvaToken.ASSIGN);
                                statementList.add(handleMethodAssign(idLiteral));
                                eatToken(EnumCvaToken.SEMI);
                                continue;
                            }
                            default:
                            {
                                errorLog("semi or assign", lexer.nextToken());
                                break;
                            }
                        }
                        break;
                    }
                    else
                    {
                        statementList.add(parseStatement());
                        continue;
                    }
                }
            }
            break;
        }
        // FIXME 隐患;
        AbstractExpression retExpr;
        if (retType.toEnum() == EnumCvaType.VOID)
        {
            retExpr = new CvaConstNullExpr(curToken.getLineNum());
            if (curToken.toEnum() == EnumCvaToken.RETURN)
            {
                eatToken(EnumCvaToken.RETURN);
                eatToken(EnumCvaToken.SEMI);
            }
        }
        else
        {
            eatToken(EnumCvaToken.RETURN);
            retExpr = parseLinkedExpr();
            eatToken(EnumCvaToken.SEMI);
        }
        eatToken(EnumCvaToken.CLOSE_CURLY_BRACE);

        return new CvaMethod.Builder()
                .putName(methodName)
                .putRetType(retType)
                .putRetExpr(retExpr)
                .putArgList(formalList)
                .putLocalVarList(localVarDecls)
                .putStatementList(statementList)
                .build();
    }

    private AbstractDeclaration handleMethodVarDecl(
            String literal, ICvaType declType)
    {
        eatToken(EnumCvaToken.IDENTIFIER);
        return new CvaDeclaration(
                curToken.getLineNum(), literal, declType);
    }

    private AbstractStatement handleMethodAssign(
            String idLiteral)
    {
        AbstractExpression expr = parseLinkedExpr();
        return new CvaAssignStatement(
                curToken.getLineNum(), idLiteral, expr);
    }

    /**
     * MethodDecls -> MethodDecl MethodDecls*
     * ->
     *
     * @return MethodDeclList;
     */
    private List<AbstractMethod> parseMethodDeclList()
    {
        List<AbstractMethod> methodList = new ArrayList<>();

        while (EnumCvaToken.isType(curToken.toEnum())
                || curToken.toEnum() == EnumCvaToken.IDENTIFIER)
        {
            methodList.add(parseMethod());
        }
        return methodList;
    }

    /**
     * ClassDecl -> class id { VarDecl* MethodDecl* }
     * -> class id : id { VarDecl* Method* }
     *
     * @return single ClassDecl;
     */
    private AbstractCvaClass parseClassDecl()
    {
        eatToken(EnumCvaToken.CLASS_DECL);
        String literal = curToken.getLiteral();
        eatToken(EnumCvaToken.IDENTIFIER);
        String superClass = null;
        if (curToken.toEnum() == EnumCvaToken.EXTENDS)
        {
            advance();
            superClass = curToken.getLiteral();
            eatToken(EnumCvaToken.IDENTIFIER);
        }
        eatToken(EnumCvaToken.OPEN_CURLY_BRACE);
        List<AbstractDeclaration> declList = parseVarDeclList();
        List<AbstractMethod> methodList = parseMethodDeclList();
        eatToken(EnumCvaToken.CLOSE_CURLY_BRACE);
        return new CvaClass(
                literal,
                superClass,
                declList,
                methodList);
    }

    /**
     * // ClassDecls -> ClassDecl ClassDecls*
     * //  ->
     *
     * @return ClassDeclList;
     */
    private List<AbstractCvaClass> parseClassDeclList()
    {
        List<AbstractCvaClass> classList = new ArrayList<>();
        while (curToken.toEnum() == EnumCvaToken.CLASS_DECL)
        {
            classList.add(parseClassDecl());
        }
        return classList;
    }

    /**
     * MainClass -> class id
     * {
     * type main()
     * {
     * StatementList;
     * }
     * }
     *
     * @return EntryClass;
     */
    private CvaEntryClass parseEntryClass()
    {
        String mainClassName;
        AbstractMethod mainMethod;
        if (curToken.toEnum() == EnumCvaToken.CLASS_DECL)
        {
            eatToken(EnumCvaToken.CLASS_DECL);
            mainClassName = curToken.getLiteral();
            eatToken(EnumCvaToken.IDENTIFIER);
            eatToken(EnumCvaToken.OPEN_CURLY_BRACE);
//            AbstractStatement statement = parseMainMethod();
            mainMethod = new CvaMainMethod.Builder(
                    parseMethod())
                    .build();

            eatToken(EnumCvaToken.CLOSE_CURLY_BRACE);
        }
        else
        {
            mainClassName = EnumLexerCommon.MAIN_CLASS_NAME.string();
            mainMethod = new CvaMainMethod.Builder(
                    parseMethod())
                    .build();
        }

        List<AbstractDeclaration> argList = mainMethod.getArgumentList();
        if (argList.size() == 1)
        {
            ICvaType argType = argList.get(0).type();
            // && 是短路的;
            if (argType.toEnum() != EnumCvaType.ARRAY ||
                    ((CvaArrayType) argType).getInnerType().toEnum() != EnumCvaType.STRING)
            {
                errorLog("only accexpt main func's arg string[]",
                        String.valueOf(argList));
            }
        }
        else
        {
            errorLog("main func's arg decl length should be 1 means string[]",
                    String.valueOf(argList));
        }

        return new CvaEntryClass.Builder()
                .putName(mainClassName)
                .putMainMethod(mainMethod)
                .build();
    }

    /**
     * Program -> MainClass ClassDecl*
     *
     * @return Program tree;
     */
    private CvaProgram parseProgram()
    {
        parsePackage();
        parseCallStatement();
        // 直接解析;
        AbstractEntryClass entryClass = null;
        List<AbstractCvaClass> classList = new ArrayList<>();
        while (true)
        {
            EnumCvaToken curTokenEnum = curToken.toEnum();
            switch (curTokenEnum)
            {
                case CLASS_DECL:
                {
                    classList.addAll(parseClassDeclList());
                    continue;
                }
                case EOF:
                {
                    break;
                }
                default:
                {
                    if (EnumCvaToken.isType(curTokenEnum))
                    {
                        entryClass = parseEntryClass();
                        hasEntry = true;
                        continue;
                    }
                    else
                    {
                        errorLog("EOF or class def or main func def" +
                                        "(cva only supported main func out the class) ",
                                curTokenEnum);
                    }
                    // 不可达;
                    break;
                }
            }
            break;
        }
        if (!hasEntry)
        {
            entryClass = (AbstractEntryClass) searchMain(classList);
        }
        eatEof();
        // find entry;
        return new CvaProgram(entryClass, classList);
    }

    private AbstractCvaClass searchMain(List<AbstractCvaClass> classList)
    {
        for (AbstractCvaClass absClass : classList)
        {
            for (AbstractMethod absMethod : absClass.getMethodList())
            {
                // null 安全的equals;
                if (Objects.equals(absMethod.name(),
                        EnumLexerCommon.MAIN_METHOD_NAME.string()))
                {
                    // return 打断多重循环, 如果重复定义main, 只执行第一个;
                    // 没有测试过行不行;
                    classList.remove(absClass);
                    return absClass;
                }
            }
        }
        errorLog("a main method",
                "null, deny to compile this file!");
        return null;
    }

    private void parsePackage()
    {
        if (curToken.toEnum() == EnumCvaToken.PACKAGE_DECL)
        {
            eatToken(EnumCvaToken.PACKAGE_DECL);
            EnumCvaToken memKind = curToken.toEnum();
            eatToken(EnumCvaToken.IDENTIFIER);
            while (true)
            {
                switch (curToken.toEnum())
                {
                    case DOT:
                    {
                        if (memKind != EnumCvaToken.IDENTIFIER)
                        {
                            errorLog();
                        }
                        memKind = EnumCvaToken.DOT;
                        eatToken(EnumCvaToken.DOT);
                        continue;
                    }
                    case IDENTIFIER:
                    {
                        if (memKind != EnumCvaToken.DOT)
                        {
                            errorLog();
                        }
                        memKind = EnumCvaToken.IDENTIFIER;
                        eatToken(EnumCvaToken.IDENTIFIER);
                        continue;
                    }
                    case SEMI:
                    {
                        eatToken(EnumCvaToken.SEMI);
                        break;
                    }
                    default:
                    {
                        errorLog("pkg name or dot or star",
                                curToken);
                        break;
                    }
                }
                break;
            }
        }
    }

    private void parseCallStatement()
    {
        // call 是多条, 所以在这里用;
        while (curToken.toEnum() == EnumCvaToken.CALL)
        {
            parseCallSentence();
        }
    }

    private void parseCallSentence()
    {
        eatToken(EnumCvaToken.CALL);
        // 规定至少一个pkg., 因为本包内不需要call;
        // 第一个必为 id;
        EnumCvaToken memKind = curToken.toEnum();
        eatToken(EnumCvaToken.IDENTIFIER);
        while (true)
        {
            switch (curToken.toEnum())
            {
                case DOT:
                {
                    if (memKind != EnumCvaToken.IDENTIFIER)
                    {
                        errorLog();
                    }
                    memKind = EnumCvaToken.DOT;
                    advance();
                    continue;
                }
                case IDENTIFIER:
                {
                    if (memKind != EnumCvaToken.DOT)
                    {
                        errorLog();
                    }
                    memKind = EnumCvaToken.IDENTIFIER;
                    advance();
                    continue;
                }
                case STAR:
                {
                    if (memKind != EnumCvaToken.DOT)
                    {
                        errorLog();
                    }
                    advance();
                    eatToken(EnumCvaToken.SEMI);
                    break;
                }
                case SEMI:
                {
                    eatToken(EnumCvaToken.SEMI);
                    break;
                }
                default:
                {
                    errorLog("pkg name or dot or star",
                            curToken);
                    break;
                }
            }
            break;
        }
    }

    private AbstractStatement handleWriteOp(byte writeMode)
    {
        // 目前 echo expr 实现还稍麻烦, 后面再想法;
        int lineNum = curToken.getLineNum();
        // 一定是write;
        advance();
        // TODO 解析不带括号的echo;
        EnumCvaToken curTokenEnum = curToken.toEnum();
        if (curTokenEnum == EnumCvaToken.OPEN_PAREN)
        {
            // (;
            advance();
            AbstractExpression expr = parseLinkedExpr();
            eatToken(EnumCvaToken.CLOSE_PAREN);
            eatToken(EnumCvaToken.SEMI);
            return new CvaWriteStatement(lineNum, expr, writeMode);
        }
        else
        {
            AbstractExpression expr = parseLinkedExpr();
            eatToken(EnumCvaToken.SEMI);
            return new CvaWriteStatement(lineNum, expr, writeMode);
        }
    }

    /**
     * @return 处理if;
     * @TODO 嵌套判定有无else之类;
     */
    private AbstractStatement handleIf()
    {
        int lineNum = curToken.getLineNum();
        advance();
        eatToken(EnumCvaToken.OPEN_PAREN);
        AbstractExpression condition = parseLinkedExpr();
        eatToken(EnumCvaToken.CLOSE_PAREN);
        AbstractStatement thenStm = parseStatement();
        AbstractStatement elseStm = handleElse();
        return new CvaIfStatement(
                lineNum,
                condition,
                thenStm,
                elseStm);
    }

    private AbstractStatement handleElse()
    {
        if (curToken.toEnum() == EnumCvaToken.ELSE_STATEMENT)
        {
            advance();
            return parseStatement();
        }
        return CvaNullStatement.getInstance();
    }

    private AbstractStatement handleWhile()
    {
        int lineNum = curToken.getLineNum();
        advance();
        eatToken(EnumCvaToken.OPEN_PAREN);
        AbstractExpression condition = parseLinkedExpr();
        eatToken(EnumCvaToken.CLOSE_PAREN);
        AbstractStatement body = parseStatement();
        return new CvaWhileForStatement(lineNum, condition, body);
    }

    /**
     * @return statement;
     * @TODO for 中实现本地变量;
     */
    private AbstractStatement handleFor()
    {
        int lineNum = curToken.getLineNum();
        advance();
        eatToken(EnumCvaToken.OPEN_PAREN);
        AbstractStatement forInit = parseStatement();
        AbstractExpression condition = parseLinkedExpr();
        eatToken(EnumCvaToken.SEMI);
        AbstractExpression afterBody = parseLinkedExpr();
        eatToken(EnumCvaToken.CLOSE_PAREN);
        AbstractStatement body = parseStatement();
        return new CvaWhileForStatement.Builder()
                .putLineNum(lineNum)
                .putForInit(forInit)
                .putCondition(condition)
                .putAfterBody(afterBody)
                .putBody(body)
                .build();
    }

    private AbstractStatement handleIdentifier()
    {
        String idLiteral = curToken.getLiteral();
        int lineNum = curToken.getLineNum();
        advance();
        EnumCvaToken curTokenEnum = curToken.toEnum();
        switch (curTokenEnum)
        {
            case ASSIGN:
            {
                advance();
                AbstractExpression expr = parseLinkedExpr();
                eatToken(EnumCvaToken.SEMI);
                return new CvaAssignStatement(lineNum, idLiteral, expr);
            }
            // 语法糖;
            case ADD_ASSIGN:
            {
                advance();
                AbstractExpression addAssignExpr =
                        new CvaOperandOperatorExpr.Builder()
                                .putLineNum(lineNum)
                                .putEnumExpr(EnumCvaExpr.ADD)
                                .putLeft(new CvaIdentifierExpr(lineNum, idLiteral))
                                .putRight(parseLinkedExpr())
                                // 改成获得expr的type, 枚举重指向;
                                .putInstType(EnumOperandType.INT)
                                .putInstOp(EnumOperator.ADD)
                                .build();
                eatToken(EnumCvaToken.SEMI);
                return new CvaAssignStatement(lineNum, idLiteral, addAssignExpr);
            }
            case SUB_ASSIGN:
            {
                advance();
                AbstractExpression subAssignExpr =
                        new CvaOperandOperatorExpr.Builder()
                                .putLineNum(lineNum)
                                .putEnumExpr(EnumCvaExpr.SUB)
                                .putLeft(new CvaIdentifierExpr(lineNum, idLiteral))
                                .putRight(parseLinkedExpr())
                                // 改成获得expr的type, 枚举重指向;
                                .putInstType(EnumOperandType.INT)
                                .putInstOp(EnumOperator.SUB)
                                .build();
                eatToken(EnumCvaToken.SEMI);
                return new CvaAssignStatement(lineNum, idLiteral, subAssignExpr);
            }
            case MULTIPLY_ASSIGN:
            {
                advance();
                AbstractExpression mulAssignExpr =
                        new CvaOperandOperatorExpr.Builder()
                                .putLineNum(lineNum)
                                .putEnumExpr(EnumCvaExpr.MUL)
                                .putLeft(new CvaIdentifierExpr(lineNum, idLiteral))
                                .putRight(parseLinkedExpr())
                                // 改成获得expr的type, 枚举重指向;
                                .putInstType(EnumOperandType.INT)
                                .putInstOp(EnumOperator.MUL)
                                .build();
                eatToken(EnumCvaToken.SEMI);
                return new CvaAssignStatement(lineNum, idLiteral, mulAssignExpr);
            }
            case DIV_ASSIGN:
            {
                advance();
                AbstractExpression divAssignExpr =
                        new CvaOperandOperatorExpr.Builder()
                                .putLineNum(lineNum)
                                .putEnumExpr(EnumCvaExpr.DIV)
                                .putLeft(new CvaIdentifierExpr(lineNum, idLiteral))
                                .putRight(parseLinkedExpr())
                                // 改成获得expr的type, 枚举重指向;
                                .putInstType(EnumOperandType.INT)
                                .putInstOp(EnumOperator.DIV)
                                .build();
                eatToken(EnumCvaToken.SEMI);
                return new CvaAssignStatement(lineNum, idLiteral, divAssignExpr);
            }
            case REM_ASSIGN:
            {
                advance();
                AbstractExpression remAssignExpr =
                        new CvaOperandOperatorExpr.Builder()
                                .putLineNum(lineNum)
                                .putEnumExpr(EnumCvaExpr.REM)
                                .putLeft(new CvaIdentifierExpr(lineNum, idLiteral))
                                .putRight(parseLinkedExpr())
                                // 改成获得expr的type, 枚举重指向;
                                .putInstType(EnumOperandType.INT)
                                .putInstOp(EnumOperator.REM)
                                .build();
                eatToken(EnumCvaToken.SEMI);
                return new CvaAssignStatement(lineNum, idLiteral, remAssignExpr);
            }
            case BIT_AND_ASSIGN:
            {
                advance();
                AbstractExpression bitAndAssignExpr =
                        new CvaOperandOperatorExpr.Builder()
                                .putLineNum(lineNum)
                                .putEnumExpr(EnumCvaExpr.BIT_AND)
                                .putLeft(new CvaIdentifierExpr(lineNum, idLiteral))
                                .putRight(parseLinkedExpr())
                                // 改成获得expr的type, 枚举重指向;
                                .putInstType(EnumOperandType.INT)
                                .putInstOp(EnumOperator.BIT_AND)
                                .build();
                eatToken(EnumCvaToken.SEMI);
                return new CvaAssignStatement(lineNum, idLiteral, bitAndAssignExpr);
            }
            case BIT_OR_ASSIGN:
            {
                advance();
                AbstractExpression bitOrAssignExpr =
                        new CvaOperandOperatorExpr.Builder()
                                .putLineNum(lineNum)
                                .putEnumExpr(EnumCvaExpr.BIT_OR)
                                .putLeft(new CvaIdentifierExpr(lineNum, idLiteral))
                                .putRight(parseLinkedExpr())
                                // 改成获得expr的type, 枚举重指向;
                                .putInstType(EnumOperandType.INT)
                                .putInstOp(EnumOperator.BIT_OR)
                                .build();
                eatToken(EnumCvaToken.SEMI);
                return new CvaAssignStatement(lineNum, idLiteral, bitOrAssignExpr);
            }
            case BIT_XOR_ASSIGN:
            {
                advance();
                AbstractExpression bitXorAssignExpr =
                        new CvaOperandOperatorExpr.Builder()
                                .putLineNum(lineNum)
                                .putEnumExpr(EnumCvaExpr.BIT_XOR)
                                .putLeft(new CvaIdentifierExpr(lineNum, idLiteral))
                                .putRight(parseLinkedExpr())
                                // 改成获得expr的type, 枚举重指向;
                                .putInstType(EnumOperandType.INT)
                                .putInstOp(EnumOperator.BIT_XOR)
                                .build();
                eatToken(EnumCvaToken.SEMI);
                return new CvaAssignStatement(lineNum, idLiteral, bitXorAssignExpr);
            }
            case LEFT_SHIFT_ASSIGN:
            {
                advance();
                AbstractExpression leftShiftAssignExpr =
                        new CvaOperandOperatorExpr.Builder()
                                .putLineNum(lineNum)
                                .putEnumExpr(EnumCvaExpr.LEFT_SHIFT)
                                .putLeft(new CvaIdentifierExpr(lineNum, idLiteral))
                                .putRight(parseLinkedExpr())
                                // 改成获得expr的type, 枚举重指向;
                                .putInstType(EnumOperandType.INT)
                                .putInstOp(EnumOperator.LEFT_SHIFT)
                                .build();
                eatToken(EnumCvaToken.SEMI);
                return new CvaAssignStatement(lineNum, idLiteral, leftShiftAssignExpr);
            }
            case RIGHT_SHIFT_ASSIGN:
            {
                advance();
                AbstractExpression rightShiftAssignExpr =
                        new CvaOperandOperatorExpr.Builder()
                                .putLineNum(lineNum)
                                .putEnumExpr(EnumCvaExpr.RIGHT_SHIFT)
                                .putLeft(new CvaIdentifierExpr(lineNum, idLiteral))
                                .putRight(parseLinkedExpr())
                                // 改成获得expr的type, 枚举重指向;
                                .putInstType(EnumOperandType.INT)
                                .putInstOp(EnumOperator.RIGHT_SHIFT)
                                .build();
                eatToken(EnumCvaToken.SEMI);
                return new CvaAssignStatement(lineNum, idLiteral, rightShiftAssignExpr);
            }
            case UNSIGNED_RIGHT_SHIFT_ASSIGN:
            {
                advance();
                AbstractExpression unsRightShiftAssignExpr =
                        new CvaOperandOperatorExpr.Builder()
                                .putLineNum(lineNum)
                                .putEnumExpr(EnumCvaExpr.UNSIGNED_RIGHT_SHIFT)
                                .putLeft(new CvaIdentifierExpr(lineNum, idLiteral))
                                .putRight(parseLinkedExpr())
                                // 改成获得expr的type, 枚举重指向;
                                .putInstType(EnumOperandType.INT)
                                .putInstOp(EnumOperator.UNSIGNED_RIGHT_SHIFT)
                                .build();
                eatToken(EnumCvaToken.SEMI);
                return new CvaAssignStatement(lineNum, idLiteral, unsRightShiftAssignExpr);
            }
            case INCREMENT:
            {
                eatToken(EnumCvaToken.INCREMENT);
                eatToken(EnumCvaToken.SEMI);
                return new CvaExprStatement(
                        lineNum, parseLinkedExpr());
            }
            case DECREMENT:
            {
                eatToken(EnumCvaToken.DECREMENT);
                eatToken(EnumCvaToken.SEMI);
                return new CvaExprStatement(
                    lineNum, parseLinkedExpr());
            }
            default:
            {
                errorLog("assign or increment or decrement", curTokenEnum);
                break;
            }
        }
        return null;
    }

    private AbstractStatement handleOpenCurly()
    {
        advance();
        int lineNum = curToken.getLineNum();
        AbstractStatement statement = new CvaBlockStatement(lineNum, parseStatementList());
        eatToken(EnumCvaToken.CLOSE_CURLY_BRACE);
        return statement;
    }

    private AbstractExpression handleIncDecExpr(AbstractExpression expr)
    {
        if (expr instanceof CvaIdentifierExpr)
        {
            CvaIdentifierExpr idExpr = (CvaIdentifierExpr) expr;
            switch (curToken.toEnum())
            {
                case INCREMENT:
                {
                    advance();
                    return new CvaIncDecExpr(
                            curToken.getLineNum(),
                            idExpr,
                            EnumIncDirection.INCREMENT);
                }
                case DECREMENT:
                {
                    advance();
                    return new CvaIncDecExpr(
                            curToken.getLineNum(),
                            idExpr,
                            EnumIncDirection.DECREMENT);
                }
                default:
                {
                    break;
                }
            }
        }
        return expr;
    }
}
