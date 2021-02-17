package cn.misection.cvac.lexer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;

/**
 * @author Military Intelligence 6 root
 * @version 1.0.0
 * @ClassName BufferedHandler
 * @Description TODO
 * @TODO 后面把int改回char;
 * @CreateTime 2021年02月14日 14:10:00
 */
public final class BufferedQueueHandler
        extends BufferedReader implements IBufferedQueue
{
    private final StringBuffer buffer = new StringBuffer();

    public BufferedQueueHandler(Reader in) throws IOException
    {
        super(in);
        init();
    }

    private void init() throws IOException
    {
        load();
    }

    private void load() throws IOException
    {
        String line = null;
        while ((line = this.readLine()) != null)
        {
            buffer.append(line).append(LexerConstPool.NEW_LINE);
        }
        // 改进完毕, 只需要装载一个EOF了, 必须装载至少一个,
        //因为StringBuffer跟文件流不一样;
        // 这样才能模拟文件流;
        buffer.append(LexerConstPool.EOF);
        this.close();
    }

    @Override
    public char peek()
    {
        // buffer末位是eof, 就不怕溢出了;
        return buffer.charAt(0);
    }

    @Override
    public char peek(int num)
    {
        // TODO 判空不要在这里, 影响效率, 放到后面lexer中;
        return num >= buffer.length() ? 0 : buffer.charAt(num);
    }

    @Override
    public char poll()
    {
        char c = peek();
        buffer.delete(0, 1);
        return c;
    }

    @Override
    public String poll(int num)
    {
        String polled = buffer.substring(0, num);
        buffer.delete(0, num);
        return polled;
    }

    @Override
    public boolean isEmpty()
    {
        return buffer.length() == 0;
    }

    public StringBuffer getBuffer()
    {
        return buffer;
    }

    @Override
    public String toString()
    {
        return String.format("BufferedQueueHandler{buffer=%s}", buffer);
    }
}
