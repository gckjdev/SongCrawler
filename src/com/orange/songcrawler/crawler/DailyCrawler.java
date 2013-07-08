package com.orange.songcrawler.crawler;

public class DailyCrawler {

	
	private static final String BAIDU_TOP_MUSIC_PAGE = "http://music.baidu.com/top";
	
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
		
	}
	
	
	private void updateNewSongBoard() {
		
	}
}
