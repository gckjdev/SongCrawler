package com.orange.songcrawler.service;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import net.sf.json.JSONObject;

import org.apache.commons.io.FileUtils;
import org.htmlparser.Node;
import org.htmlparser.NodeFilter;
import org.htmlparser.Parser;
import org.htmlparser.filters.AndFilter;
import org.htmlparser.filters.HasAttributeFilter;
import org.htmlparser.filters.NotFilter;
import org.htmlparser.filters.TagNameFilter;
import org.htmlparser.nodes.TextNode;
import org.htmlparser.tags.LinkTag;
import org.htmlparser.util.NodeList;
import org.htmlparser.util.ParserException;

import com.orange.common.log.ServerLog;
import com.orange.common.utils.http.HttpDownload;
import com.orange.songcrawler.service.CrawlPolicy.NameCapital;
import com.orange.songcrawler.service.LyricSearcher.SearchSite;
import com.orange.songcrawler.util.FileHierarchyBuilder;
import com.orange.songcrawler.util.FileHierarchyBuilder.SingerIndexLine;
import com.orange.songcrawler.util.FileHierarchyBuilder.SongIndexLine;
import com.orange.songcrawler.util.PropertyConfiger;

public class SongCrawler {

	private static final int SONGS_PER_PAGE = 20; // 百度音乐歌手歌曲页面是每20首一个分页
	private static final String BAIDU_MUSIC_HOME_PAGE = "http://music.baidu.com/artist"; 
	
	private static final FileHierarchyBuilder fileHierarchyBuilder = FileHierarchyBuilder.getInstance();
	
	// 记录*出错时正在抓取哪个字母的歌手URL*, 以便从此处断点续爬
	private static final String ERROR_CRAWLING_SINGERS_URL_LOG = fileHierarchyBuilder.getErrorCrawlingSingersURLLog();
	
	// 记录*所有抓取歌曲URL失败的歌手URL*, 以便重新抓取
	private final List<String> errorCrawlingURLs = new ArrayList<String>();
	private static final String ERROR_CRAWLING_SONGS_URL_LOG = fileHierarchyBuilder.getErrorCrawlingSongsURLLog();
	
	// 记录*所有抓取失败的歌曲URL*, 以便重新抓取
	private final List<String> errorCrawlingLyrics = new ArrayList<String>();
	private static final String ERROR_CRAWLING_SONGS_LYRICS_LOG = fileHierarchyBuilder.getErrorCrawlingLyricsLog();
	
	private final ExecutorService downloader =  Executors.newCachedThreadPool();
	
	private static final SongCrawler songCrawler = new SongCrawler();
	private SongCrawler() {}
	public static SongCrawler getInstance() {
		return songCrawler;
	}
	
	public void crawlSingersURLs(NameCapital[] nameCapitalRange) throws  ParserException, IOException {
		
		String nameCapital = null; 
		File errorCrawlingSingersURLLog = new File(ERROR_CRAWLING_SINGERS_URL_LOG);
		
		try {
		    /* --- 解析百度音乐首页，抽取歌手信息开始 --- */
			
			Parser allSingersParser = new Parser(BAIDU_MUSIC_HOME_PAGE);
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
			
		    /* --- 解析百度音乐首页，抽取歌手信息结束 --- */
			
			// 开始抓取
			for (int i = 0; i < nodeList.size(); i++) {
				nameCapital = nameCapitalNodeList.elementAt(i).getText();
				nameCapital = nameCapital.substring(nameCapital.indexOf("\"")+1, nameCapital.length()-1);
				if (! Character.isUpperCase(nameCapital.charAt(0)))
					// 歌手分类中除了A-Z，还有别的，我们跳过它
					continue;
				if (NameCapital.valueOf(nameCapital).ordinal() < nameCapitalRange[0].ordinal() ||
						NameCapital.valueOf(nameCapital).ordinal() > nameCapitalRange[1].ordinal())
					// 不在范围内的跳过
					continue;
				if (new File(fileHierarchyBuilder.getSingerIndexFileName(nameCapital)).canRead()) {
					// 跳过已经抓取的首字母
					ServerLog.info(0, "* 已存在，跳过写入歌手URL信息，首字母：" + nameCapital + "...");
					continue;
				}
				
				Node node = singerNodeList.elementAt(i);
				NodeList singerList = new NodeList();
				node.collectInto(singerList, new TagNameFilter("a"));
				List<String> singerURLs = new ArrayList<String>(); //该首字母下的所有歌手URL
				for (int j = 0; j < singerList.size(); j++) {
					Node nameNode = singerList.elementAt(j);
					String singerName = nameCleaner(((LinkTag)nameNode).getAttribute("title"));
					String URL = ((LinkTag)nameNode).extractLink();
					singerURLs.add(SingerIndexLine.buildLine(singerName, URL));
				}
				
				ServerLog.info(0, "* 正在写入歌手URL信息，首字母：" + nameCapital + "...");
				fileHierarchyBuilder.writeSingerIndexFile(nameCapital, singerURLs);
			}
			
		} catch (ParserException e) {
			// 把当前失败的首字母写入文件，下次运行这个方法从这个字母开始，跳过之前抓取成功的字母.
			// 然后抛出异常到控制台(毕竟抓取URL就已经失败了,后面也无法完成.所以中断)
			ServerLog.warn(0, "    Crawling name capital " + nameCapital + " fails ! Caused by : " + e.getCause());
			FileUtils.writeStringToFile(errorCrawlingSingersURLLog, nameCapital);
			throw e;
		} catch (IOException e) {
			ServerLog.warn(0, "    Crawling name capital " + nameCapital + " fails ! Caused by : " + e.getCause());
			FileUtils.writeStringToFile(errorCrawlingSingersURLLog, nameCapital);
			throw e;
		} finally {
			// 如果成功爬完所有字母，并且有这个错误文件存在的话，就把它删除掉
			if (nameCapital!=null && nameCapital.equals("Z") && errorCrawlingSingersURLLog.exists()) {
				errorCrawlingSingersURLLog.delete();
			}
		}
	}
	
	
	public String nameCleaner(String name) {
		String result;
		result = name.replace("&#039;", "'");
		result = result.replace("&amp;", "&");
		result = result.replace("&eacute;", "é");
		result = result.replace(";", "_");
		
		return result;
	}
	
	public void crawlSongsURLs(NameCapital[] nameCapitalRange) throws IOException {
	
		if (nameCapitalRange[0].compareTo(nameCapitalRange[1]) > 0) {
			ServerLog.warn(0, "Bad name capital range !!!");
			return;
		}
		
		for (int i = nameCapitalRange[0].ordinal(); i <= nameCapitalRange[1].ordinal(); i++) {
			crawlSongsURLsForNameCapital(NameCapital.valueOf(i));
		}
		
		// 爬完所有字母就把失败URL写入文件,以便之后重新抓取
		// 如果写入失败,就抛出异常,并最终传递到控制台
		//　因为这个*写入失败URL*操作已经是补救措施,如果补救措施仍失败,那么只好向控制台报告错误
		FileUtils.writeLines(new File(ERROR_CRAWLING_SONGS_URL_LOG), errorCrawlingURLs);
	}

	
	public void crawlSongsLyrics(NameCapital[] nameCapitalRange) throws IOException {
		
		if (nameCapitalRange[0].compareTo(nameCapitalRange[1]) > 0) {
			ServerLog.warn(0, "Bad name capital range !!!");
			return;
		}
		
		for (int i = nameCapitalRange[0].ordinal(); i <= nameCapitalRange[1].ordinal(); i++) {
			crawlSongsLyricsForNameCapital(NameCapital.valueOf(i));
		}
		
		// 爬完就把 ＊抓取失败的URL＊ 写入文件,以便之后重新抓取
		// 如果写入失败,就抛出异常,并最终传递到控制台
	    //　因为这个*写入失败URL*操作已经是补救措施,如果补救措施仍失败,那么只好向控制台报告错误
		FileUtils.writeLines(new File(ERROR_CRAWLING_SONGS_LYRICS_LOG), errorCrawlingLyrics);
	}


	
	
	private void crawlSongsURLsForNameCapital(NameCapital nameCapital) {

		// 先读取该字母下的singer_index文件，然后依次爬其中的歌手
		List<String> lines = fileHierarchyBuilder.parseSingerIndexFile(nameCapital.getCapital());
		if (lines == null) {
			ServerLog.warn(0, "    Crawing songs URL for name capital " + nameCapital.getCapital() + " fails!");
			errorCrawlingURLs.add(nameCapital.getCapital() + " : ");
			return;
		}
		
		for (String line: lines) {
			SingerIndexLine singerIndexLine = new SingerIndexLine(line);
			String singerName = singerIndexLine.getSingerName();
			String singerURL = singerIndexLine.getSingerURL();
			
			String songIndexPath = fileHierarchyBuilder.getSingerSongIndexFileName(singerName, nameCapital.getCapital());
			if (new File(songIndexPath).canRead()) {
				ServerLog.info(0, "* 已存在,　跳过歌手: " + singerName);
				continue;
			}
			// 抓取该歌手的所有歌曲URL
			ServerLog.info(0, "* 正在抓取歌手[" + singerName + "]的所有歌曲URL:　" + singerURL);
			crawlAllSongsURLsForOneSinger(singerName, nameCapital.getCapital(), singerURL);
			try {
				int sleep_interval_second = 30;
				ServerLog.info(0, "睡眠" + sleep_interval_second + "秒钟zzzZZZ~~~~~~");
				Thread.sleep(sleep_interval_second * 1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}


	private void crawlAllSongsURLsForOneSinger(final String singerName, final String nameCapital, final String singerPageURL) { 

		try {
			// 从每歌手页面抽取其 *歌曲总数*
			//   <a class="list" hidefocus="true" href="#">歌曲(total)</a>
			Parser songTotalParser = new Parser(singerPageURL);
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
			
			//　从每歌手信息页面URL中抽取其 *ID*
			// 　http://music.baidu.com/artist/<uid>
            final int uid = Integer.parseInt(singerPageURL.substring(30)); // 跳过"http://music.baidu.com/artist/"这30个字符
            
            
            //　接着进行对该歌手的*歌曲名字*信息收集
            // 上述的total用于帮助构造下面的URL格式,　每个URL获取*20*首歌曲信息
            // NOTE : 由于每个歌手的歌曲信息页面采取分页显示,　每个分页的URL通过Firebug分析得出是这种形式:
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
                    		    result.addAll(crawlSongInfo(singerName, nameCapital, uid,i * SONGS_PER_PAGE));
                    	    }
						    return result;
                        }
                    };
                
                // 提交任务,　等待该歌手所有歌曲信息下载完成
                future = downloader.submit(task);

                // 获取结果
				songData = future.get(3, TimeUnit.MINUTES);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				future.cancel(true);
				ServerLog.warn(0, "    Failed crawling " + singerPageURL + " Due to " + e.getCause());
				errorCrawlingURLs.add(nameCapital + " : " + singerPageURL + "\n");
			    return;
			} catch (TimeoutException e) {
				String cmd = PropertyConfiger.getRunCommand();
				Runtime rt = Runtime.getRuntime();
				try {
					FileUtils.writeLines(new File(ERROR_CRAWLING_SONGS_URL_LOG), errorCrawlingURLs);
					ServerLog.info(0, "超时未获得抓取结果,重启程序运行!!!");
					rt.exec(cmd);
					System.exit(0);
				} catch (IOException e1) {
					e1.printStackTrace();
				}
			} catch (Exception e) {
				ServerLog.warn(0, "    Failed crawling " + singerPageURL + " Due to " + e.getCause());
				e.printStackTrace();
				errorCrawlingURLs.add(nameCapital + " : " + singerPageURL + "\n");
				return;
			}
            
			//　完成后写入文件
            if (songData != null && songData.size() != 0) {
            	fileHierarchyBuilder.writeSingerSongIndexFile(singerName, nameCapital, total, songData);
            }
		} catch (ParserException e1) {
		    ServerLog.warn(0, "    Failed crawling " + singerPageURL);
		    errorCrawlingURLs.add(nameCapital + " : " + singerPageURL + "\n");
		} catch (IOException e) {
			ServerLog.warn(0, "    Failed crawling " + singerPageURL);
		    errorCrawlingURLs.add(nameCapital + " : " + singerPageURL + "\n");
		}
    }


	private Collection<String> crawlSongInfo(String singerName, String nameCapital, int uid, int index) throws Exception {
		
		List<String> result =  new ArrayList<String>();
		
		String URL = "http://music.baidu.com/data/user/getsongs?start=" + index 
				     +"&ting_uid=" + uid + "&order=hot";
		
		// queryURL　may throw exception
		String queryResult = null;
		try {
			queryResult = HttpDownload.downloadFromURL(URL);
		} catch (Exception e) {
			ServerLog.info(0, "<crawlSongInfo> Query fails : " +  URL + ", due to " + e.getCause());
			throw e;
		}
		JSONObject jObject = JSONObject.fromObject(queryResult);
		JSONObject jObject2 = jObject.getJSONObject("data");
		String  htmlString = ((JSONObject)jObject2).getString("html");
		
		// 下载歌曲信息生成临时的html文件
		String pathName = null;
		File  tmpFile = null;
		singerName = singerName.replaceAll("\\s+", "_");
		pathName = fileHierarchyBuilder.getTmpFile(nameCapital, singerName, index);
		tmpFile = new File(pathName);
		try {
			FileUtils.writeStringToFile(tmpFile, htmlString, null);
		} catch (IOException e) {
			ServerLog.info(0, "<crawlSongInfo> Creating temp file fails , due to " + e.getCause());
			throw e;
		}

		
		/*
		 *  解析生成的html文件　
		 */
		Parser parser;
		try {
			parser = new Parser(pathName);
			parser.setEncoding("UTF-8");
			NodeFilter filter = new AndFilter(new TagNameFilter("div"),
					new HasAttributeFilter("class", "song-item"));
			NodeList nodeList = parser.parse(filter);


			// 抽取出*歌曲信息*节点
			NodeFilter songTitleSpanFilter = new AndFilter(new TagNameFilter("span"),
					new HasAttributeFilter("class", "song-title ")); // 坑爹的,　注意最后有个空格!!
			NodeList songTitleSpanNodeList = nodeList.extractAllNodesThatMatch(songTitleSpanFilter, true);
			NodeFilter songTitleFilter = new TagNameFilter("a");
			NodeList songTitleRawNodeList = songTitleSpanNodeList.extractAllNodesThatMatch(songTitleFilter, true);
			  //　过滤掉一些干扰节点,　如"歌曲MV"节点
			@SuppressWarnings("serial")
			NodeFilter notFilter = new NotFilter(new NodeFilter() {
				@Override
				public boolean accept(Node arg0) {
					if (arg0 instanceof LinkTag) {
					    if (((LinkTag)arg0).getAttribute("title") == null)
					    	return true;
						if (((LinkTag)arg0).getAttribute("title").equals("歌曲MV"))
							return true;
						return false;
					}
					if (arg0 instanceof TextNode)
						return true;
					return false;
				}
			});
			NodeList songTitleNodeList = songTitleRawNodeList.extractAllNodesThatMatch(notFilter, true);

			
			for (int i = 0; i < nodeList.size(); i++) {
				// 获取歌曲名称节点,　节点中包含:
				// 　 <a href=..., title="歌名">text</a>
				// 抽取出歌名:　title　和 歌曲链接:　href      
				Node songTitleNode = songTitleNodeList.elementAt(i);
				String songName = nameCleaner(((LinkTag)songTitleNode).getAttribute("title"));
				String songURL  = "http://music.baidu.com" + ((LinkTag)songTitleNode).getLink().replace("file://localhost", "");

				// 获取专辑节点后,节点中可能为空, 也可能包含:
				// 　 <a href=..., title="专辑名">text</a>
				Node node = nodeList.elementAt(i);
				NodeList albumSpanTagList = new NodeList();
				node.collectInto(albumSpanTagList,  new AndFilter(new TagNameFilter("span"),new HasAttributeFilter("class", "album-title")));
				NodeList albumATagList = new NodeList();
				albumSpanTagList.elementAt(0).collectInto(albumATagList, new TagNameFilter("a"));
				String album = "";
				if (albumATagList.size() != 0) {
					album = nameCleaner(albumATagList.elementAt(0).toPlainTextString());
				}

				result.add(SongIndexLine.buildLine(songName, songURL, album));
				ServerLog.info(0, songName + " || " + songURL + " || " + album);
			}
		} catch (ParserException e) {
			ServerLog.info(0, "<crawlSongInfo> Parsing html fails , due to " + e.getCause());
			throw e;
		} finally {
			if (tmpFile != null)
				tmpFile.delete();
		}
		
		return result;
	}


	private void crawlSongsLyricsForNameCapital(NameCapital nameCapital) {

		// 先读取该字母下的singer_index文件，然后依次爬其中的歌手
		List<String> lines = fileHierarchyBuilder.parseSingerIndexFile(nameCapital.getCapital());
		if (lines == null) {
			ServerLog.warn(0, "    Crawing songs lyrics for name capital " + nameCapital.getCapital() + " fails!");
			errorCrawlingLyrics.add(nameCapital.getCapital() + " : ");
			return;
		}
		
		for (String line: lines) {
			SingerIndexLine singerIndexLine = new SingerIndexLine(line);
			String singerName = singerIndexLine.getSingerName();
			
			// 抓取该歌手的所有歌曲歌词
			ServerLog.info(0, "* 正在抓取歌手[" + singerName + "]的所有歌曲的歌词...");
			crawlAllSongsLyricsForOneSinger(singerName, nameCapital.getCapital());
		}
	}


	
	private void crawlAllSongsLyricsForOneSinger(final String singerName, final String nameCapital) {
		
		// 先读取该歌手目录下的song_index文件，然后依次抓取其中的歌曲的歌词
		List<String> lines = fileHierarchyBuilder.parseSingerSongIndexFile(singerName, nameCapital);
		if (lines == null) {
			ServerLog.warn(0, "   Crawing songs lyrics for singer [" +singerName + "] fails!");
			errorCrawlingLyrics.add(nameCapital + " : ");
			return;
		}
		
		int skipTimes = 0;
		for (String line: lines) {
			SongIndexLine songIndexLine = new SongIndexLine(line);
			String songName = songIndexLine.getSongName();
			String songURL = songIndexLine.getSongURL();
			String songAlbum = songIndexLine.getSongAlbum();

			String songLyricPath = fileHierarchyBuilder.getLyricFileName(singerName, nameCapital, songName); 
			if (new File(songLyricPath).canRead()) {
				ServerLog.info(0, "  * 已存在歌曲[" + songName + "]（nameCapital: " +nameCapital + ", singer : " + singerName +" ）,　跳过...");
				skipTimes++;
				continue;
			}
			
			// 抓取这首歌歌词和分类信息
			ServerLog.info(0, "  * 正在抓取歌曲[" + songName + "]的歌词（nameCapital: " +nameCapital + ", singer : " + singerName +" ）...");
			crawlLyricOfOneSong(songName, songURL, songAlbum, singerName, nameCapital);
			try {
				int sleep_interval_second = 3;
				ServerLog.info(0, "睡眠" + sleep_interval_second + "秒钟zzzZZZ~~~~~~");
				Thread.sleep(sleep_interval_second * 1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		if (skipTimes != lines.size()) {
			try {
				int sleep_interval_second = 20;
				ServerLog.info(0, "睡眠" + sleep_interval_second + "秒钟zzzZZZ~~~~~~");
				Thread.sleep(sleep_interval_second * 1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	
	//　顺利的话从songURL中可以抓取到歌词; 如果没有歌词,则利用传入的信息从别的站点抓取
	private void crawlLyricOfOneSong(final String songName, final String songURL,
			String songAlbum, final String singerName, final String nameCapital) {

		ExecutorService exec = Executors.newFixedThreadPool(1);
		Callable<String> downloadLyric = new Callable<String>() {

			@Override
			public String call() throws Exception {
				String lyric = null;
				try {
					Parser lyricParser = new Parser(songURL);
					lyricParser.setEncoding("UTF-8");

					//　抽取出歌词
					NodeFilter lyricFilter = new AndFilter(new TagNameFilter("div"), new HasAttributeFilter("id", "lyricCont"));
					NodeList lyricNodeList = lyricParser.parse(lyricFilter);

					if (lyricNodeList.size() != 0 ) {
						Node node = lyricNodeList.elementAt(0);
						lyric = lyricCleaner(node.toPlainTextString());
					} else {
						// 很不幸,　我们得去别的站点尝试下载歌词 -.-
						ServerLog.info(0, "　 * 未在百度找到歌曲[" + songName + "]的歌词, 正在尝试在其他站点中查询...");
						lyric = LyricSearcher.searchLyric(SearchSite.XIAMI, songName);  
						if (lyric.equals("")) {
							// 如果还是那么不幸没找到,那就作罢...
							ServerLog.info(0, "　 * 未在虾米找到歌曲[" + songName + "]的歌词, 放弃...");
							lyric = "抱歉,　暂无歌词!";
						}
					}
				} catch (ParserException e) {
					e.printStackTrace();
					errorCrawlingLyrics.add(songURL);
				} catch (Exception e) {
					e.printStackTrace();
					errorCrawlingLyrics.add(songURL);
				}
				return lyric;
			}

		};
		
		Future<String> future = exec.submit(downloadLyric);
		try {
			// 如果３分钟没有结果,就重启该程序.　该程序会自动跳过已经完成的,
			//　接着运行.
			String lyric = future.get(3, TimeUnit.MINUTES);
			if (lyric != null) {
				// 成功下载歌词,写入文件
				String lyricFilePath = fileHierarchyBuilder.getLyricFileName(singerName, nameCapital, songName);
				FileUtils.writeStringToFile(new File(lyricFilePath), lyric, "UTF-8");				
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
			errorCrawlingLyrics.add(songURL);
		} catch (ExecutionException e) {
			e.printStackTrace();
			errorCrawlingLyrics.add(songURL);
		} catch (IOException e) {
			e.printStackTrace();
			errorCrawlingLyrics.add(songURL);
		} catch (TimeoutException e) {
			String cmd = PropertyConfiger.getRunCommand();
			Runtime rt = Runtime.getRuntime();
			try {
				FileUtils.writeLines(new File(ERROR_CRAWLING_SONGS_URL_LOG), errorCrawlingURLs);
				FileUtils.writeLines(new File(ERROR_CRAWLING_SONGS_LYRICS_LOG), errorCrawlingLyrics);
				ServerLog.info(0, "超时未获得抓取结果,重启程序运行!!!");
				rt.exec(cmd);
				System.exit(0);
			} catch (IOException e1) {
				e1.printStackTrace();
			}
		}
	}


	public String lyricCleaner(String rawLyric) {
		
		String[] lines = rawLyric.split("\n");
		String result = "";
		for (String line: lines) {
			result += line.replaceFirst("\\s+", "") + "\n";
		}
		
		return result;
	}
	
	public static void main(String[] args){
		SongCrawler s = SongCrawler.getInstance();
		String name = "/home/larmbr/r&amp;b/test";
		try {
			FileUtils.writeStringToFile(new File(s.nameCleaner(name)), "test");
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
}