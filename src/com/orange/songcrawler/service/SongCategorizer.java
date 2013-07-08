package com.orange.songcrawler.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
import com.orange.game.model.dao.song.Song;
import com.orange.songcrawler.util.DBAccessProxy;
import com.orange.songcrawler.util.FileHierarchyBuilder;
import com.orange.songcrawler.util.FileHierarchyBuilder.SongCategoryIndexLine;

public class SongCategorizer {

	private static final String BAIDU_MUSIC_TAG_PAGE = "http://music.baidu.com/tag";
	private static final int CATEGORY_SONG_PAGE_NUM = 20;
	private static final int CATEGORY_SONG_PER_PAGE = 50;
	
	private static final SongCategorizer songCategorizer = new SongCategorizer();
	private static final FileHierarchyBuilder fileHierarchyBuilder = FileHierarchyBuilder.getInstance();
	private static final DBAccessProxy dbAccessProxy = DBAccessProxy.getInstance(); 

	private SongCategorizer() {}
	public static SongCategorizer getInstance() {
		return songCategorizer;
	}
	
	public void categorizeAllSongs() {
		
		// 第一步: 根据百度音乐分类首页创建所有歌曲的分类(和子分类)索引信息
		createSongCategoryIndex();

		// 第二步:　根据第一步,　依次抓取每个分类之下的子分类的歌曲信息,　并写入数据库
		crawlAllCategories();
	}
	
	private void crawlAllCategories() {

		List<String> lines = fileHierarchyBuilder.parseSongCategoryIndexFile();
		if (lines == null) {
			ServerLog.warn(0, "* 打开category_index文件失败!");
			return;
		}
		
		for (String line: lines) {
			SongCategoryIndexLine categoryIndexLine = new SongCategoryIndexLine(line);
			String category = categoryIndexLine.getCategory();
			String subcategory = categoryIndexLine.getSubcategory();
			String subcategoryURL = categoryIndexLine.getSubcategoryURL();
			
			ServerLog.info(0, "* 正在抓取[" + category + " : " + subcategory +"]下的所有歌曲信息...");
			crawlAllSongsForOneCategory(category, subcategory, subcategoryURL);
		}
	}
	
	
	private void crawlAllSongsForOneCategory(String category, String subcategory, String subcategoryURL) {
		
		Map<String, String> songsInThisCategory = new HashMap<String, String>();
		
		//　每个分类有1000首歌,分为20页
		for (int i = 0; i < CATEGORY_SONG_PAGE_NUM; i++) {
			String url = subcategoryURL+"?start=" + (i*CATEGORY_SONG_PER_PAGE) + "&size=" + CATEGORY_SONG_PER_PAGE; 
			songsInThisCategory.putAll(crawlSongsForOneCategoryPage(category, subcategory, url));
		}
		
		// 抓取该分类完成后，写入song_category表
		dbAccessProxy.writeSongCategoryCollection(category, subcategory, songsInThisCategory);
	}
	
	
	public Map<String, String> crawlSongsForOneCategoryPage(String category, String subcategory, String url) {

		Map<String, String> songsInThisCategory = new HashMap<String, String>();
		
		try {
			Parser parser = new Parser(url);
			parser.setEncoding("UTF-8");
			
			NodeFilter filter = new AndFilter(new TagNameFilter("div"),
					new HasAttributeFilter("class", "song-item clearfix"));
			NodeList nodeList = parser.parse(filter);

			// 抽取出*歌曲信息*节点
			NodeFilter songTitleSpanFilter = new AndFilter(new TagNameFilter("span"),
					new HasAttributeFilter("class", "song-title"));
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

			// 抽取邮*歌手名字*节点
			NodeFilter singerFilter = new AndFilter(new TagNameFilter("span"),
					new HasAttributeFilter("class", "author_list"));
			NodeList singerNodeList = nodeList.extractAllNodesThatMatch(singerFilter, true);
			
			// 遍历每一首作品
			for (int i = 0; i < nodeList.size(); i++) {
				// 获取歌曲名称节点,　节点中包含:
				// 　 <a href=..., title="歌名">text</a>
				Node songTitleNode = songTitleNodeList.elementAt(i);
				String songName = ((LinkTag)songTitleNode).toPlainTextString().trim();
				
				// 获取歌手名节点
				Node singerNode = singerNodeList.elementAt(i);
				NodeList aNodeList = new NodeList();
				NodeFilter aNodeFilter = new TagNameFilter("a");
				singerNode.collectInto(aNodeList, aNodeFilter);

				LinkTag aNode = (LinkTag)aNodeList.elementAt(0);
				String singerName = aNode.toPlainTextString().trim();

				// 所有信息齐备后,　写入数据库,　更新song表的category信息
				List<Song> songs = dbAccessProxy.findSongBySongNameAndSinger(songName, singerName);
				ServerLog.info(0, "* 正在写入分类信息到song表中:　" + songName + " : " + "[" + category + " , " + subcategory + "]");
				for (Song song : songs) 
				    dbAccessProxy.updateCategoryInfoForSong(song, category, subcategory);
				
				//　记下分类信息,　以便最后一并更新song_category表
				for (Song song : songs) 
					songsInThisCategory.put(songName, song.getObjectId().toString());
			}
		} catch (ParserException e) {
			e.printStackTrace();
		}
		
		return songsInThisCategory;
	}

	
	private void createSongCategoryIndex() {
		
		try {
			Parser categoryParser = new Parser(BAIDU_MUSIC_TAG_PAGE);
			categoryParser.setEncoding("UTF-8");
		
			NodeFilter categoryFilter = new AndFilter(new TagNameFilter("dl"), new HasAttributeFilter("class", "tag-mod"));
			NodeList categoryNodeList = categoryParser.parse(categoryFilter);
			
			NodeFilter categoryNameFilter = new TagNameFilter("dt");
			NodeList  categoryNameList = categoryNodeList.extractAllNodesThatMatch(categoryNameFilter, true);
			
			List<String> categories = new ArrayList<String>();
			for (int i = 0; i < categoryNodeList.size(); i++) {
				Node node = categoryNodeList.elementAt(i);

				// 获得主分类名
				String categoryName = categoryNameList.elementAt(i).toPlainTextString().replace("&amp;", "&");
				
				//　获得次分类列表
				NodeList subcategoryList = new NodeList();
				NodeFilter subcategoryFilter = new AndFilter(new TagNameFilter("span"), 
						                                     new HasAttributeFilter("class", "tag-list clearfix"));
				node.collectInto(subcategoryList,subcategoryFilter);
				
				//　解析次分类列表
				for (int k = 0; k < subcategoryList.size(); k++) {
					NodeList aNodeList = new NodeList();
					NodeFilter aNodeFilter = new TagNameFilter("a"); 
					subcategoryList.elementAt(k).collectInto(aNodeList, aNodeFilter);
					
					LinkTag aNode = (LinkTag)aNodeList.elementAt(0);
					String subcategoryName = aNode.toPlainTextString();
					String subcategoryURL = aNode.getLink();
					
					categories.add(SongCategoryIndexLine.buildLine(categoryName, subcategoryName, subcategoryURL));
			    }
				categories.add("");
			}
			
			//　写入到文件中
			ServerLog.info(0, "* 正在写入歌曲分类索引文件...");
			fileHierarchyBuilder.writeSongCategoryIndexFile(categories);
			
		} catch (ParserException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}