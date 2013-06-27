package com.orange.songcrawler.crawler;

import java.io.IOException;

import org.htmlparser.util.ParserException;

import com.orange.songcrawler.service.CrawlPolicy;
import com.orange.songcrawler.service.CrawlPolicy.NameCapital;
import com.orange.songcrawler.service.SongCrawler;


public class OneShotCrawler {
	
	private static final OneShotCrawler crawler = new OneShotCrawler();
	private OneShotCrawler() {}
	public static OneShotCrawler getInstance() {
		return crawler;
	}
	
	
	public void crawlTheWholeWorld(String host) throws ParserException, IOException {
		
		// 由自己所处哪台机器决定抓取什么首字母范围的歌手
      	NameCapital[] nameCapitalRange = CrawlPolicy.dispatchNameCapitalRange(host);
		
      	SongCrawler songCrawler = SongCrawler.getInstance();
      	
		//　第一步:　抓取指定范围歌手的URL
    	songCrawler.crawlSingersURLs(nameCapitalRange);
    	
    	//　第二步:　抓取指定范围歌手的所有歌曲的URL
    	songCrawler.crawlSongsURLs(nameCapitalRange);
    	
    	// 第三步:　抓取指定范围歌手的所有歌曲歌词
    	songCrawler.crawlSongsLyrics(nameCapitalRange);
    
	}
	
}
