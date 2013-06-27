package com.orange.songcrawler.service;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

import org.apache.commons.httpclient.Cookie;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.htmlparser.Node;
import org.htmlparser.NodeFilter;
import org.htmlparser.Parser;
import org.htmlparser.filters.AndFilter;
import org.htmlparser.filters.HasAttributeFilter;
import org.htmlparser.filters.TagNameFilter;
import org.htmlparser.tags.LinkTag;
import org.htmlparser.util.NodeList;
import org.htmlparser.util.ParserException;

public class LyricSearcher {

    private static final HttpClient httpClient = new HttpClient();  
      
    private static final String XIAMI_SEARCH_URL = "http://www.xiami.com/web/search?";  
    private static final String LOGIN_URL = "http://www.xiami.com/web/login";  
    
    private static final String EMAIL = "nasa4836@gmail.com";
    private static final String PASSWORD = "lcx209237";

	private static Cookie[] cookies = null;
  
	// 目前只支持在虾米查找歌词
	public enum SearchSite {
		XIAMI,
	};
	
    public static String searchLyric(SearchSite searchSite, String keyWord) {
    	
    	String getUrl = null;
		try {
			getUrl = XIAMI_SEARCH_URL + "key=" + URLEncoder.encode(keyWord, "UTF-8")
					     + "&submit=" + URLEncoder.encode("搜+索", "UTF-8");
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
    	
        String body = null;
		try {
			body = downloadURLwithCookie(getUrl);
		} catch (HttpException e) {
			e.printStackTrace();
			return null;
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
		
		
        String lyric = extractLyricFromResponseBody(body, keyWord);
				
        return lyric;
    }

	private static String downloadURLwithCookie(String getUrl) throws HttpException, IOException {
		
		GetMethod searchGet =  new GetMethod(getUrl);
        if (cookies == null ) {
        	postSignin();
        }
        searchGet.setRequestHeader("Cookie", cookies.toString());  
        
        httpClient.executeMethod(searchGet);
        
        String body = searchGet.getResponseBodyAsString();
		return body;
	}  
	
      
    private static String extractLyricFromResponseBody(String body, String keyWord) {

    	String lyric = "";
    	
		try {
			Parser songParser = Parser.createParser(body, "UTF-8");
			
			NodeFilter songDivFilter = new AndFilter(new TagNameFilter("div"),new HasAttributeFilter("class", "song"));
			NodeList songDivNodeList = songParser.parse(songDivFilter);
			
			NodeFilter songLiFilter = new TagNameFilter("li");
			NodeList songLiNodeList = songDivNodeList.extractAllNodesThatMatch(songLiFilter, true);
			
			if (songLiNodeList.size() != 0) {
				// <li> 
				//　　　<a href="歌曲链接"></a>
				//     <a href="歌手链接"></a>
				// </li>
				// 我们假定第一个就是最符合要求的搜索结果, 所以取elementAt(0)
				Node liNode = songLiNodeList.elementAt(0).getFirstChild();
				if (liNode instanceof LinkTag) {
					String targetUrl = "http://www.xiami.com" + ((LinkTag)liNode).extractLink();
					
					String page = downloadURLwithCookie(targetUrl);
					
					Parser lyricParser = Parser.createParser(page, "UTF-8");
					lyricParser.setEncoding("UTF-8");
					
					NodeFilter lyricFilter = new AndFilter(new TagNameFilter("div"),new HasAttributeFilter("class", "song_del"));
					NodeList lyricNodeList = lyricParser.parse(lyricFilter);
			
					for (int i = 0; i < lyricNodeList.size(); i++) {
						Node lyricNode = lyricNodeList.elementAt(i);
						if (lyricNode.toHtml().contains("===歌词===")) {
							lyric = lyricNode.toPlainTextString().replace("===歌词===", "");
							break;
						}
					}
				}
			}
		} catch (ParserException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return lyric;
	}

	public static void postSignin() throws HttpException, IOException {
    	
        PostMethod signinPost = new PostMethod(LOGIN_URL);
        
        signinPost.addParameter("email", EMAIL);  
        signinPost.addParameter("password", PASSWORD);
        signinPost.addParameter("remember","1");
        signinPost.addParameter("LoginButton","登录");
  
        httpClient.executeMethod(signinPost);
        
        // 保存cookie, 之后(在一定期限内,足够长)就可以不用再登录
        cookies = httpClient.getState().getCookies();  
    }
	
}