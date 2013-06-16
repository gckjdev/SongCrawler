package com.orange.songcrawler.service;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import net.sf.json.JSONObject;

import org.apache.commons.io.FileUtils;
import org.htmlparser.Node;
import org.htmlparser.NodeFilter;
import org.htmlparser.Parser;
import org.htmlparser.filters.AndFilter;
import org.htmlparser.filters.HasAttributeFilter;
import org.htmlparser.filters.TagNameFilter;
import org.htmlparser.tags.LinkTag;
import org.htmlparser.util.NodeList;
import org.htmlparser.util.ParserException;

import com.orange.common.log.ServerLog;

public class SongCrawler {
	
    private static final int SONGS_PER_PAGE = 20; // 百度音乐歌手歌曲页面是每20首一个分页
	private static final ExecutorService downloader =  Executors.newCachedThreadPool();
	private static final String SONGS_INFO_DIR = "/home/larmbr/songs/";
	
	private static final List<String> errorCrawlingUrls = new ArrayList<String>();
	private static final String ERROR_CRAWLING_LOG = "/home/larmbr/error_crawling.log";
	
	public static void crawlBaiduMusicBySingers() {
		
		String baiduMusicUrl = "http://music.baidu.com/artist";
		
		try {
			Parser allSingersParser = new Parser(baiduMusicUrl);
			allSingersParser.setEncoding("UTF-8");
			
			// 抽取出包含*歌手列表*的部分, 放入nodeList中
			NodeFilter filter = new AndFilter(new TagNameFilter("li"), new HasAttributeFilter("class", "list-item"));
			NodeList nodeList = allSingersParser.parse(filter);
			
			// 从nodeList中抽取包含*歌手名字首字母*的部分,放入nameCapitalNodeList
			NodeFilter h3Filter = new TagNameFilter("h3");
			NodeList h3NodeList = nodeList.extractAllNodesThatMatch(h3Filter, true);
			NodeFilter nameCapitalFilter = new TagNameFilter("a");
			NodeList nameCapitalNodeList = h3NodeList.extractAllNodesThatMatch(nameCapitalFilter, true);
			
			// 从nodeList中抽取包含*歌手名字*的部分,放入singerNodeList
			NodeFilter singerFilter = new AndFilter(new TagNameFilter("ul"), new HasAttributeFilter("class", "clearfix"));
			NodeList singerNodeList = nodeList.extractAllNodesThatMatch(singerFilter, true);
			
			for (int i = 0; i < nodeList.size(); i++) {
				String nameCapital = nameCapitalNodeList.elementAt(i).getText();
				nameCapital = nameCapital.substring(nameCapital.indexOf("\"")+1, nameCapital.length()-1);
				if (! Character.isUpperCase(nameCapital.charAt(0))) {
					continue;
				}
				
				Node node = singerNodeList.elementAt(i);
				NodeList singerList = new NodeList();
				node.collectInto(singerList, new TagNameFilter("a"));
				for (int j = 0; j < singerList.size(); j++) {
					Node nameNode = singerList.elementAt(j);
					String url = ((LinkTag)nameNode).extractLink();
					
					ServerLog.info(0, "* 正在抓取: " + url);
					crawlAllSongsOfOneSinger(url, nameCapital);
				}
			}
			
		} catch (ParserException e) {
			e.printStackTrace();
		} finally {
			// 把抓取失败的信息errorCrawlingUrls写入文件,
			// 如果写入文件也失败，就直接输出到终端
			StringBuilder sb =  new StringBuilder("");
			for (String line: errorCrawlingUrls) {
				sb.append(line);
			}
			try {
				FileUtils.writeStringToFile(new File(ERROR_CRAWLING_LOG), sb.toString());
			} catch (IOException e) {
				System.err.print(sb.toString());
			}
			
		}
		
	}
	
	
	public static void crawlAllSongsOfOneSinger(String singerPageUrl, final String singerNameCapital) {

		if (singerPageUrl == null) {
			ServerLog.warn(0, "<crawlAllSongsOfOneSinger> singerPageUrl is null !" );
			return;
		}
		
		if (singerNameCapital == null) {
			// 名字首字母不能为空
			ServerLog.error(0, new Exception("<crawlAllSongsOfOneSinger>  singerNameCapital is null !" ));
			return;
		}
		
		try {
			// 从每歌手页面抽取其 *歌曲总数*
			//   <a class="list" hidefocus="true" href="#">歌曲(total)</a>
			Parser songTotalParser = new Parser(singerPageUrl);
			songTotalParser.setEncoding("UTF-8");
			NodeFilter songTotalFilter = new AndFilter(new TagNameFilter("a"), new HasAttributeFilter("class", "list"));
			NodeList songTotalNodeList = songTotalParser.parse(songTotalFilter);
			int total = 0 ;
			for (int i = 0; i < songTotalNodeList.size(); i++) {
				String text = songTotalNodeList.elementAt(i).toPlainTextString().trim();
				int startIdx = text.indexOf("歌曲(");
				if (startIdx != -1) {
					text = text.substring(startIdx+3); // 跳过"歌曲(" 这三个unicode字符
					total =  Integer.parseInt(text.substring(0, text.length()-1));
					break;
				} 
			}
			
			// 从每歌手页面抽取其 *歌手名字*
			//   <h2 class="singer-name"><singerName></h2>
			Parser singerNameParser = new Parser(singerPageUrl);
			singerNameParser.setEncoding("UTF-8");
			NodeFilter singerNameFilter = new AndFilter(new TagNameFilter("h2"), new HasAttributeFilter("class", "singer-name"));
			NodeList singerNameNodeList = singerNameParser.parse(singerNameFilter);
			final String singerName = singerNameNodeList.elementAt(0).toPlainTextString().trim();
			
			//　从每歌手信息页面url中抽取其 *ID*
			// 　http://music.baidu.com/artist/<uid>
            final int uid = Integer.parseInt(singerPageUrl.substring(30)); // 跳过"http://music.baidu.com/artist/"这30个字符
            
            
            //　接着进行对该歌手的*歌曲名字*信息收集
            // 上述的total用于帮助构造下面的url格式,　每个url获取*20*首歌曲信息
            // NOTE : 由于每个歌手的歌曲信息页面采取分页显示,　每个分页的url通过Firebug分析得出是这种形式:
            //        　http://music.baidu.com/data/user/getsongs?start=<20的倍数>&ting_uid=<uid>&order=hot
            //　　　　 有可能会被百度改变. 2013-06-14
            Future<List<String>> future = null;
            List<String> songData = null;
            try {
                final int numThreads = (total - 1 + SONGS_PER_PAGE) / SONGS_PER_PAGE;
                Callable<List<String>> task = 
                    new Callable<List<String>>() {
                        public List<String> call() throws Exception {
                    	    List<String> result =  new ArrayList<String>();
                    	    for (int i = 0 ; i < numThreads; i++) {
                    		    result.addAll(SongCrawler.downloadSongInforOneSinger(singerName, singerNameCapital, uid,i * SONGS_PER_PAGE));
                    	    }
						    return result;
                        }
                    };
                
                // 提交任务,　等待该歌手所有歌曲信息下载完成
                future = downloader.submit(task);

                // 获取结果
				songData = future.get();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				future.cancel(true);
				ServerLog.warn(0, "<crawlAllSongsOfOneSinger> Failed crawling " + singerPageUrl);
				errorCrawlingUrls.add(singerNameCapital + " : " + singerPageUrl + "\n"); 
			} catch (Exception e) {
				ServerLog.warn(0, "<crawlAllSongsOfOneSinger> Failed crawling " + singerPageUrl);
				errorCrawlingUrls.add(singerNameCapital + " : " + singerPageUrl + "\n");
			} catch (Error err) {
				ServerLog.warn(0, "<crawlAllSongsOfOneSinger> Failed crawling " + singerPageUrl);
				errorCrawlingUrls.add(singerNameCapital + " : " + singerPageUrl + "\n");
			} finally {
				//　完成后写入文件
				writeSingerSongsIndexFile(singerName, singerNameCapital, total, songData);
				// 同时在数据库中创建必要的表
				writeSingerSongsIndexIntoDB(total, songData);
			}
		} catch (ParserException e1) {
		    ServerLog.warn(0, "<crawlAllSongsOfOneSinger> Failed crawling " + singerPageUrl);
		    errorCrawlingUrls.add(singerNameCapital + " : " + singerPageUrl + "\n");
		}
    }

	
	private static void writeSingerSongsIndexIntoDB(int total,List<String> songData) {

		
	}

	private static void writeSingerSongsIndexFile(String singerName, String nameCapital, int totalSongs, List<String> songData) {

		if (singerName == null) 
			singerName = "未知";
		if (songData == null)
			songData = Collections.emptyList();
		
		//　头部 
		StringBuilder sb =  new StringBuilder("");
		sb.append("---");
		sb.append("\n歌手: " +singerName);  // 注意! 英文的冒号和空格
		sb.append("\n总数: " + totalSongs); // 注意! 英文的冒号和空格
		sb.append("\n---\n\n");
		
		//　主体
		for (String song: songData) {
			sb.append(song);
			sb.append("\n");
		}
		
		// 写入
		singerName = singerName.replaceAll("\\s+", "_");
		String pathName = SONGS_INFO_DIR + nameCapital + "/" + singerName + "/index";
		try {
			FileUtils.writeStringToFile(new File(pathName), sb.toString());
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	
    private static String queryUrl(String url) throws Exception {
		 
	     String result = "";  
	     BufferedReader in = null;  
	     
	     try {
	    	 URL connURL = new java.net.URL(url);  
	    	 HttpURLConnection httpConn = (HttpURLConnection)connURL.openConnection();  
	         // 建立实际的连接  
	         httpConn.connect();  
	         in = new BufferedReader(new InputStreamReader(httpConn.getInputStream(), "UTF-8"));  
	         
	         // 读取返回的内容  
	         String line;  
	         while ((line = in.readLine()) != null) {  
	            result += line;  
	         }  
	     } catch(MalformedURLException me) {
			 	ServerLog.info(0, "<queryUrl> Wrong query URL for img search, please check: " + url);
			 	throw new Exception();
	     } catch(IOException ie) {
	    	 	ServerLog.info(0, "<queryUrl> Query fails due to " + ie.toString());
	    	 	throw new Exception();
	     } catch (Exception e) {
	    	 	ServerLog.info(0, "<queryUrl> Query fails due to " + e.toString());
	    	 	throw new Exception();
	     } finally {  
	    	  try {  
		           if (in != null) {  
		              in.close();  
		            }  
		       } catch (IOException ex) {  
		           ex.printStackTrace();  
		        }  
	        }  
	      
	     return result;  
	 }
	
	
	
	private static Collection<String> downloadSongInforOneSinger(String singerName, String nameCapital, int uid, int index) 
			throws IOException, ParserException, Exception {
		
		List<String> result =  new ArrayList<String>();
		
		String url = "http://music.baidu.com/data/user/getsongs?start=" + index 
				     +"&ting_uid=" + uid + "&order=hot";
		
		// queryUrl　may throw exception
		String queryResult = SongCrawler.queryUrl(url);
		JSONObject jObject = JSONObject.fromObject(queryResult);
		JSONObject jObject2 = jObject.getJSONObject("data");
		String  htmlString = ((JSONObject)jObject2).getString("html");
		
		// 下载歌曲信息生成临时的html文件
		String pathName = null;
		File  tmpFile = null;
		singerName = singerName.replaceAll("\\s+", "_");
		pathName = SONGS_INFO_DIR + nameCapital + "/" + singerName +  "/" + singerName + "_" + index + ".html";
		tmpFile = new File(pathName);
		FileUtils.writeStringToFile(tmpFile, htmlString, null);

		
		// 解析生成的html文件　
		Parser parser = new Parser(pathName);
		parser.setEncoding("UTF-8");

		NodeFilter filter = new AndFilter(new TagNameFilter("div"),
				new HasAttributeFilter("class", "song-item"));
		NodeList nodeList = parser.parse(filter);

		NodeFilter songTitleFilter = new AndFilter(new TagNameFilter("span"),
				new HasAttributeFilter("class", "song-title ")); // 坑爹的,　注意最后有个空格!!
		NodeList songTitleNodeList = nodeList.extractAllNodesThatMatch(
				songTitleFilter, true);

		NodeFilter albumTitleFilter = new AndFilter(new TagNameFilter("span"),
				new HasAttributeFilter("class", "album-title"));
		NodeList albumTitleNodeList = nodeList.extractAllNodesThatMatch(
				albumTitleFilter, true);

		for (int i = 0; i < nodeList.size(); i++) {
			// 获取节点后,　节点中包含:
			// 　 <a href=..., title="歌名">text</a>
			// 手工从中抽取出歌名:　title=歌名
			// 　因为用toPlainTextString()获得的是text部分,而这部分
			// 　有时可能太长而有省略
			Node songTitleNode = songTitleNodeList.elementAt(i);
			String songName = songTitleNode.toHtml();
			int start = songName.indexOf("title=");
			songName = songName.substring(start + 7);
			int end = songName.indexOf("\"");
			songName = songName.substring(0, end);

			// 　获取节点后,　从中获取专辑名称(可能为空).　这个字段不重要,　所以直接用toPlainTextString()获得
			Node albumTitleNode = albumTitleNodeList.elementAt(i);
			String album = albumTitleNode.toPlainTextString().trim();

			ServerLog.info(0, songName + " : " + album);
			result.add(songName + " : " + album);
		}
		
		if (tmpFile != null)
		    tmpFile.delete();
		
		return result;

	}

	
	
    public static void main(String[] args) {
		
    	SongCrawler.crawlBaiduMusicBySingers();
    	
//       String singerPageUrl = "http://music.baidu.com/artist/1054";
//	   SongCrawler.crawlAllSongsOfOneSinger(singerPageUrl, "A");	
	}
}


