package crawler;
import  java.io.IOException;

public interface WebCrawler {
    Page crawl(String url, int depth) throws IOException;
}
