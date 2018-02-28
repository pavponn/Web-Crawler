package crawler;
import base.Pair;
import java.io.*;
import java.net.*;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.*;
import java.util.regex.*;


public class SimpleWebCrawler implements WebCrawler {
    private Downloader downloader;
    private static Pattern title = Pattern.compile("<title>" + "(.*)" + "</title>");

    public SimpleWebCrawler(Downloader downloader) throws IOException {
        this.downloader = downloader;
    }

    private static String replaceSpecial(String str) {
        str = str.replaceAll("&lt;", "<");
        str = str.replaceAll("&gt;", ">");
        str =  str.replaceAll("&amp;", "&");
        str = str.replaceAll("&mdash;", "\u2014");
        str = str.replaceAll("&nbsp;", "\u00A0");
        return str;
    }
    private static String replaceCom(String page) {
        page = page.replaceAll("<!--.*?-->", "");
        return page;
    }
    private static String replaceAnchor(String url) {
        for (int i = 0; i < url.length(); i++) {
            if (url.charAt(i) == '#') {
                url = url.substring(0,i);
                break;
            }
        }
        return url;
    }


    private static List<String> extractDoubleTag(String source, String tagName) {
        List<String> stringsWithTags = new ArrayList<>();
        Pattern pattern = Pattern.compile("<" + tagName + ".*" + "?</" + tagName + ">");
        Matcher matcher = pattern.matcher(source);
        while (matcher.find()) {
            stringsWithTags.add(source.substring(matcher.start(), matcher.end()));
        }
        return stringsWithTags;
    }

    private static List<String> extractTag(String source, String tagName) {
        List<String> stringsWithTags = new ArrayList<>();
        Pattern pattern = Pattern.compile("<" + tagName + ".*?>");
        Matcher matcher = pattern.matcher(source);
        while (matcher.find()) {
            stringsWithTags.add(source.substring(matcher.start(), matcher.end()));
        }
        return stringsWithTags;
    }

    private static String extractTitle(String blank) {
        Matcher matcher = title.matcher(blank);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";

    }

    private static String takeAttribute(String tag, String attribute) {
        Pattern pattern = Pattern.compile(attribute + "\\s*=\\s*\"(.*?)\"");
        Matcher matcher = pattern.matcher(tag);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static String createFileName(String str) {
        str = str.replaceAll("://", "");
        str = str.replaceAll("/", "_");
        return str;
    }

    @Override
    public Page crawl(String url, int depth) {
        Deque<Pair<String, Integer>> crawlDeque = new ArrayDeque<>();
        Map<String, Page> globalPages = new HashMap<>();
        Map<String, Image> globalImages = new HashMap<>();
        Map<String, List<String>> childLinks = new LinkedHashMap<>();
        List <Page> linksbetweenpages= new ArrayList<>();

        URL first;
        try {
            first = new URL(url);
        } catch (MalformedURLException e) {
            System.err.println(url + " is not true link: " + e.getMessage());
            return null;
        }

        crawlDeque.add(Pair.of(first.toString(), depth));

        while (!crawlDeque.isEmpty()) {
            List<Image> localImages = new ArrayList<>();
            String localUrl = crawlDeque.getFirst().first;
            int localDepth = crawlDeque.getFirst().second;
            crawlDeque.removeFirst();
            if (globalPages.containsKey(localUrl)) {

                continue;

            } else if (localDepth == 0) {
                globalPages.put(localUrl, new Page(localUrl, ""));
            } else {

                String curPage;
                try (BufferedReader in = new BufferedReader(
                        new InputStreamReader(downloader.download(localUrl), "utf8"))) {
                    StringBuilder text = new StringBuilder();
                    String inputLine;
                    while ((inputLine = in.readLine()) != null)
                        text.append(inputLine);
                    curPage = text.toString();
                } catch (IOException e) {
                    System.err.println("Page reading failed : " + e.getMessage());
                    globalPages.put(localUrl, new Page(localUrl, ""));
                    continue;
                }
                URL convertedLocalURL;
                try {
                    convertedLocalURL = new URL(localUrl);
                } catch (MalformedURLException e) {
                    System.err.println(url + " is not real URL: " + e.getMessage());
                    continue;
                }

                String title = replaceSpecial(extractTitle(curPage));
                Page localPage = new Page(localUrl, title);
                curPage = replaceCom(curPage);
                List<String> tagsWithLinks = extractTag(curPage, "a");

                List<String> tmp = new ArrayList<>();
                for (int i = 0; i < tagsWithLinks.size(); i++) {
                    String possibleLink = takeAttribute(tagsWithLinks.get(i), "href");
                    if (possibleLink == null || possibleLink.equals("")) {
                        continue;
                    }
                    possibleLink = replaceAnchor(possibleLink);
                    possibleLink = replaceSpecial(possibleLink);
                    URL childUrl;
                    try {
                        childUrl = new URL(convertedLocalURL, possibleLink);
                    } catch (MalformedURLException e) {
                        System.err.println(url + " is not true URL: " + e.getMessage());
                        continue;
                    }
                    crawlDeque.add(Pair.of(childUrl.toString(), localDepth - 1)); //check

                    tmp.add(childUrl.toString());
                }
                childLinks.put(localUrl, tmp);

                List<String> ImagesInTags = extractTag(curPage, "img");

                for (int i = 0; i < ImagesInTags.size(); i++) {
                    String rawImageLink = takeAttribute(ImagesInTags.get(i), "src");
                    if (rawImageLink == null || rawImageLink.equals("")) {
                        continue;
                    }
                    rawImageLink = replaceSpecial(rawImageLink);
                    URL imageURL;
                    try {
                        imageURL = new URL(convertedLocalURL, rawImageLink);
                    } catch (MalformedURLException e) {
                        System.out.println(url + " is not real URL: " + e.getMessage());
                        continue;
                    }
                    if (globalImages.containsKey(imageURL.toString())) {
                        localImages.add(globalImages.get(imageURL.toString()));
                    } else {
                        try {

                            ReadableByteChannel rbc = Channels.newChannel(downloader.download(imageURL.toString()));
                            FileOutputStream fos = new FileOutputStream(createFileName(imageURL.toString()));
                            fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
                            Image img = new Image(imageURL.toString(), createFileName(imageURL.toString()));
                            localImages.add(img);
                            globalImages.put(imageURL.toString(), img);

                        } catch (FileNotFoundException e) {
                            System.err.println("Impossible to create " + createFileName(imageURL.toString()));
                        } catch (IOException e) {
                            System.err.println("Impossible to download " + imageURL);
                        }

                    }
                }
                for (int i = 0; i < localImages.size(); i++) {
                    localPage.addImage(localImages.get(i));
                }
                globalPages.put(localUrl, localPage);
                linksbetweenpages.add(localPage);
            }
        }
        for (int i = linksbetweenpages.size() - 1; i > -1; i--) {
            Page currentPage = linksbetweenpages.get(i);
            List<String> cur = childLinks.get(currentPage.getUrl());
            for (int j = 0; j < cur.size(); j++) {
                currentPage.addLink(globalPages.get(cur.get(j)));
            }
            globalPages.put(currentPage.getUrl(), currentPage);
        }
        return globalPages.get(first.toString());
    }
}
