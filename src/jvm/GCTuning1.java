package jvm;

import java.util.*;

/**
 * 场景：电商订单处理系统
 * 每隔 100ms 来一批订单，系统处理后生成报表
 *
 * 运行参数：
 * -Xms64m -Xmx64m -Xmn32m -XX:+UseSerialGC
 * -XX:+PrintGCDetails -XX:+PrintGCDateStamps -Xloggc:gc-tuning.log
 */
public class GCTuning1 {

    // 模拟数据库里的"用户会话缓存"，key=userId
    static Map<String, UserSession> sessionCache = new HashMap<>();

    public static void main(String[] args) throws InterruptedException {

        System.out.println("=== 订单系统启动 ===");

        // 模拟系统预热：加载 500 个用户会话到缓存
        initSessionCache(500);

        // 模拟持续处理订单，跑 40 批
        for (int batch = 1; batch <= 40; batch++) {

            // 每批处理 20 笔订单
            List<Order> orders = generateOrders(20);
            List<String> report = buildReport(orders);

            if (batch % 10 == 0) {
                System.out.println("第 " + batch + " 批处理完成，报表行数：" + report.size());
            }

            Thread.sleep(100);
        }

        System.out.println("=== 系统运行结束 ===");
        System.out.println("sessionCache 大小：" + sessionCache.size());
    }

    // ========== 以下是各个功能模块 ==========

    static void initSessionCache(int count) {
        for (int i = 0; i < count; i++) {
            String userId = "user_" + i;
            sessionCache.put(userId, new UserSession(userId));
        }
    }

    static List<Order> generateOrders(int count) {
        List<Order> orders = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            orders.add(new Order(i));
        }
        return orders;
    }

    static List<String> buildReport(List<Order> orders) {
        List<String> lines = new ArrayList<>();

        for (Order order : orders) {
            // 问题一藏在这里：拼接订单描述
            StringBuilder sb = new StringBuilder();
            sb.append("订单号:").append(order.id);
            sb.append(" 用户:").append(order.userId);
            sb.append(" 金额:").append(order.amount);
            sb.append(" 商品数:").append(order.itemCount);
            sb.append(" 状态:").append(order.status);
            sb.append(" 时间:").append(order.createTime);
            lines.add(sb.toString());

            // 问题二藏在这里：每笔订单都去缓存查一次用户信息，查完就扔
            UserSession session = sessionCache.get(order.userId);

        }
        return lines;
    }


    // ========== 数据模型 ==========

    static class Order {
        int id;
        String userId;
        double amount;
        int itemCount;
        String status;
        String createTime;

        Order(int id) {
            this.id = id;
            this.userId = "user_" + (id % 500);
            this.amount = 100 + Math.random() * 900;
            this.itemCount = 1 + (int)(Math.random() * 10);
            this.status = "PAID";
            this.createTime = "2026-04-07 12:00:0" + (id % 10);
        }
    }

    static class UserSession {
        String userId;
        String deviceType;

        UserSession(String userId) {
            this.userId = userId;
            this.deviceType = "mobile";
            // 去掉 loginTime、lastActiveTime、ipAddress、cartItems
        }
    }
}