// This is the entry point of the program

pkg cn.misection.cva.test;

call cva.native.io.*;
call cva.std.console.*;
/**
 * 原生string导入;
 *  由于是java StringBuffer读的, 所以支持中文注释;
 */
call cva.lang.type.String;


int main(string[] args)
{
    int num = 888;
    println num;
    return 0;
}

class Increment
{
    int incre()
    {
        int i = 0;

        while(i < 10)
        {
            echo i;
            echo "\t";
            i++;
        }
        int j = 10;
        while(j > 0)
        {
            echo j;
            echo "  ";
            j--;
        }
        return i;
    }
}
