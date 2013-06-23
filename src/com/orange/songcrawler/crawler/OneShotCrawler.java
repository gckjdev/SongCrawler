package com.orange.songcrawler.crawler;

import java.io.IOException;

import org.htmlparser.util.ParserException;

import com.orange.common.log.ServerLog;
import com.orange.songcrawler.service.CrawlPolicy;
import com.orange.songcrawler.service.CrawlPolicy.NameCapital;
import com.orange.songcrawler.service.SongCrawler;


public class OneShotCrawler {
	
	public static void crawlTheWholeWorld(String host) throws ParserException, IOException {
		
		// 由自己所处哪台机器决定抓取什么首字母范围的歌手
      	NameCapital[] nameCapitalRange = CrawlPolicy.dispatchNameCapitalRange(host);
      	if (nameCapitalRange == null){
      		ServerLog.info(0, "<crawlTheWholdWorld> Bad range, the OneShotCralwer halts!!!");
      		return;
      	}
		
      	SongCrawler songCrawler = SongCrawler.getInstance();
      	
		//　第一步:　抓取指定范围歌手的URL
    	songCrawler.crawlSingersURLs(nameCapitalRange);
    	
    	//　第二步:　抓取指定范围歌手的所有歌曲的URL
    	songCrawler.crawlSongsURLs(nameCapitalRange);
    	
    	// 第三步:　抓取指定范围歌手的所有歌曲歌词
    	songCrawler.crawlSongsLyrics(nameCapitalRange);
    
	}
	
}
