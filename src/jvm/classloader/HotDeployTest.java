package jvm.classloader;

public class HotDeployTest {

    private static final String CLASS_DIR = System.getProperty("user.home") + "/hotdeploy/classes";
    private static final String CLASS_NAME = "jvm.hot.HotService";

    public static void main(String[] args) throws Exception {

        HotDeployEngine engine = new HotDeployEngine(CLASS_DIR, CLASS_NAME);

        // 第一次加载
        engine.reload();
        Object result1 = engine.invoke("execute");
        System.out.println("第一次调用结果: " + result1);

        // 等待修改
        System.out.println("\n>>> 现在去修改 HotService.java 并重新 javac 编译");
        System.out.println(">>> 编译命令：");
        System.out.println("    javac -d ~/hotdeploy/classes ~/hotdeploy/src/jvm/hot/HotService.java");
        System.out.println(">>> 编译完成后按回车键...");
        System.in.read();

        // 热更新
        engine.reload();
        Object result2 = engine.invoke("execute");
        System.out.println("热更新后调用结果: " + result2);

        // 验证
        System.out.println("\n===== 验证 =====");
        System.out.println("两次结果是否不同: " + !result1.equals(result2));
        System.out.println("热部署" + (!result1.equals(result2) ? "成功" : "失败"));
    }
}