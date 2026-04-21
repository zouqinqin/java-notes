package jvm.classloader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * 打破双亲委派，让热部署的类优先走自己的加载器
 */
public class HotDeployClassLoader extends ClassLoader {

    private String classDir;

    public HotDeployClassLoader(String classDir) {
        super(ClassLoader.getSystemClassLoader());
        this.classDir = classDir;
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {

            // 检查是否已加载过
            Class<?> c = findLoadedClass(name);
            if (c != null) return c;

            // java. javax. 开头的核心类必须走父加载器
            if (name.startsWith("java.") || name.startsWith("javax.")) {
                return super.loadClass(name, resolve);
            }

            // 热部署目标包：优先自己加载，不委托父加载器
            // 这里是关键：打破双亲委派
            if (name.startsWith("jvm.hot.")) {
                try {
                    c = findClass(name);
                    if (resolve) resolveClass(c);
                    System.out.println("[HotDeployClassLoader] 自己加载: " + name
                            + "  加载器实例: " + System.identityHashCode(this));
                    return c;
                } catch (ClassNotFoundException e) {
                    // 自己找不到才委托
                }
            }

            // 其他类正常走双亲委派
            return super.loadClass(name, resolve);
        }
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        String path = classDir + "/" + name.replace(".", "/") + ".class";
        try {
            byte[] bytes = Files.readAllBytes(Paths.get(path));
            return defineClass(name, bytes, 0, bytes.length);
        } catch (IOException e) {
            throw new ClassNotFoundException("找不到: " + path);
        }
    }
}