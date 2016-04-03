package comp207p.target;

public class ConstantVariableFolding
{
    public int methodOne(){
        // int a = 429879283 - 876987;
        // System.out.println(527309 -1293 + 5 * a);
        // return 527309 -1293 + 5 * a;
        int a = 62;
        int b = (a + 764) * 3;
        return b + 1234 - a;
    }

    public double methodTwo(){
        double i = 0.67;
        int j = 1;
        return i + j;
    }

    public boolean methodThree(){
        int x = 12345;
        int y = 54321;
        return x > y;
    }

    public boolean methodFour(){
        long x = 4835783423L;
        long y = 400000;
        long z = x + y;
        return x > y;
    }
}