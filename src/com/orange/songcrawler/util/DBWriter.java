package com.orange.songcrawler.util;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.bson.types.ObjectId;

import com.orange.common.log.ServerLog;
import com.orange.game.model.dao.song.Song;
import com.orange.game.model.manager.song.SongManager;
import com.orange.game.model.manager.song.SongManager.SongObjectIdMap;
import com.orange.songcrawler.service.CrawlPolicy.NameCapital;
import com.orange.songcrawler.util.FileHierarchyBuilder.SingerIndexLine;
import com.orange.songcrawler.util.FileHierarchyBuilder.SongIndexLine;

public class DBWriter {

	private static final SongManager songManager = SongManager.getInstance();
	
    // 用于记录写song表时出错的条目,　以便根据这些条目,重新尝试写入:
	// 如果在解析singer_index就出错,　则条目格式为:　name capital :  :
	// 如果在写入某个singer时出错,　则条目格式为:　　 name capital : singer :
	// 如果在写入某首歌时出错,　则条目格式为:　       name capital : singer : song URL
	private static List<String> errorWritingSongCollection = new ArrayList<>();
	private static final String ERROR_WRITING_SONGS_LOG = FileHierarchyBuilder.getErrorWritingSongsLog();
	
	// 用于记录写song_index表时出错的条目,　以便重新写入
	private static List<String> errorWritingSongIndexCollection = new ArrayList<>();
	private static final String ERROR_WRITING_SONG_INDEX_LOG = FileHierarchyBuilder.getErrorWritingSongIndexLog();
	
	// 用于记录写singer表时出错的条目,　以便重新写入
	private static List<String> errorWritingSingerCollection = new ArrayList<>();
	private static final String ERROR_WRITING_SINGERS_LOG = FileHierarchyBuilder.getErrorWritingSingersLog();
		


	public static void writeAllSongsInfoToDB() throws IOException {
		writeAllsongsCollections();
	}

	

	private static void writeAllsongsCollections() throws IOException {

		for (NameCapital nc : NameCapital.values()) {
			writeSongCollectionForNameCapital(nc.getCapital());
		}
		
		FileUtils.writeLines(new File(ERROR_WRITING_SONGS_LOG), "UTF-8", errorWritingSongCollection);
		FileUtils.writeLines(new File(ERROR_WRITING_SONG_INDEX_LOG), "UTF-8", errorWritingSongIndexCollection);
		FileUtils.writeLines(new File(ERROR_WRITING_SINGERS_LOG), "UTF-8", errorWritingSingerCollection);
	}

	
	@SuppressWarnings({ "unchecked" })
	private static void writeSongCollectionForNameCapital(String nameCapital) {
		
		List<String> singerIndexLines = FileHierarchyBuilder.parseSingerIndexFile(nameCapital);
		if (singerIndexLines == null) {
			ServerLog.warn(0,"Failed Writing song collection for name capital: "+ nameCapital);
			errorWritingSongCollection.add(nameCapital + " : : ");
		}
		
		for (String line : singerIndexLines) {
			SingerIndexLine singerIndexLine = new SingerIndexLine(line);
			String singerName = singerIndexLine.getSingerName();
			
			// 写入该歌手的所有歌曲信息到数据库中
			List<SongObjectIdMap> allSongsObjectIds = new ArrayList<>();
			String songIndexFile = FileHierarchyBuilder.getSingerSongIndexFileName(singerName, nameCapital);
			List<String> songIndexLines;
			try {
				songIndexLines = FileUtils.readLines(new File(songIndexFile),"UTF-8");
			} catch (IOException e) {
				e.printStackTrace();
				errorWritingSongCollection.add(nameCapital + " : " + singerName + " : ");
				songIndexLines = Collections.emptyList();
			}
			for (String sil : songIndexLines) {
				SongIndexLine songIndexLine = new SongIndexLine(sil);
				String songName = songIndexLine.getSongName();
				String songURL = songIndexLine.getSongURL();
				String songAlbum = songIndexLine.getSongAlbum();
				String lyricPath = FileHierarchyBuilder.getLyricFileName(singerName, nameCapital, songName);
				Song song = new Song(songName, songURL, songAlbum, singerName, lyricPath);

				ServerLog.info(0, "* 正在写入歌曲到数据库中: " + songName);
				ObjectId songOId = (ObjectId)songManager.writeOneSongIntoDB(song);
				if (songOId == null) {
					ServerLog.info(0, "Failed writing song collection for [" + singerName + "]'s song: " + songURL);
					errorWritingSongCollection.add(nameCapital + " : " + singerName + " : " + songURL);
				}

				allSongsObjectIds.add(new SongObjectIdMap(songName, songOId.toString()));
			}
		    
			//　写完这个歌手的所有歌曲,　再为其生成一个歌曲索引表:singer_song_index
			ServerLog.info(0, "** 正在为歌手[" + singerName + "]写入song_index表...");
			ObjectId singerSongIndexOId = (ObjectId)songManager.writeSingerSongIndexCollection(singerName, allSongsObjectIds);
			if (singerSongIndexOId == null) {
				ServerLog.info(0, "Failed writing signer_song_index for singer " + singerName);
				errorWritingSongIndexCollection.add(singerName + "\n" + allSongsObjectIds.toString());
				continue;
			}
			
			// 再为这个歌手生成一个singer表
			ServerLog.info(0, "*** 正在为歌手[" + singerName + "]写入singer表...");
			ObjectId singerOId = (ObjectId)songManager.writeSingerCollection(nameCapital, singerName, singerSongIndexOId);
			if (singerOId == null) {
				ServerLog.info(0, "Failed writing signer_song_index for singer " + singerName);
				errorWritingSingerCollection.add(nameCapital + ":" + singerName + "\n" + allSongsObjectIds.toString());
			}
		}
	}

}
