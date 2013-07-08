package com.orange.songcrawler.service;

/**
 *  用于爬虫策略,如
 *  　　对于OneShot爬虫,由于百度的IP访问限制,每个实例进程只抓取部分字母范围　
 */
public class CrawlPolicy {

	
	public enum NameCapital {
		A("A"), B("B"), C("C"), D("D"), E("E"), F("F"), G("G"),
		H("H"), I("I"), J("J"), K("K"), L("L"), M("M"), N("N"),
		O("O"), P("P"), Q("Q"), R("R"), S("S"), T("T"),
		U("U"), V("V"), W("W"), X("X"), Y("Y"), Z("Z");
		
		private final String capital;
		NameCapital(String capital) {
			this.capital = capital;
		}
		
		public static NameCapital valueOf(int ord) {
			for (NameCapital nc: NameCapital.values()) {
				if (nc.ordinal() == ord)
					return nc;
			}
			return null;
		}
		
		public String getCapital() {
			return capital;
		}
	}

	public static NameCapital[] dispatchNameCapitalRange(String startCapital, String endCapital) throws Exception {
		
		// 检查有无提供字母范围，无则终止程序！
		if (startCapital == null || endCapital == null) {
			throw new Exception("You must provide start capital and end capital !!!");
		}
		
		// 检查是否合格的字母，否则终止程序！
		if (! Character.isUpperCase(startCapital.charAt(0)) ||
				! Character.isUpperCase(endCapital.charAt(0)) ) {
			throw new Exception("You must provide a valid capital range");
		}
		
		// 检查start是否小于等于end, 否则终止程序！
		if (NameCapital.valueOf(startCapital).ordinal() >
				NameCapital.valueOf(endCapital).ordinal() ) {
			throw new Exception("You must provide a valid capital range");
		}
		
		// 抓singersRange[0]至singersRange[１]的所有歌曲
		NameCapital[] nameCapitalRange = new NameCapital[2];
		nameCapitalRange[0] = NameCapital.valueOf(startCapital);
		nameCapitalRange[1] = NameCapital.valueOf(endCapital);
		
		return nameCapitalRange;
	}
	

	
}
