package com.github.hcsp;

import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

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

            if (link.contains("news.sina.cn") || targetUrl.equals(link)
                   ) {

                try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
                    // 2. 创建 GET 请求对象，并设置请求头（模拟浏览器，避免被反爬）
                    HttpGet httpGet = new HttpGet(link);
                    httpGet.setHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
                    httpGet.setHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
                    httpGet.setHeader("Accept-Encoding", "gzip, deflate");
                    httpGet.setHeader("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");

                    // 3. 执行请求，获取响应
                    try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
                        // 4. 检查响应状态码（200 表示成功）
                        int statusCode = response.getStatusLine().getStatusCode();
                        if (statusCode == 200) {
                            // 5. 获取响应实体，并转换为字符串（指定 UTF-8 编码避免乱码）
                            HttpEntity entity = response.getEntity();
                            String htmlContent = EntityUtils.toString(entity, StandardCharsets.UTF_8);

                            // 6. 输出爬取的网页内容
//                            System.out.println("===== 爬取的网页内容 =====");
//                            System.out.println(htmlContent);

                            // 解析HTML字符串为Document对象（核心入口）
                            Document doc = Jsoup.parse(htmlContent);
                            Elements links = doc.select("a[href]");

                            for (Element aTag : links) {
                                linkPool.add(aTag.attr("abs:href"));
                            }

                            // 假如这是一个新闻的详情页面，就存入数据库，否则，就什么都不做
                            ArrayList<Element> articleTags = doc.select("article");
                            if (!articleTags.isEmpty()) {
                                for (Element articleTag : articleTags) {
                                    String title = articleTags.get(0).child(0).text();
                                    System.out.println(title);
//                                    System.out.println("articleTag" + articleTag);
                                }
                            }

                            processedLinks.add(link);

                            // 7. 释放响应实体资源
                            EntityUtils.consume(entity);
                        } else {
                            System.out.println("请求失败，状态码：" + statusCode);
                        }
                    }
                } catch (IOException e) { // 仅捕获IO相关异常（受检异常）
                    e.printStackTrace();
                } catch (IllegalArgumentException e) { // 捕获URL格式错误等运行时异常
                    e.printStackTrace();
                }
                // 这是我们感兴趣的，我们只处理新浪站内的链接
            } else {
                // 这是我们不感兴趣的不去处理
            }


        }

        // 1. 创建 HttpClient 实例（推荐使用 CloseableHttpClient，支持资源关闭）

    }
}
