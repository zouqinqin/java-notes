package jvm;


public class GCStack {

    public static  int count = 0;

    public static void main(String[] args) {
        try {
            recurse();
        } catch (StackOverflowError  e) {
            System.out.println("栈溢出，递归深度：" + count);
        }
    }

    public static void recurse(){
        count ++ ;
        recurse();
    }
}
