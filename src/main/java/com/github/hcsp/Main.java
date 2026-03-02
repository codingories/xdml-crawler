package com.github.hcsp;

import org.apache.http.HttpEntity;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import javax.print.Doc;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Main {
    public static void main(String[] args) {
        String targetUrl = "https://sina.cn/";

        // 待处理的链接池
        List<String> linkPool = new ArrayList<>();
        // 已经处理的链接池
        Set<String> processedLinks = new HashSet<>();
        linkPool.add(targetUrl);
        while (true) {
            if (linkPool.isEmpty()) {
                break;
            }
            String link = linkPool.remove(linkPool.size() - 1);
            if (processedLinks.contains(link)) {
                continue;
            }

            if (isInterestingLink(link)) {
                // 2. 创建 GET 请求对象，并设置请求头（模拟浏览器，避免被反爬）
                Document doc = httpGetAndParseHtml(link);

                doc.select("a[href]").stream().map(aTag -> aTag.attr("abs:href")).forEach(linkPool::add);

                // 假如这是一个新闻的详情页面，就存入数据库，否则，就什么都不做
                storeIntoDatabaseIfItIsNewsPage(doc);

                processedLinks.add(link);
                // 3. 执行请求，获取响应

            }
            // 这是我们感兴趣的，我们只处理新浪站内的链接
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
        return "https://sina.cn/".equals(link);
    }

    private static boolean isNewsPage(String link) {
        return link.contains("news.sina.cn");
    }

    private static boolean isNotLoginPage(String link) {
        return !link.contains("passport.sina.cn");
    }
}
