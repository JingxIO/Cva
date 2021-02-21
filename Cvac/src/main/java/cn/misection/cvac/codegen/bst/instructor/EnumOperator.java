package cn.misection.cvac.codegen.bst.instructor;

/**
 * @author Military Intelligence 6 root
 * @version 1.0.0
 * @ClassName EnumOperator
 * @Description 桥接模式底层操作符;
 * @CreateTime 2021年02月21日 22:24:00
 */
public enum EnumOperator implements IInstructor, Instructable
{
    /**
     * 底层操作符;
     */
    /*
     * neg 其实不应该出现, 其是一元的;
     */
    NEG("neg"),

    ADD("add"),

    SUB("sub"),

    MUL("mul"),

    DIV("div"),

    /**
     * 求余;
     */
    REM("rem"),

    AND("and"),

    OR("or"),

    XOR("xor"),

    SHL("shl"),

    SHR("shr"),

    /**
     * 无符号右移;
     */
    USHR("ushr"),

    RETURN("return"),
    ;
    
    private final String opInst;

    EnumOperator(String opInst)
    {
        this.opInst = opInst;
    }

    @Override
    public String instruction()
    {
        return opInst;
    }
}
