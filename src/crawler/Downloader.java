package crawler;

import java.io.IOException;
import java.io.InputStream;


public interface Downloader {
    InputStream download(String url) throws IOException;
}
