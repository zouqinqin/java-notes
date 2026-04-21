package jvm;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 演示使用MAT 查询GC Roots
 */
public class GCRoot_Demo {

    public static void main(String[] args) throws IOException {
        List<Object> list1 =new ArrayList<>();
        list1.add("a");
        list1.add("b");
        System.out.println("1");
        System.in.read();

        list1 = null;
        System.out.println("2");
        System.in.read();
        System.out.println("end....");

    }
}
