package com.orange.songcrawler.crawler;

import com.orange.songcrawler.service.SongCrawler;

public class DailyCrawler {

	private static final SongCrawler songCrawler = SongCrawler.getInstance();
//	private static final String BAIDU_TOP_MUSIC_PAGE = "http://music.baidu.com/top";
	
	private static final DailyCrawler crawler = new DailyCrawler();
	private DailyCrawler() {}
	public static DailyCrawler getInstance() {
		return crawler;
	}
	
	public void crawlTopMusic() {

		// 更新热歌榜
		updateHotSongBoard();
		
		// 更新新歌榜
		updateNewSongBoard();
	}
	
	
	private void updateHotSongBoard() {
		
		// 抓取热歌
		songCrawler.crawlHotSongs();
		
		// 更新热歌数据库
		
	}
	
	
	private void updateNewSongBoard() {
		
		// 抓取热歌
		songCrawler.crawlNewSongs();
		
		// 更新新歌数据库
		
	}
}
