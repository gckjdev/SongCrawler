package com.orange.songcrawler.util;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.io.FileUtils;

import com.orange.songcrawler.util.ArgumentParser.Arguments;

public class FileHierarchyBuilder {
	
	private static final String SONGS_DIR = Arguments.SONGS_DIR.getValue();
	private static final String SONG_CATEGORY_DIR = Arguments.SONG_CATEGORY_DIR.getValue();
	
	private static int singerIndexFileHeaderLines = 5;
	private static int songIndexFileHeaderLines = 5;

	private static final FileHierarchyBuilder  oneInstance = new FileHierarchyBuilder();
	private FileHierarchyBuilder() {}
	public static  FileHierarchyBuilder getInstance() {
		return oneInstance;
	}
	
	
	/*
	 * 定义singer_index文件的行格式
	 * 所有歌曲分Ａ-Ｚ共26个大目录,　每个目录下有所以以这个字母作名字首字母的歌手子目录
	 * 和一个singer_index文件, 其格式为:
	 * 
	 * 　　歌手名 || 歌手链接URL
	 */
	public static class SingerIndexLine {
		
		private final String singerName;
		private final String singerURL;
		
		public SingerIndexLine(String line) {
			String[] splittedLine = line.split("\\|\\|"); // "||"需要转义
			this.singerName = splittedLine[0].trim();
			this.singerURL = splittedLine[1].trim();
		}
		
		public String getSingerName() {
			return singerName;
		}
		
		public String getSingerURL() {
			return singerURL;
		}

		public static  String buildLine(String singerName, String url) {
			return singerName + " || " + url;
		}
	}
	
	
	/*
	 * 定义song_index文件的行格式
	 * 所有歌曲分Ａ-Ｚ共26个大目录,　每个目录下有所以以这个字母作名字首字母的歌手子目录
	 * 和一个singer_index文件, 每个歌手子目录下有该歌手所有歌的歌词文件和song_index
	 * 文件, 其格式为:
	 * 
	 * 　　歌曲名 || 歌曲链接URL　|| 歌曲所属专辑(可选)
	 */
	public static class SongIndexLine {
		
		private final String songName;
		private final String songURL;
		private final String songAlbum;
		
		public SongIndexLine(String line) {
			String[] splittedLine = line.split("\\|\\|"); // "||"需要转义
			this.songName = splittedLine[0].trim();
			this.songURL = splittedLine[1].trim();
			this.songAlbum = splittedLine[2].trim();
		}

		public String getSongName() {
			return songName;
		}

		public String getSongURL() {
			return songURL;
		}

		public String getSongAlbum() {
			return songAlbum;
		}

		public static  String buildLine(String songName, String songUrl, String album) {
			return songName + " || "  + songUrl + " || " + album;
		}
	}
	

	/*
	 * 定义category_index文件的行格式
	 * 
	 * 　　主分类 || 次分类　|| 次分类URL
	 */
	public static class SongCategoryIndexLine {
		
		private final String category;
		private final String subcategory;
		private final String subcategoryURL;
		
		public SongCategoryIndexLine(String line) {
			String[] splittedLine = line.split("\\|\\|"); // "||"需要转义
			this.category       = splittedLine[0].trim();
			this.subcategory    = splittedLine[1].trim();
			this.subcategoryURL = splittedLine[2].trim();
		}

		public String getCategory() {
			return category;
		}

		public String getSubcategory() {
			return subcategory;
		}

		public String getSubcategoryURL() {
			return subcategoryURL;
		}

		public static  String buildLine(String category, String subcategory, String subcategoryURL) {
			return category + " || "  + subcategory + " || " + subcategoryURL;
		}
	}
	
	
	public void writeSingerIndexFile(String nameCapital, List<String> singerUrls) throws IOException {

		int singerNum = singerUrls.size();
		List<String> lines = new ArrayList<String>();
		
		//　头部 
		lines.add("---");
		lines.add("首字母: " + nameCapital);  // 注意! 英文的冒号和空格
		lines.add("歌手总数: " + singerNum); // 注意! 英文的冒号和空格
		lines.add("---");
		lines.add(""); //空行,换行符WriteLines会帮我们添加
		
		//　主体
        lines.addAll(singerUrls);
        
		// 写入
		String pathName = getSingerIndexFileName(nameCapital);
		FileUtils.writeLines(new File(pathName), "UTF-8", lines);
	}

	public List<String> parseSingerIndexFile(String nameCapital) {
		
		List<String> restult = null;
		
		List<String> lines= readFileIntoLines(getSingerIndexFileName(nameCapital));
		try {
			restult = new ArrayList<String>(lines.subList(singerIndexFileHeaderLines, lines.size()));
		} catch (Exception e) {
			restult = null;
		}
		
		return restult;
	}
	
	
	public void writeSingerSongIndexFile(String singerName, String nameCapital,
			                                    int totalSongs, List<String> songData) throws IOException {

		if (singerName == null) 
			singerName = "未知";
		if (songData == null)
			songData = Collections.emptyList();
		
		List<String> lines = new ArrayList<String>();
		
		//　头部 
		lines.add("---");
		lines.add("歌手: " +singerName);  // 注意! 英文的冒号和空格
		lines.add("总数: " + totalSongs); // 注意! 英文的冒号和空格
		lines.add("---");
		lines.add(""); //空行,换行符WriteLines会帮我们添加
		
		//　主体
		lines.addAll(songData);
		
		// 写入
		String pathName = getSingerSongIndexFileName(singerName, nameCapital);
		FileUtils.writeLines(new File(pathName), "UTF-8", lines);
	}

	
    public List<String> parseSingerSongIndexFile(String singerName, String nameCapital) {
		
		List<String> restult = null;
		
		List<String> lines= readFileIntoLines(getSingerSongIndexFileName(singerName, nameCapital));
		try {
			restult = new ArrayList<String>(lines.subList(songIndexFileHeaderLines, lines.size()));
		} catch (Exception e) {
			restult = null;
		}
		
		return restult;
	}
	
	
    public void writeSongCategoryIndexFile(List<String> categories) throws IOException {
	
    	String pathName = getSongCategoryIndexFileName();
    	FileUtils.writeLines(new File(pathName), "UTF-8", categories);
	}
    
	public List<String> parseSongCategoryIndexFile() {

		List<String> lines= readFileIntoLines(getSongCategoryIndexFileName());
		return lines;
	}

    
	public String getSingerIndexFileName(final String nameCapital) {
		
		String singerIndexFileName = SONGS_DIR + nameCapital + "/singer_index";
		return singerIndexFileName;
	}


    public String getSingerSongIndexFileName(final String singerName, final String nameCapital) {
		
    	String songsIndexFileName = SONGS_DIR + nameCapital + "/" + singerName.replaceAll("\\s+", "_")
    			                          + "/song_index";
		return songsIndexFileName;
	}
    
    
    public String getLyricFileName(final String singerName,final String nameCapital, final String songName) {
		
		String songLyricFileName = SONGS_DIR + nameCapital + "/" + singerName.replaceAll("\\s+", "_") 
				                              + "/" + songName.replaceAll("\\s+", "_") + ".lyric";
		return songLyricFileName;
	}
	

    private String getSongCategoryIndexFileName() {
		return SONG_CATEGORY_DIR + "category_index";
	}
    
    
    @SuppressWarnings("unchecked")
	public List<String> readFileIntoLines(String pathName) {

		File file = new File(pathName);
		List<String> lines = null;  
		if (file.canRead()) {
			try {
				lines = FileUtils.readLines(file,"UTF-8");
			} catch (IOException e) {
				lines = null;
			}
		}
		
		return lines;
	}


	
    
    public String getErrorCrawlingSingersURLLog() {
		return SONGS_DIR + "error_crawling_singers_url.log";
	}


	public String getErrorCrawlingSongsURLLog() {
		return SONGS_DIR + "error_crawling_songs_url.log";
	}


	public String getErrorWritingSingersLog() {
		return SONGS_DIR + "error_writing_singers.log";
	}
	

	public String getErrorWritingSongsLog() {
		return SONGS_DIR + "error_writing_songs.log";
	}
	
	public  String getErrorWritingSongIndexLog() {
		return SONGS_DIR + "error_writing_song_index.log";
	}
	
	public  String getErrorCrawlingLyricsLog() {
		return SONGS_DIR + "error_crawling_songs_lyrics.log";
	}


	public  String getTmpFile(String nameCapital, String singerName, int index) {
		return SONGS_DIR + nameCapital + "/" + singerName +  "/" + singerName + "_" + index + ".html";
	}

}