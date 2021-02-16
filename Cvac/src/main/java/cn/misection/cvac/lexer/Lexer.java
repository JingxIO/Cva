package cn.misection.cvac.lexer;

import cn.misection.cvac.config.Macro;

import java.util.Map;

/**
 * Created by MI6 root 1/6.
 */
public class Lexer
{
    /**
     * input stream of the file;
     */
    private IBufferedQueue queueStream;

    private int lineNum;

    public Lexer(IBufferedQueue queueStream)
    {
        this.queueStream = queueStream;
        this.lineNum = 1;
    }

    public CvaToken nextToken()
    {
        return lex();
    }

    private CvaToken lex()
    {
        char c = this.queueStream.poll();

        // skip all kinds of blanks
        while (Character.isWhitespace(c))
        {
            if ('\n' == c)
            {
                lineNum++;
            }
            c = this.queueStream.poll();
        }

        // deal with comments
        if (c == '/')
        {
            char nextCh = (char) queueStream.peek();
            switch (nextCh)
            {
                case '/':
                {
                    while (nextCh != LexerConstPool.NEW_LINE)
                    {
                        nextCh = (char) this.queueStream.poll();
                    }
                    lineNum++;
                    // tail recursion;
                    return lex();
                }
                case '*':
                {
                    // TODO Block comment;
                    break;
                }
                default:
                {
                    return new CvaToken(CvaKind.DIV_OPERATOR, lineNum);
                }
            }
        }
        // 把单目符给抽象出来;
        switch (c)
        {
            case LexerConstPool.EOF:
            {
                if (Macro.DEBUG)
                {
                    System.err.println("bug place");
                }
                return new CvaToken(CvaKind.EOF, lineNum);
            }
            case '+':
                return new CvaToken(CvaKind.ADD, lineNum);
            case '-':
                return new CvaToken(CvaKind.SUB, lineNum);
            case '*':
                return new CvaToken(CvaKind.STAR, lineNum);
            case '&':
                c = this.queueStream.poll();
                if ('&' == c)
                {
                    return new CvaToken(CvaKind.AND_AND, lineNum);
                }
                else
                {
                    System.out.printf("Expect two &, but only got one at line %d%n", lineNum);
                    System.exit(1);
                }
            case '=':
                return new CvaToken(CvaKind.ASSIGN, lineNum);
//            case ':':
//                return new CvaToken(CvaKind.COLON, lineNum);
//            case ',':
//                return new CvaToken(CvaKind.COMMA, lineNum);
//            case '.':
//                return new CvaToken(CvaKind.DOT, lineNum);
            case '<':
                return new CvaToken(CvaKind.LESS_THAN, lineNum);
//            case '>':
//                return new CvaToken(CvaKind.MORE_THAN, lineNum);
//            case '!':
//                return new CvaToken(CvaKind.NEGATE, lineNum);
//            case '{':
//                return new CvaToken(CvaKind.OPEN_CURLY_BRACE, lineNum);
//            case '}':
//                return new CvaToken(CvaKind.CLOSE_CURLY_BRACE, lineNum);
//            case '(':
//                return new CvaToken(CvaKind.OPEN_PAREN, lineNum);
//            case ')':
//                return new CvaToken(CvaKind.CLOSE_PAREN, lineNum);
//            case ';':
//                return new CvaToken(CvaKind.SEMI, lineNum);
            default:
            {
                // 先看c是否是非前缀字符, 这里是 int, 必须先转成char看在不在表中;
                if (CvaKind.containsKind(String.valueOf(c)))
                {
                    return new CvaToken(CvaKind.selectReverse(String.valueOf(c)), lineNum);
                }
                StringBuilder builder = new StringBuilder();
                builder.append((char) c);
                while (true)
                {
                    c = queueStream.peek();
                    if (c != -1 && !Character.isWhitespace(c)
                            && !isSpecialCharacter(c))
                    {
                        builder.append((char) c);
                        this.queueStream.poll();
                    }
                    else
                    {
                        break;
                    }
                }
                String literal = builder.toString();
                if (CvaKind.containsKind(literal))
                {
                    return new CvaToken(CvaKind.selectReverse(literal), lineNum);
                }
                else
                {
                    if (isNumber(literal))
                    {
                        return new CvaToken(CvaKind.NUMBER, lineNum, builder.toString());
                    }
                    else if (isIdentifier(literal))
                    {
                        return new CvaToken(CvaKind.IDENTIFIER, lineNum, builder.toString());
                    }
                    else
                    {
                        System.err.printf("This is an illegal identifier at line %d%n", lineNum);
                        System.exit(1);
                        return null;
                    }
                }
            }
        }
    }

    private static boolean isSpecialCharacter(int c)
    {
        return '+' == c || '&' == c || '=' == c || ',' == c || '.' == c
                || '{' == c || '(' == c || '<' == c || c == '>'
                || '!' == c || c == '[' || c == ']'
                || '}' == c || ')' == c || ';' == c || ':' == c
                || '-' == c || '*' == c;
    }

    private static boolean isNumber(String str)
    {
        for (int i = 0; i < str.length(); i++)
        {
            if (str.charAt(i) < '0' || str.charAt(i) > '9')
            {
                return false;
            }
        }
        return true;
    }

    private static boolean isIdentifier(String str)
    {
        // 只接受字母开头, 不接受下划线开头;
        return Character.isAlphabetic(str.charAt(0));
    }

}
