package com.github.hcsp;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class Main {
    private static final String USER_NAME = "root";
    private static final String PASSWORD = "root";
    private static List<String> loadUrlsFromDatabase(Connection connection, String sql) throws SQLException {
        List<String> results = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            ResultSet resultSet = statement.executeQuery();
            while (resultSet.next()) {
                results.add(resultSet.getString(1));
            }
        }
        return results;
    }


    @SuppressFBWarnings("DMI_CONSTANT_DB_PASSWORD")
    public static void main(String[] args) throws SQLException {
        Connection connection = DriverManager.getConnection("jdbc:h2:file:/Users/ories/Downloads/java-zhangbo2/" +
                "30项目实战 - 多线程网络爬虫与Elasticsearch新闻搜索引擎" +
                "/project/xdml-crawler/news", USER_NAME, PASSWORD);

        while (true) {
            // 待处理的链接池
            // 从数据库加载即将处理的链接的代码
            String queryLinksToBeProcessedSql = "select * from LINKS_TO_BE_PROCESSED";
            List<String> linkPool = loadUrlsFromDatabase(connection, queryLinksToBeProcessedSql);

            if (linkPool.isEmpty()) {
                break;
            }

            // 从待处理池子中捞一个来删除
            // 处理完后从池子(包括数据库)中删除
            String link = linkPool.remove(linkPool.size() - 1);
            insertLinkIntoDatabase(connection, "DELETE FROM LINKS_TO_BE_PROCESSED WHERE LINK = ?", link);

            // 询问数据库，当前链接是不是已经被处理过了?
            if (isLinkProcessed(connection, link)) {
                continue;
            }

            if (isInterestingLink(link)) {
                // 2. 创建 GET 请求对象，并设置请求头（模拟浏览器，避免被反爬）
                Document doc = httpGetAndParseHtml(link);

                parseUrlsFromPageAndStoreIntoDatabase(doc, connection);

                // 假如这是一个新闻的详情页面，就存入数据库，否则，就什么都不做
                storeIntoDatabaseIfItIsNewsPage(doc);

                insertLinkIntoDatabase(connection, "INSERT INTO LINKS_ALREADY_PROCESSED (LINK) values (?)", link);
                // 3. 执行请求，获取响应

            }
            // 这是我们感兴趣的，我们只处理新浪站内的链接
        }

    }

    private static void parseUrlsFromPageAndStoreIntoDatabase(Document doc, Connection connection) throws SQLException {
        for (Element aTag : doc.select("a[href]")) {
            String href = aTag.attr("abs:href");
            insertLinkIntoDatabase(connection, "INSERT INTO LINKS_TO_BE_PROCESSED (LINK) values (?)", href);
        }
    }

    private static boolean isLinkProcessed(Connection connection, String link) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT LINK FROM LINKS_ALREADY_PROCESSED WHERE LINK = ?")) {
            statement.setString(1, link);
            ResultSet resultSet = statement.executeQuery();
            while (resultSet.next()) {
                return true;
            }
        }
        return false;
    }

    private static void insertLinkIntoDatabase(Connection connection, String sql, String link) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, link);
            statement.executeUpdate();
        }
    }


    private static void storeIntoDatabaseIfItIsNewsPage(Document doc) {
        ArrayList<Element> articleTags = doc.select("article");
        if (!articleTags.isEmpty()) {
            for (Element articleTag : articleTags) {
                String title = articleTags.get(0).child(0).text();
                System.out.println(title);
            }
        }
    }

    private static Document httpGetAndParseHtml(String link) {
        CloseableHttpClient httpClient = HttpClients.createDefault();
        HttpGet httpGet = new HttpGet(link);
        httpGet.setHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        httpGet.setHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        httpGet.setHeader("Accept-Encoding", "gzip, deflate");
        httpGet.setHeader("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");

        try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
            // 4. 检查响应状态码（200 表示成功）
            int statusCode = response.getStatusLine().getStatusCode();
            // 5. 获取响应实体，并转换为字符串（指定 UTF-8 编码避免乱码）
            HttpEntity entity = response.getEntity();
            String htmlContent = EntityUtils.toString(entity, StandardCharsets.UTF_8);
            // 解析HTML字符串为Document对象（核心入口）
            return Jsoup.parse(htmlContent);
//                // 7. 释放响应实体资源
//                EntityUtils.consume(entity);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean isInterestingLink(String link) {
        return isNewsPage(link) || isIndexPage(link) && isNotLoginPage(link);
    }

    private static boolean isIndexPage(String link) {
        return "https://sina.cn".equals(link);
    }

    private static boolean isNewsPage(String link) {
        return link.contains("news.sina.cn");
    }

    private static boolean isNotLoginPage(String link) {
        return !link.contains("passport.sina.cn");
    }
}
