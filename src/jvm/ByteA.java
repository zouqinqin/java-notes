package jvm;

public class ByteA {
    public static void main(String[] args) {
        int a =10 ;
        int b = a ++ + ++a + a--;
        System.out.println(a);
        System.out.println(b);
    }
}
